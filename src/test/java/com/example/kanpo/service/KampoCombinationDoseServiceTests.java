package com.example.kanpo.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.example.kanpo.repository.KampoImportRepository;
import com.example.kanpo.view.KampoCombinationDoseResultView;
import com.example.kanpo.view.KampoIngredientView;
import com.example.kanpo.view.KampoIngredientTotalView;
import com.example.kanpo.view.KampoProductView;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class KampoCombinationDoseServiceTests {

	@Test
	void calculatesTotalsForTargetIngredients() {
		KampoProductView product1 = product(1L, "1", "ツムラA", ingredient("カンゾウ", "1.5", "g"));
		KampoProductView product2 = product(2L, "2", "ツムラB", ingredient("オウゴン", "0.5", "g"), ingredient("カンゾウ", "0.5", "g"));
		KampoCombinationDoseService service = new KampoCombinationDoseService(new FakeRepository(List.of(product1, product2)));

		KampoCombinationDoseResultView result = service.calculate(List.of(1L, 2L));

		assertThat(result.getSelectedProducts()).extracting(KampoProductView::getId).containsExactly(1L, 2L);
		assertThat(result.getIngredientTotals()).hasSize(2);
		assertThat(result.getIngredientTotals().get(0).getIngredientName()).isEqualTo("カンゾウ");
		assertThat(result.getIngredientTotals().get(0).getTotalAmountValue()).isEqualByComparingTo("2.0");
		assertThat(result.getIngredientTotals().get(0).getContributionCount()).isEqualTo(2);
		assertThat(result.getIngredientTotals().get(1).getIngredientName()).isEqualTo("オウゴン");
		assertThat(result.getIngredientTotals().get(1).getTotalAmountValue()).isEqualByComparingTo("0.5");
		assertThat(result.getIngredientTotals().get(0).getJudgementLabel()).isEqualTo("OK");
		assertThat(result.getIngredientTotals().get(0).getCautionThreshold()).isEqualByComparingTo("2.5");
		assertThat(result.getIngredientTotals().get(0).getReviewThreshold()).isEqualByComparingTo("5.0");
		assertThat(result.getIngredientTotals().get(1).getCautionThreshold()).isEqualByComparingTo("2.5");
		assertThat(result.getWarnings()).isEmpty();
	}

	@Test
	void marksCautionAndReviewByThreshold() {
		KampoProductView cautionProduct = product(1L, "1", "ツムラC", ingredient("カンゾウ", "2.5", "g"));
		KampoProductView reviewProduct = product(2L, "2", "ツムラD", ingredient("マオウ", "8.0", "g"));
		KampoCombinationDoseService service = new KampoCombinationDoseService(new FakeRepository(List.of(cautionProduct, reviewProduct)));

		KampoCombinationDoseResultView result = service.calculate(List.of(1L, 2L));

		assertThat(result.getOverallJudgementLevel()).isEqualTo("REVIEW");
		assertThat(result.getIngredientTotals())
				.extracting(KampoIngredientTotalView::getIngredientName)
				.containsExactly("カンゾウ", "マオウ");
		assertThat(result.getIngredientTotals().get(0).getJudgementLabel()).isEqualTo("注意");
		assertThat(result.getIngredientTotals().get(1).getJudgementLabel()).isEqualTo("要確認");
	}

	@Test
	void buildsCsvAndPdfExports() throws Exception {
		KampoProductView product = product(1L, "1", "ツムラA", ingredient("カンゾウ", "2.5", "g"));
		KampoCombinationDoseService service = new KampoCombinationDoseService(new FakeRepository(List.of(product)));
		KampoCombinationDoseResultView result = service.calculate(List.of(1L));

		String csv = new String(service.buildCsv(result), StandardCharsets.UTF_8);
		assertThat(csv).contains("漢方薬 併用成分集計結果");
		assertThat(csv).contains("成分別合計");
		assertThat(csv).contains("カンゾウ");

		byte[] pdfBytes = service.buildPdf(result);
		assertThat(pdfBytes[0]).isEqualTo((byte) '%');
		try (var document = Loader.loadPDF(pdfBytes)) {
			String extracted = new PDFTextStripper().getText(document);
			assertThat(extracted).contains("漢方薬 併用成分集計結果");
			assertThat(extracted).contains("カンゾウ");
			assertThat(extracted).contains("要確認");
		}
	}

	private KampoProductView product(long id, String code, String name, KampoIngredientView... ingredients) {
		KampoProductView product = new KampoProductView();
		product.setId(id);
		product.setIdentificationCode(code);
		product.setSalesName(name);
		product.setIngredients(List.of(ingredients));
		return product;
	}

	private KampoIngredientView ingredient(String name, String amountValue, String unit) {
		KampoIngredientView ingredient = new KampoIngredientView();
		ingredient.setIngredientName(name);
		ingredient.setAmountValue(new BigDecimal(amountValue));
		ingredient.setAmountUnit(unit);
		return ingredient;
	}

	private static final class FakeRepository extends KampoImportRepository {

		private final List<KampoProductView> products;

		private FakeRepository(List<KampoProductView> products) {
			super((JdbcTemplate) null);
			this.products = products;
		}

		@Override
		public List<KampoProductView> findProductsByIds(List<Long> ids) {
			return products.stream()
					.filter(product -> ids.contains(product.getId()))
					.toList();
		}
	}
}
