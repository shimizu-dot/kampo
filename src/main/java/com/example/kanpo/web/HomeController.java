package com.example.kanpo.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

	@GetMapping("/")
	public String root() {
		return "redirect:/kampo/import";
	}

	@GetMapping("/kampo")
	public String kampoRoot() {
		return "redirect:/kampo/import";
	}
}
