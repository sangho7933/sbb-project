package com.mysite.sbb.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

import com.mysite.sbb.ai.AiSearchRecommendation;
import com.mysite.sbb.ai.AiSearchResult;
import com.mysite.sbb.ai.AiSearchService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/ai")
public class AIController {

	private static final String EMPTY_MESSAGE = "일치하는 결과가 없습니다. 예: 뉴비, 마도, 자유, 장비, 보스";
	private static final List<String> EXAMPLES = List.of("뉴비 뭐부터 해야함", "마도성 스킬트리", "자유게시판", "장비 세팅", "보스 공략");

	private final AiSearchService aiSearchService;

	@GetMapping("/search")
	public String search(@RequestParam(value = "q", required = false) String query, Model model) {
		AiSearchResult searchResult = this.aiSearchService.search(query);
		if (searchResult.hasSingleRecommendation()) {
			AiSearchRecommendation recommendation = searchResult.primaryRecommendation();
			return "redirect:" + recommendation.url();
		}

		model.addAttribute("pageTitle", "검색 추천 - A2C");
		model.addAttribute("searchQuery", searchResult.originalQuery());
		model.addAttribute("searchResult", searchResult);
		model.addAttribute("emptyMessage", EMPTY_MESSAGE);
		model.addAttribute("examples", EXAMPLES);
		return "ai_search";
	}

	@PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
		String question = request != null ? request.question() : null;
		if (question == null || question.isBlank()) {
			return ResponseEntity.badRequest().body(new ChatResponse("message", "검색어를 입력해 주세요.", null, null));
		}

		AiSearchResult searchResult = this.aiSearchService.search(question);
		if (searchResult.hasSingleRecommendation()) {
			AiSearchRecommendation recommendation = searchResult.primaryRecommendation();
			return ResponseEntity.ok(new ChatResponse(
					"redirect",
					recommendation.title() + " 페이지로 이동합니다.",
					recommendation.url(),
					null));
		}

		return ResponseEntity.ok(new ChatResponse(
				"results",
				searchResult.hasRecommendations()
						? "입력하신 내용과 관련된 페이지를 추천합니다."
						: EMPTY_MESSAGE,
				null,
				buildSearchPageUrl(question)));
	}

	private String buildSearchPageUrl(String query) {
		return UriComponentsBuilder.fromPath("/ai/search")
				.queryParam("q", query == null ? "" : query.trim())
				.build()
				.encode()
				.toUriString();
	}

	public record ChatRequest(String question) {
	}

	public record ChatResponse(String action, String answer, String redirectUrl, String resultPageUrl) {
	}
}
