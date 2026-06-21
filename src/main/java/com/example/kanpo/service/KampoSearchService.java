package com.example.kanpo.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.kanpo.view.KampoProductEditForm;
import com.example.kanpo.repository.KampoImportRepository;
import com.example.kanpo.view.KampoProductView;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KampoSearchService {

	private final KampoImportRepository repository;
	private final KampoInputValidator inputValidator;

	public KampoSearchService(KampoImportRepository repository, KampoInputValidator inputValidator) {
		this.repository = repository;
		this.inputValidator = inputValidator;
	}

	public List<KampoProductView> searchByIdentificationCode(String identificationCode) {
		if (!StringUtils.hasText(identificationCode)) {
			return List.of();
		}
		List<String> codes = splitIdentificationCodes(identificationCode);
		if (codes.isEmpty()) {
			return List.of();
		}
		Map<Long, KampoProductView> uniqueProducts = new LinkedHashMap<>();
		for (String code : codes) {
			for (KampoProductView product : repository.findProductsByIdentificationCode(code)) {
				uniqueProducts.putIfAbsent(product.getId(), product);
			}
		}
		return new ArrayList<>(uniqueProducts.values());
	}

	public List<KampoProductView> searchByIngredientName(String ingredientName) {
		if (!StringUtils.hasText(ingredientName)) {
			return List.of();
		}
		return repository.findProductsByIngredientName(ingredientName.trim());
	}

	public List<KampoProductView> searchBySummaryText(String summaryText) {
		if (!StringUtils.hasText(summaryText)) {
			return List.of();
		}
		return repository.findProductsBySummaryText(summaryText.trim());
	}

	public List<KampoProductView> listAllSortedByIdentificationCode() {
		return repository.findAllProductsSortedByIdentificationCode();
	}

	public List<KampoProductView> listAllSortedByIdentificationCode(int limit, int offset) {
		return repository.findAllProductsSortedByIdentificationCode(limit, offset);
	}

	public long countAllProducts() {
		return repository.countAllProducts();
	}

	public KampoProductView findProductById(long id) {
		return repository.findProductById(id);
	}

	@Transactional
	public void updateProduct(KampoProductEditForm form) {
		inputValidator.validateSalesName(form.getSalesName());
		inputValidator.validateReading(form.getReading());
		repository.updateProduct(form);
	}

	private List<String> splitIdentificationCodes(String identificationCode) {
		String normalized = identificationCode.trim();
		if (!StringUtils.hasText(normalized)) {
			return List.of();
		}
		List<String> codes = new ArrayList<>();
		for (String token : normalized.split("[\\s　,、;；]+")) {
			if (StringUtils.hasText(token)) {
				codes.add(token.trim());
			}
		}
		if (codes.isEmpty()) {
			return List.of(normalized);
		}
		return codes;
	}
}
