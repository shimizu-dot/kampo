package com.example.kanpo.service;

import com.example.kanpo.importer.KampoImportDraft;
import com.example.kanpo.repository.KampoImportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KampoImportService {

	private final KampoImportRepository repository;
	private final KampoInputValidator inputValidator;

	public KampoImportService(KampoImportRepository repository, KampoInputValidator inputValidator) {
		this.repository = repository;
		this.inputValidator = inputValidator;
	}

	@Transactional
	public long register(KampoImportDraft draft) {
		inputValidator.validateSalesName(draft.getSalesName());
		inputValidator.validateReading(draft.getReading());
		long productId = repository.insertProduct(draft);
		for (var ingredient : draft.getIngredients()) {
			long ingredientId = repository.findOrCreateIngredient(ingredient.getIngredientName());
			repository.insertProductIngredient(productId, ingredientId, ingredient);
		}
		return productId;
	}
}
