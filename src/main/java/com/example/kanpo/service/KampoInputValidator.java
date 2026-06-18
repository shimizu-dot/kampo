package com.example.kanpo.service;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class KampoInputValidator {

	private static final int SALES_NAME_MAX_LENGTH = 100;
	private static final int READING_MAX_LENGTH = 100;
	private static final Pattern FULL_WIDTH_KATAKANA_PATTERN = Pattern.compile("^[ァ-ヶー]*$");

	public void validateSalesName(String salesName) {
		if (!StringUtils.hasText(salesName)) {
			throw new IllegalArgumentException("販売名を入力してください。");
		}
		if (salesName.length() > SALES_NAME_MAX_LENGTH) {
			throw new IllegalArgumentException("販売名は100文字以内で入力してください。");
		}
	}

	public void validateReading(String reading) {
		if (!StringUtils.hasText(reading)) {
			return;
		}
		if (reading.length() > READING_MAX_LENGTH) {
			throw new IllegalArgumentException("読み方は100文字以内で入力してください。");
		}
		if (!FULL_WIDTH_KATAKANA_PATTERN.matcher(reading).matches()) {
			throw new IllegalArgumentException("読み方は全角カタカナで入力してください。");
		}
	}
}
