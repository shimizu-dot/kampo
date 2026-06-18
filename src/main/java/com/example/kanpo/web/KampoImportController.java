package com.example.kanpo.web;

import com.example.kanpo.importer.KampoImportDraft;
import com.example.kanpo.importer.KampoPdfImportException;
import com.example.kanpo.importer.KampoPdfParser;
import com.example.kanpo.service.KampoImportService;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequestMapping("/kampo/import")
@SessionAttributes("draft")
@Slf4j
public class KampoImportController {

	private final KampoPdfParser pdfParser;
	private final KampoImportService importService;

	public KampoImportController(KampoPdfParser pdfParser, KampoImportService importService) {
		this.pdfParser = pdfParser;
		this.importService = importService;
	}

	@GetMapping
	public String uploadForm(Model model) {
		model.addAttribute("activeTab", "import");
		return "kampo/upload";
	}

	@GetMapping("/")
	public String root() {
		return "redirect:/kampo/import";
	}

	@PostMapping("/preview")
	public String preview(@RequestParam("file") MultipartFile file, Model model) {
		KampoImportDraft draft = pdfParser.parse(file);
		model.addAttribute("draft", draft);
		model.addAttribute("ingredientCount", draft.getIngredients().size());
		model.addAttribute("activeTab", "import");
		return "kampo/preview";
	}

	@PostMapping("/save")
	public String save(@ModelAttribute("draft") KampoImportDraft draft, SessionStatus sessionStatus, Model model) {
		try {
			long productId = importService.register(draft);
			sessionStatus.setComplete();
			model.addAttribute("productId", productId);
			model.addAttribute("identificationCode", draft.getIdentificationCode());
			model.addAttribute("salesName", draft.getSalesName());
			model.addAttribute("reading", draft.getReading());
			model.addAttribute("activeTab", "import");
			return "kampo/success";
		} catch (Exception exception) {
			log.error("Failed to save kampo draft", exception);
			model.addAttribute("errorMessage", buildSaveErrorMessage(exception));
			model.addAttribute("errorDetails", buildSaveErrorDetails(exception));
			model.addAttribute("draft", draft);
			model.addAttribute("ingredientCount", draft.getIngredients() == null ? 0 : draft.getIngredients().size());
			model.addAttribute("activeTab", "import");
			return "kampo/preview";
		}
	}

	@org.springframework.web.bind.annotation.ExceptionHandler(KampoPdfImportException.class)
	public String handleImportError(KampoPdfImportException exception, Model model) {
		model.addAttribute("errorMessage", exception.getMessage());
		model.addAttribute("draft", new KampoImportDraft());
		model.addAttribute("activeTab", "import");
		return "kampo/upload";
	}

	private String buildSaveErrorMessage(Exception exception) {
		Throwable rootCause = getRootCause(exception);
		if (rootCause instanceof CannotGetJdbcConnectionException) {
			return "PostgreSQL に接続できませんでした。DB が起動しているか、接続先設定が正しいか確認してください。";
		}
		if (rootCause instanceof SQLException sqlException) {
			String sqlState = sqlException.getSQLState();
			if ("23505".equals(sqlState)) {
				return "この販売名はすでに登録されています。既存レコードの販売名を変更するか、更新処理にしてください。";
			}
			if ("42P01".equals(sqlState)) {
				return "DB のテーブルが見つかりません。`kampo_products` などの初期化ができていません。";
			}
			if ("23502".equals(sqlState)) {
				return "必須項目が空です。抽出結果に未入力の値がないか確認してください。";
			}
			if ("23503".equals(sqlState)) {
				return "外部キー制約に違反しました。親データが未作成の可能性があります。";
			}
		}
		if (exception instanceof DataIntegrityViolationException) {
			return "DB 制約に違反しました。重複登録、必須項目不足、または関連データ不足の可能性があります。";
		}
		return "登録に失敗しました。ログを確認してください。";
	}

	private List<String> buildSaveErrorDetails(Exception exception) {
		List<String> details = new ArrayList<>();
		Throwable current = exception;
		while (current != null) {
			if (current instanceof SQLException sqlException) {
				details.add("SQLState=" + sqlException.getSQLState() + ", errorCode=" + sqlException.getErrorCode());
				details.add(sqlException.getMessage());
				break;
			}
			current = current.getCause();
		}
		if (details.isEmpty()) {
			Throwable rootCause = getRootCause(exception);
			if (rootCause != null && rootCause.getMessage() != null) {
				details.add(rootCause.getMessage());
			}
		}
		return details;
	}

	private Throwable getRootCause(Throwable throwable) {
		Throwable current = throwable;
		while (current.getCause() != null && current.getCause() != current) {
			current = current.getCause();
		}
		return current;
	}

}
