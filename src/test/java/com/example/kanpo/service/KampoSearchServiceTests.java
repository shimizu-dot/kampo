package com.example.kanpo.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import com.example.kanpo.repository.KampoImportRepository;
import com.example.kanpo.view.KampoIngredientView;
import com.example.kanpo.view.KampoProductView;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class KampoSearchServiceTests {

	@Test
	void searchesMultipleIdentificationCodes() {
		KampoProductView product1 = product(1L, "101");
		KampoProductView product2 = product(2L, "102");
		KampoProductView product3 = product(3L, "103");
		KampoSearchService service = new KampoSearchService(new FakeRepository(List.of(product1, product2, product3)), new KampoInputValidator());

		List<KampoProductView> results = service.searchByIdentificationCode("101, 102\n103");

		assertThat(results).extracting(KampoProductView::getId).containsExactly(1L, 2L, 3L);
	}

	@Test
	void removesDuplicateResultsWhilePreservingOrder() {
		KampoProductView product1 = product(1L, "101");
		KampoProductView product2 = product(2L, "102");
		KampoSearchService service = new KampoSearchService(new FakeRepository(List.of(product1, product2)), new KampoInputValidator());

		List<KampoProductView> results = service.searchByIdentificationCode("101 102 101");

		assertThat(results).extracting(KampoProductView::getId).containsExactly(1L, 2L);
	}

	private KampoProductView product(long id, String code) {
		KampoProductView product = new KampoProductView();
		product.setId(id);
		product.setIdentificationCode(code);
		product.setSalesName("ツムラ" + code);
		product.setReading("ツムラ" + code);
		product.setIngredients(List.of(dummyIngredient()));
		return product;
	}

	private KampoIngredientView dummyIngredient() {
		KampoIngredientView ingredient = new KampoIngredientView();
		ingredient.setIngredientName("カンゾウ");
		ingredient.setAmountValue(new BigDecimal("0.1"));
		ingredient.setAmountUnit("g");
		return ingredient;
	}

	private static final class FakeRepository extends KampoImportRepository {

		private final List<KampoProductView> products;

		private FakeRepository(List<KampoProductView> products) {
			super((JdbcTemplate) null);
			this.products = products;
		}

		@Override
		public List<KampoProductView> findProductsByIdentificationCode(String identificationCode) {
			return products.stream()
					.filter(product -> product.getIdentificationCode().equals(identificationCode))
					.toList();
		}
	}
}
