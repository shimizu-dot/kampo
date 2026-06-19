package com.example.kanpo.view;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class KampoCombinationDoseResultView {

	private List<KampoProductView> selectedProducts = new ArrayList<>();
	private List<KampoIngredientTotalView> ingredientTotals = new ArrayList<>();
	private List<String> warnings = new ArrayList<>();
	private String overallJudgementLevel;
	private String overallJudgementLabel;
	private String overallJudgementNote;
	private boolean listAll;
	private int currentPage;
	private int pageSize;
	private long totalCount;
	private int totalPages;
	private int pageFrom;
	private int pageTo;
	private List<Integer> pageNumbers = new ArrayList<>();
}
