package com.example.kanpo.view;

import lombok.Data;

@Data
public class KampoBackupResult {

	private String fileName;
	private String filePath;
	private long fileSizeBytes;
	private String createdAt;
	private long productCount;
	private long ingredientCount;
	private long productIngredientCount;
}
