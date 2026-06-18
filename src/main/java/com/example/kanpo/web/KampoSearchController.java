package com.example.kanpo.web;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.example.kanpo.service.KampoSearchService;
import com.example.kanpo.view.KampoProductEditForm;
import com.example.kanpo.view.KampoIngredientView;
import com.example.kanpo.view.KampoProductView;
import com.example.kanpo.view.TextHighlightService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
@RequestMapping("/kampo/search")
@Slf4j
public class KampoSearchController {

	private static final int LIST_PAGE_SIZE = 20;

	private final KampoSearchService searchService;
	private final TextHighlightService textHighlightService;

	public KampoSearchController(KampoSearchService searchService, TextHighlightService textHighlightService) {
		this.searchService = searchService;
		this.textHighlightService = textHighlightService;
	}

	@GetMapping
	public String search(
			@RequestParam(name = "searchType", required = false) String searchType,
			@RequestParam(name = "searchKeyword", required = false) String searchKeyword,
			@RequestParam(name = "identificationCode", required = false) String identificationCode,
			@RequestParam(name = "ingredientName", required = false) String ingredientName,
			@RequestParam(name = "summaryText", required = false) String summaryText,
			@RequestParam(name = "listAll", required = false, defaultValue = "false") boolean listAll,
			Model model) {
		String queryType = normalizeSearchType(searchType);
		String keyword = searchKeyword == null ? "" : searchKeyword.trim();
		String legacyIdentificationQuery = identificationCode == null ? "" : identificationCode.trim();
		String legacyIngredientQuery = ingredientName == null ? "" : ingredientName.trim();
		String legacySummaryQuery = summaryText == null ? "" : summaryText.trim();
		if (!StringUtils.hasText(keyword)) {
			if (StringUtils.hasText(legacyIdentificationQuery)) {
				queryType = "IDENTIFICATION_CODE";
				keyword = legacyIdentificationQuery;
			} else if (StringUtils.hasText(legacyIngredientQuery)) {
				queryType = "INGREDIENT_NAME";
				keyword = legacyIngredientQuery;
			} else if (StringUtils.hasText(legacySummaryQuery)) {
				queryType = "SUMMARY_TEXT";
				keyword = legacySummaryQuery;
			}
		}

		model.addAttribute("searchType", queryType);
		model.addAttribute("searchKeyword", keyword);
		model.addAttribute("highlightQuery", "");
		model.addAttribute("listAll", listAll);
		model.addAttribute("activeTab", listAll ? "list" : "search");

		try {
			if (!StringUtils.hasText(keyword)) {
				model.addAttribute("searched", false);
				model.addAttribute("products", List.of());
				model.addAttribute("message", "検索語を入力してください。");
				return "kampo/search";
			}

			List<KampoProductView> products;
			String modeLabel;
			if ("INGREDIENT_NAME".equals(queryType)) {
				products = searchService.searchByIngredientName(keyword);
				modeLabel = "成分「" + keyword + "」";
			} else if ("SUMMARY_TEXT".equals(queryType)) {
				products = searchService.searchBySummaryText(keyword);
				modeLabel = "摘要「" + keyword + "」";
			} else {
				products = searchService.searchByIdentificationCode(keyword);
				modeLabel = "コード「" + keyword + "」";
			}
			model.addAttribute("highlightQuery", keyword);

			products = decorateProducts(products, keyword);
			model.addAttribute("searched", true);
			model.addAttribute("products", products);
			model.addAttribute("resultCount", products.size());
			model.addAttribute("modeLabel", modeLabel);
			model.addAttribute("listAll", false);
			if (products.isEmpty()) {
				model.addAttribute("message", buildNotFoundMessage(queryType, keyword));
			}
			return "kampo/search";
		} catch (Exception exception) {
			log.error("Failed to search kampo products", exception);
			model.addAttribute("searched", false);
			model.addAttribute("products", List.of());
			model.addAttribute("errorMessage", buildSearchErrorMessage(exception));
			model.addAttribute("errorDetails", buildSearchErrorDetails(exception));
			return "kampo/search";
		}
	}

