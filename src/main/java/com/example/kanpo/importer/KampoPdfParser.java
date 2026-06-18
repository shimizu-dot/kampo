package com.example.kanpo.importer;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

@Component
public class KampoPdfParser {

	private static final Logger log = LoggerFactory.getLogger(KampoPdfParser.class);
	private static final Pattern SALES_NAME_PATTERN = Pattern.compile("^販売名\\s+(.+)$");
	private static final Pattern READING_PATTERN = Pattern.compile("^[ァ-ヶー\\s\\u3000]+$");
	private static final Pattern DOCUMENT_NO_PATTERN = Pattern.compile("No\\.?(\\d+)");
	private static final Pattern IDENTIFICATION_CODE_PATTERN = Pattern.compile("識別コード\\s*(?:.+?／\\s*)?(\\d+)");
	private static final Pattern INGREDIENT_PATTERN = Pattern.compile("(?:日局)?([^\\s0-9]+)\\s+([0-9]+(?:\\.[0-9]+)?)\\s*[gｇ]");
	private static final Pattern DOSAGE_PATTERN = Pattern.compile("1日([0-9]+(?:\\.[0-9]+)?)gを(.+?)する(?:。|$)");
	private static final Pattern EFFICACY_PATTERN = Pattern.compile("^(.*?)(?:を伴う)?次の諸症[:：](.+)$");
	private static final Pattern EFFICACY_START_PATTERN = Pattern.compile("(?m)^\\s*4\\s*[\\.．]?\\s*効能又は効果");
	private static final Pattern EFFICACY_END_PATTERN = Pattern.compile("(?m)^\\s*6\\s*[\\.．]?\\s*用法及び用量");
	private static final Pattern DOSAGE_START_PATTERN = Pattern.compile("(?m)^\\s*6\\s*[\\.．]?\\s*用法及び用量");
	private static final Pattern DOSAGE_END_PATTERN = Pattern.compile("(?m)^\\s*8\\s*[\\.．]?\\s*重要な基本的注意");

	public KampoImportDraft parse(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new KampoPdfImportException("PDFファイルが選択されていません。");
		}

