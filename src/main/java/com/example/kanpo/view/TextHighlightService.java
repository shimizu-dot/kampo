package com.example.kanpo.view;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component("textHighlighter")
public class TextHighlightService {

	public String highlight(String text, String query) {
		if (text == null) {
			return "";
		}
		if (query == null || query.isBlank()) {
			return HtmlUtils.htmlEscape(text);
		}

		String escapedQuery = HtmlUtils.htmlEscape(query);
		StringBuilder builder = new StringBuilder();
		int fromIndex = 0;
		int matchIndex;
		while ((matchIndex = text.indexOf(query, fromIndex)) >= 0) {
			builder.append(HtmlUtils.htmlEscape(text.substring(fromIndex, matchIndex)));
			builder.append("<mark class=\"match\">");
			builder.append(escapedQuery);
			builder.append("</mark>");
			fromIndex = matchIndex + query.length();
		}
		builder.append(HtmlUtils.htmlEscape(text.substring(fromIndex)));
		return builder.toString();
	}
}
