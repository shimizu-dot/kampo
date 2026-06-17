package com.example.kanpo.importer;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class KampoIngredientDraft {

	private String ingredientName;
	private BigDecimal amountValue;
	private String amountUnit;
	private int sortOrder;
	private String rawAmountText;
}
