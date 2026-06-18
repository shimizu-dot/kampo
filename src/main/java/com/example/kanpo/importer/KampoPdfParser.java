package com.example.kanpo.importer;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

@Component
public class KampoPdfParser {

	private static final Pattern SALES_NAME_PATTERN = Pattern.compile("^販売名\\s+(.+)$");
	private static final Pattern DOCUMENT_NO_PATTERN = Pattern.compile("No\\.?(\\d+)");
	private static final Pattern IDENTIFICATION_CODE_PATTERN = Pattern.compile("識別コード\\s*(?:.+?／\\s*)?(\\d+)");
	private static final Pattern INGREDIENT_PATTERN = Pattern.compile("(?:日局)?([^\\s0-9]+)\\s+([0-9]+(?:\\.[0-9]+)?)\\s*[gｇ]");
	private static final Pattern DOSAGE_PATTERN = Pattern.compile("1日([0-9]+(?:\\.[0-9]+)?)gを(.+?)する(?:。|$)");
	private static final Pattern EFFICACY_PATTERN = Pattern.compile("^(.*?)(?:を伴う)?次の諸症[:：](.+)$");

	public KampoImportDraft parse(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new KampoPdfImportException("PDFファイルが選択されていません。");
		}

		String text = extractText(file);
		List<String> lines = splitLines(text);
		KampoImportDraft draft = new KampoImportDraft();
		draft.setSourceFileName(Objects.requireNonNullElse(file.getOriginalFilename(), ""));
		draft.setSourceDocumentNo(findDocumentNo(text));
		draft.setIdentificationCode(findIdentificationCode(text));
		draft.setSalesName(findSalesName(lines));
		fillComposition(lines, draft);
		fillEfficacy(text, draft);
		fillDosage(text, draft);
		validate(draft);
		return draft;
	}

	private String extractText(MultipartFile file) {
		try (InputStream inputStream = file.getInputStream(); PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
			PDFTextStripper stripper = new PDFTextStripper();
			stripper.setSortByPosition(false);
			return stripper.getText(document);
		} catch (IOException e) {
			throw new KampoPdfImportException("PDFの読み取りに失敗しました。", e);
		}
	}

	private List<String> splitLines(String text) {
		String normalized = normalizeWhitespace(text.replace('\r', '\n'));
		String[] rawLines = normalized.split("\n");
		List<String> lines = new ArrayList<>();
		for (String rawLine : rawLines) {
			String line = rawLine.trim();
			if (!line.isEmpty()) {
				lines.add(line);
			}
		}
		return lines;
	}

	private String findDocumentNo(String text) {
		Matcher matcher = DOCUMENT_NO_PATTERN.matcher(text);
		if (matcher.find()) {
			return matcher.group(1);
		}
		return "";
	}

	private String findIdentificationCode(String text) {
		Matcher matcher = IDENTIFICATION_CODE_PATTERN.matcher(text);
		if (matcher.find()) {
			return matcher.group(1);
		}
		Matcher fallback = Pattern.compile("／\\s*(\\d+)").matcher(text);
		if (fallback.find()) {
			return fallback.group(1);
		}
		throw new KampoPdfImportException("識別コードを抽出できませんでした。");
	}

	private String findSalesName(List<String> lines) {
		for (String line : lines) {
			Matcher matcher = SALES_NAME_PATTERN.matcher(line);
			if (matcher.find()) {
				return normalizeSalesName(matcher.group(1));
			}
		}
		throw new KampoPdfImportException("販売名を抽出できませんでした。");
	}

	private String normalizeSalesName(String salesName) {
		if (salesName == null) {
			return "";
		}
		return salesName
				.replace("（医療用）", "")
				.replace("(医療用)", "")
				.trim();
	}

	private void fillComposition(List<String> lines, KampoImportDraft draft) {
		int ingredientStart = -1;
		int ingredientEnd = -1;
		for (int i = 0; i < lines.size(); i++) {
			if (ingredientStart < 0 && lines.get(i).startsWith("有効成分")) {
				ingredientStart = i;
			}
			if (ingredientStart >= 0 && lines.get(i).startsWith("添加剤")) {
				ingredientEnd = i;
				break;
			}
		}
		if (ingredientStart < 0 || ingredientEnd < 0 || ingredientEnd <= ingredientStart) {
			throw new KampoPdfImportException("有効成分の構成を抽出できませんでした。");
		}

		String section = joinWithNewlines(lines, ingredientStart + 1, ingredientEnd);
		int includeIndex = section.indexOf("含有する。");
		if (includeIndex >= 0) {
			section = section.substring(includeIndex + "含有する。".length());
		}

		List<KampoIngredientDraft> ingredients = new ArrayList<>();
		Matcher matcher = INGREDIENT_PATTERN.matcher(section);
		while (matcher.find()) {
			KampoIngredientDraft ingredient = new KampoIngredientDraft();
			ingredient.setIngredientName(matcher.group(1).trim());
			ingredient.setAmountValue(new BigDecimal(matcher.group(2)));
			ingredient.setAmountUnit("g");
			ingredient.setSortOrder(ingredients.size() + 1);
			ingredient.setRawAmountText(matcher.group(0).trim());
			ingredients.add(ingredient);
		}
		if (ingredients.isEmpty()) {
			throw new KampoPdfImportException("有効成分の配合量を抽出できませんでした。");
		}
		draft.setIngredients(ingredients);
	}

	private void fillEfficacy(String text, KampoImportDraft draft) {
		Pattern sectionPattern = Pattern.compile("4\\.\\s*効能又は効果([\\s\\S]*?)6\\.\\s*用法及び用量");
		Matcher sectionMatcher = sectionPattern.matcher(text);
		if (!sectionMatcher.find()) {
			throw new KampoPdfImportException("効能又は効果の範囲を抽出できませんでした。");
		}
		String section = cleanJapaneseText(sectionMatcher.group(1));
		Matcher matcher = EFFICACY_PATTERN.matcher(section);
		if (matcher.find()) {
			draft.setEfficacyConditionText(cleanJapaneseText(matcher.group(1)));
			draft.setEfficacyIndicationText(cleanJapaneseText(matcher.group(2)));
			draft.setEfficacySplitFallback(false);
			return;
		}
		int colonIndex = section.indexOf('：');
		if (colonIndex < 0) {
			colonIndex = section.indexOf(':');
		}
		if (colonIndex < 0) {
			draft.setEfficacyConditionText(section);
			draft.setEfficacyIndicationText("");
			draft.setEfficacySplitFallback(true);
			return;
		}
		String condition = cleanJapaneseText(section.substring(0, colonIndex));
		String indication = cleanJapaneseText(section.substring(colonIndex + 1));
		draft.setEfficacyConditionText(condition);
		draft.setEfficacyIndicationText(indication);
		draft.setEfficacySplitFallback(false);
	}

	private void fillDosage(String text, KampoImportDraft draft) {
		Pattern sectionPattern = Pattern.compile("6\\.\\s*用法及び用量([\\s\\S]*?)8\\.\\s*重要な基本的注意");
		Matcher sectionMatcher = sectionPattern.matcher(text);
		if (!sectionMatcher.find()) {
			throw new KampoPdfImportException("用法及び用量の範囲を抽出できませんでした。");
		}
		String section = cleanJapaneseText(sectionMatcher.group(1));
		Matcher matcher = DOSAGE_PATTERN.matcher(section);
		if (!matcher.find()) {
			throw new KampoPdfImportException("用法及び用量の分割に失敗しました。");
		}
		draft.setDosageDailyAmount(new BigDecimal(matcher.group(1)));
		draft.setDosageInstructionsText(cleanJapaneseText(matcher.group(2)));
	}

	private String joinWithNewlines(List<String> lines, int startInclusive, int endExclusive) {
		StringBuilder builder = new StringBuilder();
		for (int i = startInclusive; i < endExclusive; i++) {
			if (builder.length() > 0) {
				builder.append('\n');
			}
			builder.append(normalizeWhitespace(lines.get(i)));
		}
		return builder.toString();
	}

	private String cleanJapaneseText(String text) {
		return text.replaceAll("[\\s\\u00A0\\u2007\\u202F\\u3000]+", "");
	}

	private String normalizeWhitespace(String text) {
		return text
				.replace('\u00A0', ' ')
				.replace('\u2007', ' ')
				.replace('\u202F', ' ')
				.replace('\u3000', ' ');
	}

	private void validate(KampoImportDraft draft) {
		List<String> missing = new ArrayList<>();
		if (draft.getIdentificationCode() == null || draft.getIdentificationCode().isBlank()) {
			missing.add("識別コード");
		}
		if (draft.getSalesName() == null || draft.getSalesName().isBlank()) {
			missing.add("販売名");
		}
		if (draft.getEfficacyConditionText() == null || draft.getEfficacyConditionText().isBlank()) {
			missing.add("効能又は効果（前段）");
		}
		if (!draft.isEfficacySplitFallback() && (draft.getEfficacyIndicationText() == null || draft.getEfficacyIndicationText().isBlank())) {
			missing.add("摘要");
		}
		if (draft.getDosageDailyAmount() == null) {
			missing.add("1日量");
		}
		if (draft.getDosageInstructionsText() == null || draft.getDosageInstructionsText().isBlank()) {
			missing.add("用法");
		}
		if (draft.getIngredients() == null || draft.getIngredients().isEmpty()) {
			missing.add("有効成分");
		}
		if (!missing.isEmpty()) {
			throw new KampoPdfImportException("必要項目の抽出に失敗しました: " + String.join(", ", missing));
		}
	}
}
