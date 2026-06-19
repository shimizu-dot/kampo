package com.example.kanpo.web;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.example.kanpo.service.KampoCombinationDoseService;
import com.example.kanpo.service.KampoSearchService;
import com.example.kanpo.view.KampoCombinationDoseResultView;
import com.example.kanpo.view.KampoProductView;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/kampo/combination-dose")
@Slf4j
public class KampoCombinationDoseController {

	private static final int LIST_PAGE_SIZE = 20;

	private final KampoSearchService searchService;
	private final KampoCombinationDoseService combinationDoseService;

	public KampoCombinationDoseController(KampoSearchService searchService, KampoCombinationDoseService combinationDoseService) {
		this.searchService = searchService;
		this.combinationDoseService = combinationDoseService;
	}

	@GetMapping
	public String page(
			@RequestParam(name = "searchType", required = false) String searchType,
			@RequestParam(name = "searchKeyword", required = false) String searchKeyword,
			@RequestParam(name = "listAll", required = false, defaultValue = "false") boolean listAll,
			@RequestParam(name = "page", required = false, defaultValue = "1") int page,
			@RequestParam(name = "selectedProductIds", required = false) List<Long> selectedProductIds,
			Model model) {
		try {
			preparePage(searchType, searchKeyword, listAll, page, selectedProductIds, model);
			model.addAttribute("activeTab", "combination-dose");
			return "kampo/combination-dose";
		} catch (Exception exception) {
			log.error("Failed to render combination dose page", exception);
			model.addAttribute("searchType", normalizeSearchType(searchType));
			model.addAttribute("searchKeyword", searchKeyword == null ? "" : searchKeyword.trim());
			model.addAttribute("searched", false);
			model.addAttribute("listAll", listAll);
			model.addAttribute("selectedProductIds", selectedProductIds == null ? List.of() : selectedProductIds);
			model.addAttribute("candidates", List.of());
			model.addAttribute("currentPage", 1);
			model.addAttribute("pageSize", LIST_PAGE_SIZE);
			model.addAttribute("totalPages", 0);
			model.addAttribute("totalCount", 0L);
			model.addAttribute("pageFrom", 0);
			model.addAttribute("pageTo", 0);
			model.addAttribute("pageNumbers", List.of());
			model.addAttribute("activeTab", "combination-dose");
			model.addAttribute("errorMessage", buildSearchErrorMessage(exception));
			return "kampo/combination-dose";
		}
	}

	@PostMapping
	public String calculate(
			@RequestParam(name = "searchType", required = false) String searchType,
			@RequestParam(name = "searchKeyword", required = false) String searchKeyword,
			@RequestParam(name = "listAll", required = false, defaultValue = "false") boolean listAll,
			@RequestParam(name = "page", required = false, defaultValue = "1") int page,
			@RequestParam(name = "selectedProductIds", required = false) List<Long> selectedProductIds,
			Model model) {
		try {
			preparePage(searchType, searchKeyword, listAll, page, selectedProductIds, model);
			if (selectedProductIds == null || selectedProductIds.isEmpty()) {
				model.addAttribute("message", "判定する薬剤を選択してください。");
			} else {
				KampoCombinationDoseResultView result = combinationDoseService.calculate(selectedProductIds);
				model.addAttribute("result", result);
				if (result.getSelectedProducts().isEmpty()) {
					model.addAttribute("message", "選択された薬剤が見つかりませんでした。");
				} else if (result.getIngredientTotals().isEmpty()) {
					model.addAttribute("message", "選択した薬剤に、対象の 5 成分は含まれていませんでした。");
				}
			}
			model.addAttribute("activeTab", "combination-dose");
			return "kampo/combination-dose";
		} catch (Exception exception) {
			log.error("Failed to calculate combination dose", exception);
			model.addAttribute("searchType", normalizeSearchType(searchType));
			model.addAttribute("searchKeyword", searchKeyword == null ? "" : searchKeyword.trim());
			model.addAttribute("searched", false);
			model.addAttribute("listAll", listAll);
			model.addAttribute("selectedProductIds", selectedProductIds == null ? List.of() : selectedProductIds);
			model.addAttribute("candidates", List.of());
			model.addAttribute("currentPage", 1);
			model.addAttribute("pageSize", LIST_PAGE_SIZE);
			model.addAttribute("totalPages", 0);
			model.addAttribute("totalCount", 0L);
			model.addAttribute("pageFrom", 0);
			model.addAttribute("pageTo", 0);
			model.addAttribute("pageNumbers", List.of());
			model.addAttribute("activeTab", "combination-dose");
			model.addAttribute("errorMessage", buildSearchErrorMessage(exception));
			return "kampo/combination-dose";
		}
	}

	@PostMapping("/export/csv")
	public ResponseEntity<byte[]> exportCsv(@RequestParam(name = "selectedProductIds", required = false) List<Long> selectedProductIds) {
		return buildExportResponse(selectedProductIds, "csv");
	}

	@GetMapping("/export/csv")
	public ResponseEntity<byte[]> exportCsvGet(@RequestParam(name = "selectedProductIds", required = false) List<Long> selectedProductIds) {
		return buildExportResponse(selectedProductIds, "csv");
	}

	@PostMapping("/export/pdf")
	public ResponseEntity<byte[]> exportPdf(@RequestParam(name = "selectedProductIds", required = false) List<Long> selectedProductIds) {
		return buildExportResponse(selectedProductIds, "pdf");
	}

	@GetMapping("/export/pdf")
	public ResponseEntity<byte[]> exportPdfGet(@RequestParam(name = "selectedProductIds", required = false) List<Long> selectedProductIds) {
		return buildExportResponse(selectedProductIds, "pdf");
	}