		String fileName = Objects.requireNonNullElse(file.getOriginalFilename(), "");
		log.info("PDF parse started: fileName={}, size={}", fileName, file.getSize());
		try {
			log.debug("step=extractText(raw)");
			String text = extractText(file, false);
			log.debug("step=extractText(raw), length={}", text.length());

			log.debug("step=extractText(sorted)");
			String sortedText = extractText(file, true);
			log.debug("step=extractText(sorted), length={}", sortedText.length());

			List<String> lines = splitLines(text);
			List<String> sortedLines = splitLines(sortedText);
			log.debug("step=splitLines, rawLines={}, sortedLines={}", lines.size(), sortedLines.size());

			KampoImportDraft draft = new KampoImportDraft();
			draft.setSourceFileName(fileName);

			log.debug("step=findDocumentNo");
			draft.setSourceDocumentNo(findDocumentNo(text));

			log.debug("step=findIdentificationCode");
			draft.setIdentificationCode(findIdentificationCode(text));

			log.debug("step=findSalesName");
			int salesNameLineIndex = findSalesNameLineIndex(lines);
			draft.setSalesName(salesNameLineIndex >= 0 ? normalizeSalesName(extractSalesName(lines.get(salesNameLineIndex))) : findSalesName(lines));

			log.debug("step=findReading");
			draft.setReading(findReading(lines, sortedLines, salesNameLineIndex));

			log.debug("step=fillComposition");
			fillComposition(lines, draft);

			log.debug("step=fillEfficacy");
			fillEfficacy(text, sortedText, lines, sortedLines, draft);

			log.debug("step=fillDosage");
			fillDosage(text, sortedText, draft);

			log.debug("step=validate");
			validate(draft);

			log.info("PDF parse completed: fileName={}, identificationCode={}, salesName={}", fileName, draft.getIdentificationCode(), draft.getSalesName());
			return draft;
		} catch (RuntimeException exception) {
			log.error("PDF parse failed: fileName={}, message={}", fileName, exception.getMessage(), exception);
			throw exception;
		}
	}

	private String extractText(MultipartFile file, boolean sortByPosition) {
		try (InputStream inputStream = file.getInputStream(); PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
			PDFTextStripper stripper = new PDFTextStripper();
			stripper.setSortByPosition(sortByPosition);
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
		int salesNameLineIndex = findSalesNameLineIndex(lines);
		if (salesNameLineIndex >= 0) {
			return normalizeSalesName(extractSalesName(lines.get(salesNameLineIndex)));
		}
		throw new KampoPdfImportException("販売名を抽出できませんでした。");
	}

	private int findSalesNameLineIndex(List<String> lines) {
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			Matcher matcher = SALES_NAME_PATTERN.matcher(line);
			if (matcher.find()) {
				return i;
			}
		}
		return -1;
	}

	private String extractSalesName(String line) {
		Matcher matcher = SALES_NAME_PATTERN.matcher(line);
		if (matcher.find()) {
			return matcher.group(1);
		}
		return "";
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

	private String findReading(List<String> lines, List<String> sortedLines, int salesNameLineIndex) {
		String reading = findReadingNearSalesName(lines, salesNameLineIndex);
		if (!reading.isEmpty()) {
			return reading;
		}
		reading = findReadingNearKanboSeizai(sortedLines);
		if (!reading.isEmpty()) {
			return reading;
		}
		return findReadingNearKanboSeizai(lines);
	}

	private String findReadingNearSalesName(List<String> lines, int salesNameLineIndex) {
		if (salesNameLineIndex < 0 || salesNameLineIndex >= lines.size()) {
			return "";
		}
		for (int i = salesNameLineIndex - 1; i >= Math.max(0, salesNameLineIndex - 3); i--) {
			String candidate = lines.get(i);
			if (candidate == null || candidate.isBlank()) {
				continue;
			}
			String normalized = normalizeReading(candidate);
			if (!normalized.isEmpty() && isReadingLine(candidate)) {
				log.debug("reading: picked from line above sales name, index={}, value={}", i, normalized);
				return normalized;
			}
		}
		return "";
	}

	private String findReadingNearKanboSeizai(List<String> lines) {
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			if (line == null || !line.equals("漢方製剤")) {
				continue;
			}
			for (int j = i + 1; j < Math.min(lines.size(), i + 8); j++) {
				String candidate = lines.get(j);
				if (candidate == null || candidate.isBlank()) {
					continue;
				}
				if (candidate.contains("販売名") || candidate.contains("有効成分")) {
					break;
				}
				String normalized = normalizeReading(candidate);
				if (!normalized.isEmpty() && isReadingLine(candidate)) {
					log.debug("reading: picked from line after 漢方製剤, index={}, value={}", j, normalized);
					return normalized;
				}
			}
		}
		return "";
	}

	private boolean isReadingLine(String candidate) {
		return READING_PATTERN.matcher(candidate).matches();
	}

	private String normalizeReading(String reading) {
		if (reading == null) {
			return "";
		}
		return reading
				.replace(" ", "")
				.replace("　", "")
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

	private void fillEfficacy(String text, String sortedText, List<String> lines, List<String> sortedLines, KampoImportDraft draft) {
		String section = extractSection(text, EFFICACY_START_PATTERN, EFFICACY_END_PATTERN);
		if (section == null) {
			log.debug("efficacy: raw text section not found, retrying sorted text");
			section = extractSection(sortedText, EFFICACY_START_PATTERN, EFFICACY_END_PATTERN);
		}
		List<String> sectionLines = null;
		if (section == null) {
			log.debug("efficacy: text section still missing, retrying line-based extraction");
			sectionLines = extractSectionLines(lines, EFFICACY_START_PATTERN, EFFICACY_END_PATTERN);
			if (sectionLines == null) {
				log.debug("efficacy: raw lines failed, retrying sorted lines");
				sectionLines = extractSectionLines(sortedLines, EFFICACY_START_PATTERN, EFFICACY_END_PATTERN);
			}
			if (sectionLines != null) {
				section = String.join("\n", sectionLines);
			}
		}
		if (sectionLines == null) {
			sectionLines = extractSectionLines(lines, EFFICACY_START_PATTERN, EFFICACY_END_PATTERN);
			if (sectionLines == null) {
				sectionLines = extractSectionLines(sortedLines, EFFICACY_START_PATTERN, EFFICACY_END_PATTERN);
			}
		}
		if (section == null) {
			log.debug("efficacy: extraction failed after all fallbacks");
			throw new KampoPdfImportException("効能又は効果の範囲を抽出できませんでした。");
		}
		log.debug("efficacy: section length={}, lineCount={}", section.length(), sectionLines == null ? 0 : sectionLines.size());
		section = cleanJapaneseText(section);
		Matcher matcher = EFFICACY_PATTERN.matcher(section);
		if (matcher.find()) {
			log.debug("efficacy: split by EFFICACY_PATTERN");
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
			log.debug("efficacy: no colon delimiter, using first body line fallback");
			String condition = sectionLines == null ? section : findFirstBodyLine(sectionLines);
			draft.setEfficacyConditionText(cleanJapaneseText(condition));
			draft.setEfficacyIndicationText("");
			draft.setEfficacySplitFallback(true);
			return;
		}
		log.debug("efficacy: split by colon");
		String condition = cleanJapaneseText(section.substring(0, colonIndex));
		String indication = cleanJapaneseText(section.substring(colonIndex + 1));
		draft.setEfficacyConditionText(condition);
		draft.setEfficacyIndicationText(indication);
		draft.setEfficacySplitFallback(false);
	}

	private void fillDosage(String text, String sortedText, KampoImportDraft draft) {
		String section = extractSection(text, DOSAGE_START_PATTERN, DOSAGE_END_PATTERN);
		if (section == null) {
			section = extractSection(sortedText, DOSAGE_START_PATTERN, DOSAGE_END_PATTERN);
		}
		if (section == null) {
			throw new KampoPdfImportException("用法及び用量の範囲を抽出できませんでした。");
		}
		section = cleanJapaneseText(section);
		Matcher matcher = DOSAGE_PATTERN.matcher(section);
		if (!matcher.find()) {
			throw new KampoPdfImportException("用法及び用量の分割に失敗しました。");
		}
		draft.setDosageDailyAmount(new BigDecimal(matcher.group(1)));
		draft.setDosageInstructionsText(cleanJapaneseText(matcher.group(2)));
	}

	private String extractSection(String text, Pattern startPattern, Pattern endPattern) {
		if (text == null || text.isBlank()) {
			return null;
		}
		Matcher startMatcher = startPattern.matcher(text);
		if (!startMatcher.find()) {
			return null;
		}
		int startIndex = startMatcher.end();
		Matcher endMatcher = endPattern.matcher(text);
		if (!endMatcher.find(startIndex)) {
			return null;
		}
		if (endMatcher.start() <= startIndex) {
			return null;
		}
		return text.substring(startIndex, endMatcher.start());
	}

	private List<String> extractSectionLines(List<String> lines, Pattern startPattern, Pattern endPattern) {
		if (lines == null || lines.isEmpty()) {
			return null;
		}
		int startIndex = findLineIndex(lines, startPattern, 0);
		if (startIndex < 0) {
			return null;
		}
		int endIndex = findLineIndex(lines, endPattern, startIndex + 1);
		if (endIndex < 0 || endIndex <= startIndex) {
			return null;
		}
		return new ArrayList<>(lines.subList(startIndex + 1, endIndex));
	}

	private int findLineIndex(List<String> lines, Pattern pattern, int fromIndex) {
		for (int i = Math.max(fromIndex, 0); i < lines.size(); i++) {
			if (pattern.matcher(lines.get(i)).find()) {
				return i;
			}
		}
		return -1;
	}

	private String findFirstBodyLine(List<String> sectionLines) {
		for (String line : sectionLines) {
			String trimmed = line == null ? "" : line.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			if (trimmed.matches("^\\d+(?:\\.\\d+)*\\s*.*$")) {
				continue;
			}
			if (trimmed.equals("効能又は効果") || trimmed.equals("用法及び用量") || trimmed.equals("重要な基本的注意")) {
				continue;
			}
			return trimmed;
		}
		return sectionLines.isEmpty() ? "" : sectionLines.get(0);
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
