package com.example.kanpo.view;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class KampoIngredientView {

	private int sortOrder;
	private String ingredientName;
	private BigDecimal amountValue;
	private String amountUnit;
	private String rawAmountText;
	private String highlightedIngredientName;
	private String highlightedAmountUnit;
	private String highlightedRawAmountText;
}