	@GetMapping(params = "listAll=true")
	public String listAll(@RequestParam(name = "page", required = false, defaultValue = "1") int page, Model model) {
		long totalCount = searchService.countAllProducts();
		int totalPages = totalCount == 0 ? 0 : (int) Math.ceil((double) totalCount / LIST_PAGE_SIZE);
		int currentPage = totalPages == 0 ? 1 : Math.min(Math.max(page, 1), totalPages);
		int offset = Math.max(currentPage - 1, 0) * LIST_PAGE_SIZE;
		List<KampoProductView> products = decorateProducts(searchService.listAllSortedByIdentificationCode(LIST_PAGE_SIZE, offset), "");
		int fromCount = products.isEmpty() ? 0 : offset + 1;
		int toCount = products.isEmpty() ? 0 : offset + products.size();
		model.addAttribute("searchType", "IDENTIFICATION_CODE");
		model.addAttribute("searchKeyword", "");
		model.addAttribute("highlightQuery", "");
		model.addAttribute("listAll", true);
		model.addAttribute("activeTab", "list");
		model.addAttribute("searched", true);
		model.addAttribute("products", products);
		model.addAttribute("resultCount", products.size());
		model.addAttribute("totalCount", totalCount);
		model.addAttribute("currentPage", currentPage);
		model.addAttribute("pageSize", LIST_PAGE_SIZE);
		model.addAttribute("totalPages", totalPages);
		model.addAttribute("pageFrom", fromCount);
		model.addAttribute("pageTo", toCount);
		model.addAttribute("pageNumbers", buildPageNumbers(currentPage, totalPages));
		model.addAttribute("modeLabel", "全件一覧");
		if (products.isEmpty()) {
			model.addAttribute("message", "登録データはまだありません。");
		}
		return "kampo/search";
	}

	@GetMapping("/{id}/edit")
	public String editForm(
			@PathVariable("id") long id,
			@RequestParam(name = "searchType", required = false) String searchType,
			@RequestParam(name = "searchKeyword", required = false) String searchKeyword,
			Model model) {
		KampoProductView product = searchService.findProductById(id);
		if (product == null) {
			model.addAttribute("errorMessage", "編集対象のデータが見つかりませんでした。");
			model.addAttribute("searchType", normalizeSearchType(searchType));
			model.addAttribute("searchKeyword", searchKeyword == null ? "" : searchKeyword.trim());
			model.addAttribute("activeTab", "search");
			return "kampo/search";
		}
		model.addAttribute("editForm", toEditForm(product));
		model.addAttribute("product", product);
		model.addAttribute("searchType", normalizeSearchType(searchType));
		model.addAttribute("searchKeyword", searchKeyword == null ? "" : searchKeyword.trim());
		model.addAttribute("backUrl", buildSearchBackUrl(searchType, searchKeyword));
		model.addAttribute("activeTab", "search");
		return "kampo/edit";
	}

	@PostMapping("/{id}/edit")
	public String update(
			@PathVariable("id") long id,
			@ModelAttribute("editForm") KampoProductEditForm editForm,
			BindingResult bindingResult,
			@RequestParam(name = "searchType", required = false) String searchType,
			@RequestParam(name = "searchKeyword", required = false) String searchKeyword,
			Model model,
			RedirectAttributes redirectAttributes) {
		editForm.setId(id);
		if (bindingResult.hasErrors()) {
			model.addAttribute("product", searchService.findProductById(id));
			model.addAttribute("searchType", normalizeSearchType(searchType));
			model.addAttribute("searchKeyword", searchKeyword == null ? "" : searchKeyword.trim());
			model.addAttribute("backUrl", buildSearchBackUrl(searchType, searchKeyword));
			model.addAttribute("activeTab", "search");
			model.addAttribute("errorMessage", "入力内容に誤りがあります。");
			return "kampo/edit";
		}
		try {
			searchService.updateProduct(editForm);
			redirectAttributes.addFlashAttribute("successMessage", "データを更新しました。");
			return "redirect:" + UriComponentsBuilder.fromPath("/kampo/search")
					.queryParam("searchType", "IDENTIFICATION_CODE")
					.queryParam("searchKeyword", editForm.getIdentificationCode())
					.build()
					.encode()
					.toUriString();
		} catch (Exception exception) {
			log.error("Failed to update kampo product", exception);
			model.addAttribute("product", searchService.findProductById(id));
			model.addAttribute("searchType", normalizeSearchType(searchType));
			model.addAttribute("searchKeyword", searchKeyword == null ? "" : searchKeyword.trim());
			model.addAttribute("backUrl", buildSearchBackUrl(searchType, searchKeyword));
			model.addAttribute("activeTab", "search");
			model.addAttribute("errorMessage", buildUpdateErrorMessage(exception));
			model.addAttribute("errorDetails", buildUpdateErrorDetails(exception));
			return "kampo/edit";
		}
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
		if (exception instanceof DataIntegrityViolationException) {
			return "DB 制約に違反しました。";
		}
		return "検索に失敗しました。ログを確認してください。";
	}

	private String buildUpdateErrorMessage(Exception exception) {
		Throwable rootCause = getRootCause(exception);
		if (rootCause instanceof IllegalArgumentException illegalArgumentException) {
			return illegalArgumentException.getMessage();
		}
		if (rootCause instanceof CannotGetJdbcConnectionException) {
			return "PostgreSQL に接続できませんでした。DB が起動しているか、接続先設定が正しいか確認してください。";
		}
		if (rootCause instanceof SQLException sqlException) {
			String sqlState = sqlException.getSQLState();
			if ("23505".equals(sqlState)) {
				return "この販売名はすでに登録されています。別の販売名に変更してください。";
			}
			if ("42P01".equals(sqlState)) {
				return "DB のテーブルが見つかりません。`kampo_products` などの初期化ができていません。";
			}
			if ("23502".equals(sqlState)) {
				return "必須項目が空です。入力内容を確認してください。";
			}
			if ("23503".equals(sqlState)) {
				return "外部キー制約に違反しました。関連データが未作成の可能性があります。";
			}
		}
		if (exception instanceof DataIntegrityViolationException) {
			return "DB 制約に違反しました。重複登録、必須項目不足、または関連データ不足の可能性があります。";
		}
		return "更新に失敗しました。ログを確認してください。";
	}

