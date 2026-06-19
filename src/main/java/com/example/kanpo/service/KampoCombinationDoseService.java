package com.example.kanpo.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.example.kanpo.repository.KampoImportRepository;
import com.example.kanpo.view.KampoCombinationDoseResultView;
import com.example.kanpo.view.KampoIngredientContributionView;
import com.example.kanpo.view.KampoIngredientTotalView;
import com.example.kanpo.view.KampoProductView;
import org.apache.fontbox.ttf.TrueTypeCollection;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class KampoCombinationDoseService {

	private static final List<String> TARGET_INGREDIENTS = List.of("カンゾウ", "マオウ", "サンシシ", "ダイオウ", "オウゴン");
	private static final Set<String> TARGET_INGREDIENT_SET = TARGET_INGREDIENTS.stream()
			.map(KampoCombinationDoseService::normalizeName)
			.collect(Collectors.toUnmodifiableSet());
	private static final Map<String, ThresholdRule> THRESHOLDS = Map.of(
			"カンゾウ", new ThresholdRule(new BigDecimal("2.5"), new BigDecimal("5.0")),
			"マオウ", new ThresholdRule(new BigDecimal("4.0"), new BigDecimal("8.0")),
			"サンシシ", new ThresholdRule(new BigDecimal("3.0"), new BigDecimal("6.0")),
			"ダイオウ", new ThresholdRule(new BigDecimal("2.0"), new BigDecimal("4.0")),
			"オウゴン", new ThresholdRule(new BigDecimal("2.5"), new BigDecimal("5.0")));
	private static final String[] JAPANESE_FONT_CANDIDATES = {
			"/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
			"/System/Library/Fonts/Supplemental/AppleGothic.ttf",
			"/System/Library/Fonts/Supplemental/NotoSansGothic-Regular.ttf",
			"/System/Library/Fonts/ヒラギノ角ゴシック W4.ttc",
			"/System/Library/Fonts/ヒラギノ角ゴシック W6.ttc",
			"/System/Library/Fonts/ヒラギノ角ゴシック W3.ttc",
			"/System/Library/Fonts/ヒラギノ明朝 ProN.ttc"
	};
	private static final List<String> JAPANESE_FONT_NAMES = List.of(
			"Hiragino Sans",
			"Hiragino Kaku Gothic ProN",
			"Hiragino Kaku Gothic Pro",
			"Hiragino Mincho ProN",
			"Hiragino Mincho Pro",
			"Hiragino Maru Gothic ProN");

	private final KampoImportRepository repository;

	public KampoCombinationDoseService(KampoImportRepository repository) {
		this.repository = repository;
	}

	public KampoCombinationDoseResultView calculate(List<Long> selectedProductIds) {
		List<Long> ids = selectedProductIds == null ? List.of() : selectedProductIds.stream()
				.filter(Objects::nonNull)
				.distinct()
				.toList();
		List<KampoProductView> selectedProducts = ids.isEmpty() ? List.of() : repository.findProductsByIds(ids);

		Map<IngredientKey, KampoIngredientTotalView> totals = new LinkedHashMap<>();
		List<String> warnings = new ArrayList<>();

		for (KampoProductView product : selectedProducts) {
			if (product.getIngredients() == null) {
				continue;
			}
			for (var ingredient : product.getIngredients()) {
				String ingredientName = normalizeName(ingredient.getIngredientName());
				if (!TARGET_INGREDIENT_SET.contains(ingredientName)) {
					continue;
				}
				String unit = normalizeUnit(ingredient.getAmountUnit());
				IngredientKey key = new IngredientKey(ingredientName, unit);
				KampoIngredientTotalView totalView = totals.computeIfAbsent(key, ignored -> {
					KampoIngredientTotalView view = new KampoIngredientTotalView();
					view.setIngredientName(ingredientName);
					view.setAmountUnit(unit);
					ThresholdRule thresholdRule = thresholdFor(ingredientName);
					view.setCautionThreshold(thresholdRule.cautionThreshold());
					view.setReviewThreshold(thresholdRule.reviewThreshold());
					return view;
				});
				BigDecimal amountValue = ingredient.getAmountValue() == null ? BigDecimal.ZERO : ingredient.getAmountValue();
				totalView.setTotalAmountValue(totalView.getTotalAmountValue().add(amountValue));
				totalView.setContributionCount(totalView.getContributionCount() + 1);

				KampoIngredientContributionView contribution = new KampoIngredientContributionView();
				contribution.setProductId(product.getId());
				contribution.setIdentificationCode(product.getIdentificationCode());
				contribution.setSalesName(product.getSalesName());
				contribution.setAmountValue(amountValue);
				contribution.setAmountUnit(unit);
				totalView.getContributions().add(contribution);
			}
		}

		List<KampoIngredientTotalView> ingredientTotals = totals.values().stream()
				.sorted(Comparator
						.comparing((KampoIngredientTotalView view) -> TARGET_INGREDIENTS.indexOf(view.getIngredientName()))
						.thenComparing(KampoIngredientTotalView::getAmountUnit, Comparator.nullsFirst(String::compareTo)))
				.collect(Collectors.toCollection(ArrayList::new));

		for (String targetIngredient : TARGET_INGREDIENTS) {
			List<KampoIngredientTotalView> targetTotals = ingredientTotals.stream()
					.filter(view -> targetIngredient.equals(view.getIngredientName()))
					.toList();
			if (targetTotals.isEmpty()) {
				continue;
			}
			long distinctUnits = targetTotals.stream()
					.map(KampoIngredientTotalView::getAmountUnit)
					.distinct()
					.count();
			if (distinctUnits > 1) {
				warnings.add(targetIngredient + " は単位が混在しています。単位ごとに分けて表示しています。");
			}
		}

		applyJudgement(ingredientTotals, warnings);

		KampoCombinationDoseResultView result = new KampoCombinationDoseResultView();
		result.setSelectedProducts(selectedProducts);
		result.setIngredientTotals(ingredientTotals);
		result.setWarnings(warnings);
		setOverallJudgement(result, ingredientTotals);
		return result;
	}

	public byte[] buildCsv(KampoCombinationDoseResultView result) {
		StringBuilder builder = new StringBuilder();
		builder.append('\uFEFF');
		appendCsvRow(builder, "漢方薬 併用成分集計結果");
		appendCsvRow(builder, "総合判定", valueOrEmpty(result.getOverallJudgementLabel()));
		appendCsvRow(builder, "判定説明", valueOrEmpty(result.getOverallJudgementNote()));
		appendCsvRow(builder, "選択薬剤数", Integer.toString(result.getSelectedProducts().size()));
		appendCsvRow(builder, "対象成分", String.join(" / ", TARGET_INGREDIENTS));
		appendCsvRow(builder);
		appendCsvRow(builder, "選択薬剤");
		appendCsvRow(builder, "識別コード", "販売名", "読み方", "1日量");
		for (KampoProductView product : result.getSelectedProducts()) {
			appendCsvRow(builder,
					valueOrEmpty(product.getIdentificationCode()),
					valueOrEmpty(product.getSalesName()),
					valueOrEmpty(product.getReading()),
					product.getDosageDailyAmount() == null ? "" : product.getDosageDailyAmount().toPlainString());
		}
		appendCsvRow(builder);
		appendCsvRow(builder, "成分別合計");
		appendCsvRow(builder, "成分", "合計1日量", "単位", "注意閾値", "要確認閾値", "判定", "判定説明");
		for (KampoIngredientTotalView total : result.getIngredientTotals()) {
			appendCsvRow(builder,
					valueOrEmpty(total.getIngredientName()),
					total.getTotalAmountValue() == null ? "" : total.getTotalAmountValue().toPlainString(),
					valueOrEmpty(total.getAmountUnit()),
					total.getCautionThreshold() == null ? "" : total.getCautionThreshold().toPlainString(),
					total.getReviewThreshold() == null ? "" : total.getReviewThreshold().toPlainString(),
					valueOrEmpty(total.getJudgementLabel()),
					valueOrEmpty(total.getJudgementNote()));
		}
		if (!result.getWarnings().isEmpty()) {
			appendCsvRow(builder);
			appendCsvRow(builder, "注意事項");
			for (String warning : result.getWarnings()) {
				appendCsvRow(builder, warning);
			}
		}
		return builder.toString().getBytes(StandardCharsets.UTF_8);
	}

	public byte[] buildPdf(KampoCombinationDoseResultView result) throws IOException {
		try {
			return buildTextPdf(result);
		} catch (Throwable textPdfException) {
			return buildRasterPdf(result, textPdfException);
		}
	}

	private byte[] buildTextPdf(KampoCombinationDoseResultView result) throws IOException {
		try (PDDocument document = new PDDocument()) {
			PDType0Font font = loadJapaneseFont(document);
			PdfWriter writer = new PdfWriter(document, font);
			writer.writeTitle("漢方薬 併用成分集計結果");
			writer.writeKv("総合判定", valueOrEmpty(result.getOverallJudgementLabel()));
			writer.writeKv("判定説明", valueOrEmpty(result.getOverallJudgementNote()));
			writer.writeKv("選択薬剤数", Integer.toString(result.getSelectedProducts().size()));
			writer.writeKv("対象成分", String.join(" / ", TARGET_INGREDIENTS));
			writer.writeSection("選択薬剤");
			for (KampoProductView product : result.getSelectedProducts()) {
				writer.writeBullet(valueOrEmpty(product.getIdentificationCode()) + " " + valueOrEmpty(product.getSalesName())
						+ " " + valueOrEmpty(product.getReading())
						+ " 1日量=" + (product.getDosageDailyAmount() == null ? "-" : product.getDosageDailyAmount().toPlainString()));
			}
			writer.writeSection("成分別合計");
			for (KampoIngredientTotalView total : result.getIngredientTotals()) {
				writer.writeBullet(
						valueOrEmpty(total.getIngredientName())
								+ " 合計=" + (total.getTotalAmountValue() == null ? "-" : total.getTotalAmountValue().toPlainString())
								+ " " + valueOrEmpty(total.getAmountUnit())
								+ " / 注意=" + valueOrEmpty(total.getCautionThreshold() == null ? null : total.getCautionThreshold().toPlainString())
								+ " / 要確認=" + valueOrEmpty(total.getReviewThreshold() == null ? null : total.getReviewThreshold().toPlainString())
								+ " / 判定=" + valueOrEmpty(total.getJudgementLabel()));
				writer.writeWrapped("  " + valueOrEmpty(total.getJudgementNote()));
			}
			if (!result.getWarnings().isEmpty()) {
				writer.writeSection("注意事項");
				for (String warning : result.getWarnings()) {
					writer.writeBullet(warning);
				}
			}

			writer.close();
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			document.save(outputStream);
			return outputStream.toByteArray();
		}
	}

	private byte[] buildRasterPdf(KampoCombinationDoseResultView result, Throwable cause) throws IOException {
		log.warn("Falling back to raster PDF rendering", cause);
		List<BufferedImage> pages = new ArrayList<>();
		RasterPdfWriter writer = new RasterPdfWriter(pages);
		writer.writeTitle("漢方薬 併用成分集計結果");
		writer.writeKv("総合判定", valueOrEmpty(result.getOverallJudgementLabel()));
		writer.writeKv("判定説明", valueOrEmpty(result.getOverallJudgementNote()));
		writer.writeKv("選択薬剤数", Integer.toString(result.getSelectedProducts().size()));
		writer.writeKv("対象成分", String.join(" / ", TARGET_INGREDIENTS));
		writer.writeSection("選択薬剤");
		for (KampoProductView product : result.getSelectedProducts()) {
			writer.writeBullet(valueOrEmpty(product.getIdentificationCode()) + " " + valueOrEmpty(product.getSalesName())
					+ " " + valueOrEmpty(product.getReading())
					+ " 1日量=" + (product.getDosageDailyAmount() == null ? "-" : product.getDosageDailyAmount().toPlainString()));
		}
		writer.writeSection("成分別合計");
		for (KampoIngredientTotalView total : result.getIngredientTotals()) {
			writer.writeBullet(
					valueOrEmpty(total.getIngredientName())
							+ " 合計=" + (total.getTotalAmountValue() == null ? "-" : total.getTotalAmountValue().toPlainString())
							+ " " + valueOrEmpty(total.getAmountUnit())
							+ " / 注意=" + valueOrEmpty(total.getCautionThreshold() == null ? null : total.getCautionThreshold().toPlainString())
							+ " / 要確認=" + valueOrEmpty(total.getReviewThreshold() == null ? null : total.getReviewThreshold().toPlainString())
							+ " / 判定=" + valueOrEmpty(total.getJudgementLabel()));
			writer.writeWrapped("  " + valueOrEmpty(total.getJudgementNote()));
		}
		if (!result.getWarnings().isEmpty()) {
			writer.writeSection("注意事項");
			for (String warning : result.getWarnings()) {
				writer.writeBullet(warning);
			}
		}
		writer.close();

		try (PDDocument document = new PDDocument()) {
			for (BufferedImage pageImage : pages) {
				PDPage page = new PDPage(PDRectangle.A4);
				document.addPage(page);
				var image = LosslessFactory.createFromImage(document, pageImage);
				try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
					contentStream.drawImage(image, 0, 0, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
				}
			}
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			document.save(outputStream);
			return outputStream.toByteArray();
		}
	}

	public String buildExportBaseName() {
		return "kampo_combination_dose_" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
	}

	public String buildCsvFileName() {
		return buildExportBaseName() + ".csv";
	}

	public String buildPdfFileName() {
		return buildExportBaseName() + ".pdf";
	}

	private void applyJudgement(List<KampoIngredientTotalView> ingredientTotals, List<String> warnings) {
		for (KampoIngredientTotalView total : ingredientTotals) {
			String unit = normalizeUnit(total.getAmountUnit());
			if (!"g".equalsIgnoreCase(unit)) {
				total.setJudgementLevel("REVIEW");
				total.setJudgementLabel("要確認");
				total.setJudgementNote("単位が g ではないため手動で確認してください。");
				warnings.add(total.getIngredientName() + " は g 以外の単位です。しきい値判定を手動確認してください。");
				continue;
			}

				BigDecimal totalAmount = total.getTotalAmountValue() == null ? BigDecimal.ZERO : total.getTotalAmountValue();
				ThresholdRule thresholdRule = thresholdFor(total.getIngredientName());
				if (totalAmount.compareTo(thresholdRule.reviewThreshold()) >= 0) {
					total.setJudgementLevel("REVIEW");
					total.setJudgementLabel("要確認");
					total.setJudgementNote("合計量が " + thresholdRule.reviewThreshold().toPlainString() + " g 以上です。");
				} else if (totalAmount.compareTo(thresholdRule.cautionThreshold()) >= 0) {
					total.setJudgementLevel("CAUTION");
					total.setJudgementLabel("注意");
					total.setJudgementNote("合計量が " + thresholdRule.cautionThreshold().toPlainString() + " g 以上です。");
				} else {
					total.setJudgementLevel("OK");
					total.setJudgementLabel("OK");
					total.setJudgementNote("しきい値未満です。");
				}
		}
	}

	private void setOverallJudgement(KampoCombinationDoseResultView result, List<KampoIngredientTotalView> ingredientTotals) {
		String overallLevel = "OK";
		String overallLabel = "OK";
		String overallNote = "対象成分の合計はしきい値内です。";
		for (KampoIngredientTotalView total : ingredientTotals) {
			if ("REVIEW".equals(total.getJudgementLevel())) {
				overallLevel = "REVIEW";
				overallLabel = "要確認";
				overallNote = "少なくとも 1 成分が要確認です。";
				break;
			}
			if ("CAUTION".equals(total.getJudgementLevel()) && !"REVIEW".equals(overallLevel)) {
				overallLevel = "CAUTION";
				overallLabel = "注意";
				overallNote = "少なくとも 1 成分が注意です。";
			}
		}
		result.setOverallJudgementLevel(overallLevel);
		result.setOverallJudgementLabel(overallLabel);
		result.setOverallJudgementNote(overallNote);
	}

	private static String normalizeName(String value) {
		if (!StringUtils.hasText(value)) {
			return "";
		}
		return value.replaceAll("[\\s　]+", "");
	}

	private String normalizeUnit(String value) {
		return StringUtils.hasText(value) ? value.trim() : "";
	}

	private ThresholdRule thresholdFor(String ingredientName) {
		ThresholdRule rule = THRESHOLDS.get(normalizeName(ingredientName));
		if (rule != null) {
			return rule;
		}
		return new ThresholdRule(new BigDecimal("2.5"), new BigDecimal("5.0"));
	}

	private String valueOrEmpty(String value) {
		return value == null ? "" : value;
	}

	private void appendCsvRow(StringBuilder builder, String... columns) {
		for (int i = 0; i < columns.length; i++) {
			if (i > 0) {
				builder.append(',');
			}
			builder.append(csvEscape(columns[i]));
		}
		builder.append('\n');
	}

	private String csvEscape(String value) {
		if (value == null) {
			return "";
		}
		boolean needsQuotes = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
		String escaped = value.replace("\"", "\"\"");
		return needsQuotes ? "\"" + escaped + "\"" : escaped;
	}

	private PDType0Font loadJapaneseFont(PDDocument document) throws IOException {
		for (String candidate : JAPANESE_FONT_CANDIDATES) {
			Path path = Path.of(candidate);
			if (Files.exists(path)) {
				if (candidate.toLowerCase().endsWith(".ttc")) {
					PDType0Font loaded = loadJapaneseFontFromCollection(document, path);
					if (loaded != null) {
						return loaded;
					}
				} else {
					try {
						return PDType0Font.load(document, path.toFile());
					} catch (IOException ignored) {
						// Try the next font candidate.
					}
				}
			}
		}
		throw new IOException("日本語フォントを見つけられませんでした。");
	}

	private PDType0Font loadJapaneseFontFromCollection(PDDocument document, Path path) throws IOException {
		try (TrueTypeCollection collection = new TrueTypeCollection(path.toFile())) {
			for (String fontName : JAPANESE_FONT_NAMES) {
				try {
					TrueTypeFont font = collection.getFontByName(fontName);
					if (font != null) {
						try {
							return PDType0Font.load(document, font, false);
						} catch (IOException ignored) {
							// Try the next candidate font name.
						}
					}
				} catch (IOException ignored) {
					// Try the next candidate font name.
				}
			}
			final PDType0Font[] loaded = new PDType0Font[1];
			collection.processAllFonts((TrueTypeFont font) -> {
				if (loaded[0] != null) {
					return;
				}
				try {
					loaded[0] = PDType0Font.load(document, font, false);
				} catch (IOException ignored) {
					// Continue to the next font in the collection.
				}
			});
			return loaded[0];
		}
	}

	private final class PdfWriter {
		private final PDDocument document;
		private final PDType0Font font;
		private PDPage page;
		private PDPageContentStream contentStream;
		private float y;
		private final float left = 40f;
		private final float right = 40f;
		private final float top = 48f;
		private final float bottom = 48f;
		private final float pageWidth = PDRectangle.A4.getWidth();
		private final float pageHeight = PDRectangle.A4.getHeight();

		private PdfWriter(PDDocument document, PDType0Font font) throws IOException {
			this.document = document;
			this.font = font;
			newPage();
		}

		private void writeTitle(String text) throws IOException {
			writeLine(text, 16f);
			writeBlankLine(0.4f);
		}

		private void writeSection(String text) throws IOException {
			writeBlankLine(0.8f);
			writeLine(text, 13f);
			writeLine(repeat("─", Math.min(text.length(), 12)), 9f);
		}

		private void writeKv(String label, String value) throws IOException {
			writeWrapped(label + ": " + value);
		}

		private void writeBullet(String text) throws IOException {
			writeWrapped("・" + text);
		}

		private void writeWrapped(String text) throws IOException {
			writeWrapped(text, 11f);
		}

		private void writeWrapped(String text, float fontSize) throws IOException {
			float availableWidth = pageWidth - left - right;
			for (String line : wrapText(text, fontSize, availableWidth)) {
				writeLine(line, fontSize);
			}
		}

		private List<String> wrapText(String text, float fontSize, float maxWidth) throws IOException {
			if (text == null || text.isEmpty()) {
				return List.of("");
			}
			List<String> lines = new ArrayList<>();
			StringBuilder current = new StringBuilder();
			for (int i = 0; i < text.length(); i++) {
				char ch = text.charAt(i);
				current.append(ch);
				if (stringWidth(current.toString(), fontSize) > maxWidth) {
					current.deleteCharAt(current.length() - 1);
					if (current.length() > 0) {
						lines.add(current.toString());
					}
					current.setLength(0);
					current.append(ch);
				}
				if (ch == '\n') {
					lines.add(current.toString().stripTrailing());
					current.setLength(0);
				}
			}
			if (!current.isEmpty()) {
				lines.add(current.toString().stripTrailing());
			}
			return lines;
		}

		private float stringWidth(String text, float fontSize) throws IOException {
			return font.getStringWidth(text) / 1000f * fontSize;
		}

		private void writeBlankLine(float multiplier) throws IOException {
			ensureSpace(14f * multiplier);
			y -= 14f * multiplier;
		}

		private void writeLine(String text, float fontSize) throws IOException {
			ensureSpace(fontSize + 4f);
			contentStream.beginText();
			contentStream.setFont(font, fontSize);
			contentStream.newLineAtOffset(left, y);
			contentStream.showText(text == null ? "" : text);
			contentStream.endText();
			y -= fontSize + 4f;
		}

		private void ensureSpace(float needed) throws IOException {
			if (y - needed < bottom) {
				newPage();
			}
		}

		private void newPage() throws IOException {
			closeCurrentStream();
			page = new PDPage(PDRectangle.A4);
			document.addPage(page);
			contentStream = new PDPageContentStream(document, page);
			y = pageHeight - top;
		}

		private void closeCurrentStream() throws IOException {
			if (contentStream != null) {
				contentStream.close();
				contentStream = null;
			}
		}

		private void close() throws IOException {
			closeCurrentStream();
		}

		private String repeat(String text, int count) {
			return String.join("", Collections.nCopies(Math.max(count, 0), text));
		}
	}

	private final class RasterPdfWriter {
		private final List<BufferedImage> pages;
		private final int pageWidth = 1240;
		private final int pageHeight = 1754;
		private final int left = 80;
		private final int right = 80;
		private final int top = 72;
		private final int bottom = 72;
		private final int lineGap = 8;
		private final Font titleFont = pickJapaneseFont(Font.BOLD, 28);
		private final Font sectionFont = pickJapaneseFont(Font.BOLD, 20);
		private final Font bodyFont = pickJapaneseFont(Font.PLAIN, 15);
		private final Font smallFont = pickJapaneseFont(Font.PLAIN, 13);
		private BufferedImage currentImage;
		private Graphics2D g2d;
		private int y;

		private RasterPdfWriter(List<BufferedImage> pages) {
			this.pages = pages;
			newPage();
		}

		private void writeTitle(String text) {
			writeLine(text, titleFont);
			writeBlankLine(0.5f);
		}

		private void writeSection(String text) {
			writeBlankLine(0.8f);
			writeLine(text, sectionFont);
			drawRule(Math.min(text.length(), 12) * 18);
		}

		private void writeKv(String label, String value) {
			writeWrapped(label + ": " + value, bodyFont);
		}

		private void writeBullet(String text) {
			writeWrapped("・" + text, bodyFont);
		}

		private void writeWrapped(String text) {
			writeWrapped(text, bodyFont);
		}

		private void writeWrapped(String text, Font font) {
			int availableWidth = pageWidth - left - right;
			for (String line : wrapText(text, font, availableWidth)) {
				writeLine(line, font);
			}
		}

		private List<String> wrapText(String text, Font font, int maxWidth) {
			if (text == null || text.isEmpty()) {
				return List.of("");
			}
			List<String> lines = new ArrayList<>();
			StringBuilder current = new StringBuilder();
			for (int i = 0; i < text.length(); i++) {
				char ch = text.charAt(i);
				current.append(ch);
				if (stringWidth(current.toString(), font) > maxWidth) {
					current.deleteCharAt(current.length() - 1);
					if (current.length() > 0) {
						lines.add(current.toString());
					}
					current.setLength(0);
					current.append(ch);
				}
				if (ch == '\n') {
					lines.add(current.toString().stripTrailing());
					current.setLength(0);
				}
			}
			if (!current.isEmpty()) {
				lines.add(current.toString().stripTrailing());
			}
			return lines;
		}

		private int stringWidth(String text, Font font) {
			FontMetrics metrics = g2d.getFontMetrics(font);
			return metrics.stringWidth(text);
		}

		private void writeBlankLine(float multiplier) {
			y += Math.round(lineHeight(bodyFont) * multiplier);
		}

		private void writeLine(String text, Font font) {
			int height = lineHeight(font);
			ensureSpace(height);
			g2d.setFont(font);
			FontMetrics metrics = g2d.getFontMetrics(font);
			int baseline = y + metrics.getAscent();
			g2d.drawString(text == null ? "" : text, left, baseline);
			y += height + lineGap;
		}

		private int lineHeight(Font font) {
			FontMetrics metrics = g2d.getFontMetrics(font);
			return metrics.getHeight();
		}

		private void drawRule(int length) {
			ensureSpace(16);
			g2d.drawLine(left, y + 4, left + Math.max(length, 120), y + 4);
			y += 16;
		}

		private void ensureSpace(int needed) {
			if (y + needed + bottom > pageHeight) {
				newPage();
			}
		}

		private void newPage() {
			if (g2d != null) {
				pages.add(currentImage);
				g2d.dispose();
			}
			currentImage = new BufferedImage(pageWidth, pageHeight, BufferedImage.TYPE_INT_RGB);
			g2d = currentImage.createGraphics();
			g2d.setColor(Color.WHITE);
			g2d.fillRect(0, 0, pageWidth, pageHeight);
			g2d.setColor(new Color(31, 41, 55));
			g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			y = top;
		}

		private void close() {
			if (g2d != null) {
				g2d.dispose();
				g2d = null;
			}
			if (currentImage != null) {
				pages.add(currentImage);
				currentImage = null;
			}
		}

		private Font pickJapaneseFont(int style, int size) {
			List<String> logicalFonts = List.of(Font.SANS_SERIF, Font.SERIF, Font.MONOSPACED);
			for (String family : logicalFonts) {
				Font candidate = new Font(family, style, size);
				if (candidate.canDisplayUpTo("漢字かなカナ") == -1) {
					return candidate;
				}
			}
			return new Font(Font.SANS_SERIF, style, size);
		}
	}

	private record ThresholdRule(BigDecimal cautionThreshold, BigDecimal reviewThreshold) {
	}

	private record IngredientKey(String ingredientName, String amountUnit) {
	}
}
