package com.example.kanpo.view;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class KampoIngredientTotalView {

	private String ingredientName;
	private String amountUnit;
	private BigDecimal totalAmountValue = BigDecimal.ZERO;
	private int contributionCount;
	private List<KampoIngredientContributionView> contributions = new ArrayList<>();
	private String judgementLevel;
	private String judgementLabel;
	private String judgementNote;
	private BigDecimal cautionThreshold;
	private BigDecimal reviewThreshold;
}
