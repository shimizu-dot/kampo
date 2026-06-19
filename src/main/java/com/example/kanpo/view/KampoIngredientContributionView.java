package com.example.kanpo.view;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class KampoIngredientContributionView {

	private long productId;
	private String identificationCode;
	private String salesName;
	private BigDecimal amountValue;
	private String amountUnit;
}