	private void preparePage(
			String searchType,
			String searchKeyword,
			boolean listAll,
			int page,
			List<Long> selectedProductIds,
			Model model) {
		String normalizedSearchType = normalizeSearchType(searchType);
		String keyword = searchKeyword == null ? "" : searchKeyword.trim();
		List<KampoProductView> candidates = List.of();
		int currentPage = 1;
		int totalPages = 0;
		long totalCount = 0L;
		int pageFrom = 0;
		int pageTo = 0;
		boolean searched = false;

		if (listAll) {
			totalCount = searchService.countAllProducts();
			totalPages = totalCount == 0 ? 0 : (int) Math.ceil((double) totalCount / LIST_PAGE_SIZE);
			currentPage = totalPages == 0 ? 1 : Math.min(Math.max(page, 1), totalPages);
			int offset = Math.max(currentPage - 1, 0) * LIST_PAGE_SIZE;
			candidates = searchService.listAllSortedByIdentificationCode(LIST_PAGE_SIZE, offset);
			pageFrom = candidates.isEmpty() ? 0 : offset + 1;
			pageTo = candidates.isEmpty() ? 0 : offset + candidates.size();
			searched = true;
		} else if (StringUtils.hasText(keyword)) {
			candidates = searchCandidates(normalizedSearchType, keyword);
			searched = true;
		}

		model.addAttribute("searchType", normalizedSearchType);
		model.addAttribute("searchKeyword", keyword);
		model.addAttribute("searched", searched);
		model.addAttribute("listAll", listAll);
		model.addAttribute("selectedProductIds", selectedProductIds == null ? List.of() : selectedProductIds);
		model.addAttribute("candidates", candidates);
		model.addAttribute("currentPage", currentPage);
		model.addAttribute("pageSize", LIST_PAGE_SIZE);
		model.addAttribute("totalPages", totalPages);
		model.addAttribute("totalCount", totalCount);
		model.addAttribute("pageFrom", pageFrom);
		model.addAttribute("pageTo", pageTo);
		model.addAttribute("pageNumbers", buildPageNumbers(currentPage, totalPages));
	}

	private List<KampoProductView> searchCandidates(String searchType, String keyword) {
		return switch (searchType) {
			case "INGREDIENT_NAME" -> searchService.searchByIngredientName(keyword);
			case "SUMMARY_TEXT" -> searchService.searchBySummaryText(keyword);
			default -> searchService.searchByIdentificationCode(keyword);
		};
	}

	private String normalizeSearchType(String searchType) {
		if ("INGREDIENT_NAME".equals(searchType) || "SUMMARY_TEXT".equals(searchType)) {
			return searchType;
		}
		return "IDENTIFICATION_CODE";
	}

	private List<Integer> buildPageNumbers(int currentPage, int totalPages) {
		List<Integer> pageNumbers = new ArrayList<>();
		if (totalPages <= 0) {
			return pageNumbers;
		}
		int start = Math.max(1, currentPage - 2);
		int end = Math.min(totalPages, start + 4);
		start = Math.max(1, end - 4);
		for (int i = start; i <= end; i++) {
			pageNumbers.add(i);
		}
		return pageNumbers;
	}

	private String buildSearchErrorMessage(Exception exception) {
		Throwable rootCause = getRootCause(exception);
		if (rootCause instanceof CannotGetJdbcConnectionException) {
			return "PostgreSQL に接続できませんでした。DB が起動しているか、接続先設定が正しいか確認してください。";
		}
		if (rootCause instanceof SQLException sqlException) {
			String sqlState = sqlException.getSQLState();
			if ("42P01".equals(sqlState)) {
				return "DB のテーブルが見つかりません。`kampo_products` などの初期化ができていません。";
			}
		}
		return "判定ページの表示に失敗しました。ログを確認してください。";
	}

	private ResponseEntity<byte[]> buildExportResponse(List<Long> selectedProductIds, String format) {
		try {
			if (selectedProductIds == null || selectedProductIds.isEmpty()) {
				return ResponseEntity.badRequest()
						.contentType(MediaType.parseMediaType("text/plain; charset=UTF-8"))
						.body("判定する薬剤を選択してください。".getBytes(java.nio.charset.StandardCharsets.UTF_8));
			}
			KampoCombinationDoseResultView result = combinationDoseService.calculate(selectedProductIds);
			byte[] content;
			String fileName;
			MediaType contentType;
			if ("pdf".equals(format)) {
				content = combinationDoseService.buildPdf(result);
				fileName = combinationDoseService.buildPdfFileName();
				contentType = MediaType.APPLICATION_PDF;
			} else {
				content = combinationDoseService.buildCsv(result);
				fileName = combinationDoseService.buildCsvFileName();
				contentType = MediaType.parseMediaType("text/csv; charset=UTF-8");
			}
			String contentDisposition = "pdf".equals(format)
					? "inline; filename=\"" + fileName + "\""
					: "attachment; filename=\"" + fileName + "\"";
			return ResponseEntity.ok()
					.contentType(contentType)
					.header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
					.body(content);
		} catch (Throwable exception) {
			Throwable rootCause = getRootCause(exception);
			log.error("Failed to export combination dose report: {}: {}", rootCause.getClass().getName(), rootCause.getMessage(), exception);
			return ResponseEntity.internalServerError()
					.contentType(MediaType.parseMediaType("text/plain; charset=UTF-8"))
					.body(("出力に失敗しました。原因: " + rootCause.getClass().getSimpleName()
							+ (rootCause.getMessage() == null ? "" : " - " + rootCause.getMessage()))
							.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		}
	}

	private Throwable getRootCause(Throwable throwable) {
		Throwable current = throwable;
		while (current.getCause() != null && current.getCause() != current) {
			current = current.getCause();
		}
		return current;
	}
}
