package com.example.kanpo.view;

import lombok.Data;

@Data
public class KampoBackupFileView {

	private String fileName;
	private String filePath;
	private long fileSizeBytes;
	private String lastModifiedAt;
}
