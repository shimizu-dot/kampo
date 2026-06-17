package com.example.kanpo.importer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class KampoImportDraft {

	private String sourceFileName;
	private String sourceDocumentNo;
	private String identificationCode;
	private String salesName;
	private String efficacyConditionText;
	private String efficacyIndicationText;
	private BigDecimal dosageDailyAmount;
	private String dosageInstructionsText;
	private List<KampoIngredientDraft> ingredients = new ArrayList<>();
}