	private List<String> buildUpdateErrorDetails(Exception exception) {
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

	private List<String> buildSearchErrorDetails(Exception exception) {
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

	private String normalizeSearchType(String searchType) {
		if (!StringUtils.hasText(searchType)) {
			return "IDENTIFICATION_CODE";
		}
		return switch (searchType.trim()) {
			case "IDENTIFICATION_CODE", "INGREDIENT_NAME", "SUMMARY_TEXT" -> searchType.trim();
			default -> "IDENTIFICATION_CODE";
		};
	}

	private String buildNotFoundMessage(String searchType, String keyword) {
		return switch (searchType) {
			case "INGREDIENT_NAME" -> "成分 " + keyword + " を含む登録データは見つかりませんでした。";
			case "SUMMARY_TEXT" -> "摘要 " + keyword + " を含む登録データは見つかりませんでした。";
			default -> "コード " + keyword + " の登録データは見つかりませんでした。";
		};
	}

	private String buildSearchBackUrl(String searchType, String searchKeyword) {
		String normalizedType = normalizeSearchType(searchType);
		String keyword = searchKeyword == null ? "" : searchKeyword.trim();
		if (!StringUtils.hasText(keyword)) {
			return "/kampo/search";
		}
		return UriComponentsBuilder.fromPath("/kampo/search")
				.queryParam("searchType", normalizedType)
				.queryParam("searchKeyword", keyword)
				.build()
				.encode()
				.toUriString();
	}

	private KampoProductEditForm toEditForm(KampoProductView product) {
		KampoProductEditForm form = new KampoProductEditForm();
		form.setId(product.getId());
		form.setIdentificationCode(product.getIdentificationCode());
		form.setSalesName(product.getSalesName());
		form.setReading(product.getReading());
		form.setEfficacyConditionText(product.getEfficacyConditionText());
		form.setEfficacyIndicationText(product.getEfficacyIndicationText());
		form.setDosageDailyAmount(product.getDosageDailyAmount());
		form.setDosageInstructionsText(product.getDosageInstructionsText());
		form.setSourceFileName(product.getSourceFileName());
		form.setSourceDocumentNo(product.getSourceDocumentNo());
		return form;
	}

	private List<KampoProductView> decorateProducts(List<KampoProductView> products, String highlightQuery) {
		if (products == null || products.isEmpty()) {
			return Collections.emptyList();
		}
		for (KampoProductView product : products) {
			product.setHighlightedIdentificationCode(textHighlightService.highlight(product.getIdentificationCode(), highlightQuery));
			product.setHighlightedSalesName(textHighlightService.highlight(product.getSalesName(), highlightQuery));
			product.setHighlightedReading(textHighlightService.highlight(product.getReading(), highlightQuery));
			product.setHighlightedEfficacyConditionText(textHighlightService.highlight(product.getEfficacyConditionText(), highlightQuery));
			product.setHighlightedEfficacyIndicationText(textHighlightService.highlight(product.getEfficacyIndicationText(), highlightQuery));
			product.setHighlightedDosageInstructionsText(textHighlightService.highlight(product.getDosageInstructionsText(), highlightQuery));
			product.setHighlightedSourceFileName(textHighlightService.highlight(product.getSourceFileName(), highlightQuery));
			product.setHighlightedSourceDocumentNo(textHighlightService.highlight(product.getSourceDocumentNo(), highlightQuery));
			if (product.getIngredients() != null) {
				for (KampoIngredientView ingredient : product.getIngredients()) {
					ingredient.setHighlightedIngredientName(textHighlightService.highlight(ingredient.getIngredientName(), highlightQuery));
					ingredient.setHighlightedAmountUnit(textHighlightService.highlight(ingredient.getAmountUnit(), highlightQuery));
					ingredient.setHighlightedRawAmountText(textHighlightService.highlight(ingredient.getRawAmountText(), highlightQuery));
				}
			}
		}
		return products;
	}

	private List<Integer> buildPageNumbers(int currentPage, int totalPages) {
		if (totalPages <= 0) {
			return List.of();
		}
		Set<Integer> pages = new LinkedHashSet<>();
		pages.add(1);
		pages.add(totalPages);
		for (int delta = -2; delta <= 2; delta++) {
			int page = currentPage + delta;
			if (page >= 1 && page <= totalPages) {
				pages.add(page);
			}
		}
		return pages.stream().sorted().toList();
	}
}
