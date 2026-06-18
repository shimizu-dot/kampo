package com.example.kanpo.view;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class KampoProductView {

	private long id;
	private String identificationCode;
	private String salesName;
	private String reading;
	private String efficacyConditionText;
	private String efficacyIndicationText;
	private BigDecimal dosageDailyAmount;
	private String dosageInstructionsText;
	private String sourceFileName;
	private String sourceDocumentNo;
	private List<KampoIngredientView> ingredients = new ArrayList<>();
	private String highlightedIdentificationCode;
	private String highlightedSalesName;
	private String highlightedReading;
	private String highlightedEfficacyConditionText;
	private String highlightedEfficacyIndicationText;
	private String highlightedDosageInstructionsText;
	private String highlightedSourceFileName;
	private String highlightedSourceDocumentNo;
}
