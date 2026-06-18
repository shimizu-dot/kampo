package com.example.kanpo.view;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class KampoProductEditForm {

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
}
