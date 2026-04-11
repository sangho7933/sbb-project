package com.mysite.sbb.ai;

import java.util.List;

public record AiSearchResult(
		String originalQuery,
		String normalizedQuery,
		String priorityLabel,
		List<String> matchedKeywords,
		List<AiSearchRecommendation> recommendations) {

	public AiSearchResult {
		originalQuery = originalQuery == null ? "" : originalQuery;
		normalizedQuery = normalizedQuery == null ? "" : normalizedQuery;
		priorityLabel = priorityLabel == null ? "" : priorityLabel;
		matchedKeywords = List.copyOf(matchedKeywords == null ? List.of() : matchedKeywords);
		recommendations = List.copyOf(recommendations == null ? List.of() : recommendations);
	}

	public boolean hasRecommendations() {
		return !recommendations.isEmpty();
	}

	public boolean hasSingleRecommendation() {
		return recommendations.size() == 1;
	}

	public boolean hasMatchedKeywords() {
		return !matchedKeywords.isEmpty();
	}

	public AiSearchRecommendation primaryRecommendation() {
		return hasRecommendations() ? recommendations.get(0) : null;
	}

	public String matchedKeywordSummary() {
		return String.join(", ", matchedKeywords);
	}
}
