package com.example.kanpo.web;

import com.example.kanpo.service.KampoBackupService;
import com.example.kanpo.view.KampoBackupResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/kampo/backup")
@Slf4j
public class KampoBackupController {

	private final KampoBackupService backupService;

	public KampoBackupController(KampoBackupService backupService) {
		this.backupService = backupService;
	}

	@GetMapping
	public String page(Model model) {
		model.addAttribute("activeTab", "backup");
		loadBackupFiles(model);
		return "kampo/backup";
	}

	@PostMapping
	public String createBackup(Model model) {
		model.addAttribute("activeTab", "backup");
		try {
			KampoBackupResult result = backupService.createBackup();
			model.addAttribute("backupResult", result);
			model.addAttribute("message", "バックアップを作成しました。");
		} catch (Exception exception) {
			log.error("Failed to create backup", exception);
			model.addAttribute("errorMessage", "バックアップの作成に失敗しました。");
			model.addAttribute("errorDetails", java.util.List.of(exception.getMessage() == null ? "詳細不明" : exception.getMessage()));
		}
		loadBackupFiles(model);
		return "kampo/backup";
	}

	@PostMapping("/restore")
	public String restore(@RequestParam("fileName") String fileName, Model model) {
		model.addAttribute("activeTab", "backup");
		try {
			KampoBackupResult result = backupService.restoreBackup(fileName);
			model.addAttribute("backupResult", result);
			model.addAttribute("message", "バックアップを復元しました。");
		} catch (Exception exception) {
			log.error("Failed to restore backup", exception);
			model.addAttribute("errorMessage", "バックアップの復元に失敗しました。");
			model.addAttribute("errorDetails", java.util.List.of(exception.getMessage() == null ? "詳細不明" : exception.getMessage()));
		}
		loadBackupFiles(model);
		return "kampo/backup";
	}

	@GetMapping("/download")
	public ResponseEntity<Resource> download(@RequestParam("fileName") String fileName) {
		try {
			Resource resource = backupService.getBackupResource(fileName);
			return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType("application/sql"))
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
				.body(resource);
		} catch (Exception exception) {
			log.error("Failed to download backup", exception);
			return ResponseEntity.notFound().build();
		}
	}

	private void loadBackupFiles(Model model) {
		try {
			var backupFiles = backupService.listBackupFiles();
			model.addAttribute("backupFiles", backupFiles);
			model.addAttribute("backupFilesCount", backupFiles.size());
			model.addAttribute("backupFilesEmpty", backupFiles.isEmpty());
		} catch (Exception exception) {
			log.error("Failed to load backup files", exception);
			model.addAttribute("backupFiles", java.util.List.of());
			model.addAttribute("backupFilesCount", 0);
			model.addAttribute("backupFilesEmpty", true);
			model.addAttribute("errorMessage", "バックアップ一覧の読み込みに失敗しました。");
			model.addAttribute("errorDetails", java.util.List.of(exception.getMessage() == null ? "詳細不明" : exception.getMessage()));
		}
	}
}
