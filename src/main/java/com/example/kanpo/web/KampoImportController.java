package com.example.kanpo.web;

import com.example.kanpo.importer.KampoImportDraft;
import com.example.kanpo.importer.KampoPdfImportException;
import com.example.kanpo.importer.KampoPdfParser;
import com.example.kanpo.service.KampoImportService;
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
public class KampoImportController {

	private final KampoPdfParser pdfParser;
	private final KampoImportService importService;

	public KampoImportController(KampoPdfParser pdfParser, KampoImportService importService) {
		this.pdfParser = pdfParser;
		this.importService = importService;
	}

	@GetMapping
	public String uploadForm(Model model) {
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
		return "kampo/preview";
	}

	@PostMapping("/save")
	public String save(@ModelAttribute("draft") KampoImportDraft draft, SessionStatus sessionStatus, Model model) {
		long productId = importService.register(draft);
		sessionStatus.setComplete();
		model.addAttribute("productId", productId);
		model.addAttribute("salesName", draft.getSalesName());
		return "kampo/success";
	}

	@org.springframework.web.bind.annotation.ExceptionHandler(KampoPdfImportException.class)
	public String handleImportError(KampoPdfImportException exception, Model model) {
		model.addAttribute("errorMessage", exception.getMessage());
		model.addAttribute("draft", new KampoImportDraft());
		return "kampo/upload";
	}
}
