package com.example.kanpo.service;

import java.util.List;

import com.example.kanpo.view.KampoProductEditForm;
import com.example.kanpo.repository.KampoImportRepository;
import com.example.kanpo.view.KampoProductView;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KampoSearchService {

	private final KampoImportRepository repository;

	public KampoSearchService(KampoImportRepository repository) {
		this.repository = repository;
	}

	public List<KampoProductView> searchByIdentificationCode(String identificationCode) {
		if (!StringUtils.hasText(identificationCode)) {
			return List.of();
		}
		return repository.findProductsByIdentificationCode(identificationCode.trim());
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
		repository.updateProduct(form);
	}
}
