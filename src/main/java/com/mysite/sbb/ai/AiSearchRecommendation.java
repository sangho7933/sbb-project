package com.mysite.sbb.ai;

public record AiSearchRecommendation(
		String categoryLabel,
		String title,
		String description,
		String url,
		boolean loginRequired) {
}
