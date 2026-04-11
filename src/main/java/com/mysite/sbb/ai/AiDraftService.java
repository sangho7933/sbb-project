package com.mysite.sbb.ai;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysite.sbb.board.BoardCategory;
import com.mysite.sbb.service.GeminiRequestException;
import com.mysite.sbb.service.GeminiService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AiDraftService {

	private static final Logger log = LoggerFactory.getLogger(AiDraftService.class);
	private static final Pattern TITLE_LABEL_PATTERN = Pattern.compile("^(?:제목|title|subject)\\s*[:：\\-]\\s*(.+)$",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern CONTENT_LABEL_PATTERN = Pattern.compile("^(?:본문|내용|content|body)\\s*[:：\\-]\\s*(.*)$",
			Pattern.CASE_INSENSITIVE);

	private final GeminiService geminiService;
	private final ObjectMapper objectMapper;

	public AiDraft generateBoardDraft(BoardCategory category, String prompt) {
		BoardDraftProfile profile = resolveDraftProfile(category);
		String normalizedPrompt = normalizePrompt(prompt);
		String response;
		try {
			response = this.geminiService.ask(buildBoardPrompt(profile, normalizedPrompt));
		} catch (GeminiRequestException exception) {
			logGeminiFailure(category, normalizedPrompt, exception);
			AiDraft fallbackDraft = buildPromptFallbackDraft(profile, normalizedPrompt);
			log.warn("AI draft fallback_used board={} reason=api_call_failure promptPreview={}",
					category.getCode(), preview(normalizedPrompt));
			return fallbackDraft;
		}

		AiDraft draft = parseDraft(category, profile, normalizedPrompt, response);
		if (draft == null) {
			log.warn("AI draft content extraction failed board={} promptPreview={}", category.getCode(),
					preview(normalizedPrompt));
			throw new IllegalStateException("AI 초안을 해석하지 못했습니다.");
		}
		return draft;
	}

	public AiDraft generateFreeBoardDraft(String prompt) {
		return generateBoardDraft(BoardCategory.FREE_BOARD, prompt);
	}

	private String normalizePrompt(String prompt) {
		if (!StringUtils.hasText(prompt)) {
			throw new IllegalArgumentException("요청 내용을 입력해 주세요.");
		}
		return prompt.trim();
	}

	private String buildBoardPrompt(BoardDraftProfile profile, String prompt) {
		return """
				너는 A2C %s 글쓰기 도우미다.
				사용자 요청을 바탕으로 %s에 어울리는 제목 1개와 본문 1개를 한국어로 작성해라.
				
				규칙:
				- %s
				- 제목은 짧고 명확하게 쓴다.
				- 본문은 3~5문장 정도로 너무 길지 않게 작성한다.
				- 커뮤니티 게시글처럼 자연스럽게 읽히도록 쓴다.
				- %s
				- 과한 이모지, 해시태그, 번호 목록은 넣지 않는다.
				- 결과는 반드시 JSON 하나로만 출력한다.
				
				JSON 형식:
				{"title":"제목","content":"본문"}
				
				사용자 요청:
				%s
				""".formatted(
				profile.assistantName(),
				profile.boardLabel(),
				profile.toneGuide(),
				profile.extraGuide(),
				prompt);
	}

	private BoardDraftProfile resolveDraftProfile(BoardCategory category) {
		if (category == null) {
			throw new IllegalArgumentException("지원하지 않는 게시판입니다.");
		}

		return switch (category) {
			case FREE_BOARD -> new BoardDraftProfile(
					"자유게시판",
					"자유게시판",
					"딱딱한 공략문보다는 편하게 읽히는 커뮤니티 글 느낌으로 작성한다.",
					"질문, 경험담, 하소연, 정보 공유처럼 자연스러운 흐름으로 쓴다.",
					"자유게시판 글");
			case GUILD_RECRUITMENT -> new BoardDraftProfile(
					"길드게시판",
					"길드 모집 게시판",
					"길드 모집글이나 길드 소개글처럼 편하게 읽히는 커뮤니티 톤으로 작성한다.",
					"길드 분위기, 활동 시간, 모집 대상 같은 정보가 있으면 자연스럽게 녹여낸다.",
					"길드 모집 글");
			case BOSS_GUIDE -> new BoardDraftProfile(
					"보스게시판",
					"보스 공략 게시판",
					"보스 공략이나 파티 경험 공유 글처럼 핵심이 바로 보이게 자연스럽게 작성한다.",
					"패턴, 준비물, 주의점이 있으면 너무 길지 않게 짧게 녹여낸다.",
					"보스 공략 글");
			case HIGHLIGHT -> new BoardDraftProfile(
					"하이라이트 게시판",
					"하이라이트 게시판",
					"인상적인 장면을 공유하는 후기 글처럼 생생하고 가볍게 작성한다.",
					"긴 설명보다 어떤 장면이 포인트였는지 바로 떠오르게 쓴다.",
					"하이라이트 글");
		};
	}

	private AiDraft parseDraft(BoardCategory category, BoardDraftProfile profile, String prompt, String response) {
		String cleanedResponse = stripCodeFence(response);
		List<String> failureReasons = new ArrayList<>();

		DraftParseAttempt jsonAttempt = parseJsonDraft(cleanedResponse);
		if (jsonAttempt.success()) {
			return jsonAttempt.draft();
		}
		failureReasons.add(jsonAttempt.failureReason());

		DraftParseAttempt labeledAttempt = parseLabeledDraft(cleanedResponse);
		if (labeledAttempt.success()) {
			return labeledAttempt.draft();
		}
		failureReasons.add(labeledAttempt.failureReason());

		DraftParseAttempt heuristicAttempt = parseHeuristicDraft(cleanedResponse);
		if (heuristicAttempt.success()) {
			return heuristicAttempt.draft();
		}
		failureReasons.add(heuristicAttempt.failureReason());

		AiDraft fallbackDraft = buildRawFallbackDraft(profile, cleanedResponse);
		if (fallbackDraft != null) {
			log.warn("AI draft parsing fallback used board={} reasons={} promptPreview={} responsePreview={}",
					category.getCode(), failureReasons, preview(prompt), preview(cleanedResponse));
			return fallbackDraft;
		}

		log.warn("AI draft parsing failed board={} reasons={} promptPreview={} responsePreview={}",
				category.getCode(), failureReasons, preview(prompt), preview(cleanedResponse));
		return null;
	}

	private String stripCodeFence(String response) {
		if (!StringUtils.hasText(response)) {
			return "";
		}

		String trimmed = response.trim();
		if (!trimmed.startsWith("```")) {
			return trimmed;
		}

		int firstLineBreak = trimmed.indexOf('\n');
		int lastFence = trimmed.lastIndexOf("```");
		if (firstLineBreak < 0 || lastFence <= firstLineBreak) {
			return trimmed;
		}
		return trimmed.substring(firstLineBreak + 1, lastFence).trim();
	}

	private DraftParseAttempt parseJsonDraft(String response) {
		String jsonCandidate = extractJsonCandidate(response);
		if (!StringUtils.hasText(jsonCandidate)) {
			return DraftParseAttempt.failure("json_candidate_missing");
		}

		try {
			JsonNode root = this.objectMapper.readTree(jsonCandidate);
			String title = firstNonBlank(
					root.path("title").asText(null),
					root.path("subject").asText(null),
					root.path("headline").asText(null),
					root.path("제목").asText(null));
			String content = firstNonBlank(
					root.path("content").asText(null),
					root.path("body").asText(null),
					root.path("본문").asText(null),
					root.path("내용").asText(null));
			AiDraft draft = buildDraft(title, content);
			if (draft != null) {
				return DraftParseAttempt.success(draft);
			}
			return DraftParseAttempt.failure("json_title_or_content_missing");
		} catch (Exception exception) {
			return DraftParseAttempt.failure("json_parse_error:" + exception.getClass().getSimpleName());
		}
	}

	private String extractJsonCandidate(String response) {
		int start = response.indexOf('{');
		int end = response.lastIndexOf('}');
		if (start < 0 || end <= start) {
			return null;
		}
		return response.substring(start, end + 1);
	}

	private DraftParseAttempt parseLabeledDraft(String response) {
		if (!StringUtils.hasText(response)) {
			return DraftParseAttempt.failure("labeled_response_empty");
		}

		String title = null;
		StringBuilder contentBuilder = new StringBuilder();
		boolean contentSection = false;
		boolean matchedAnyLabel = false;

		for (String line : response.split("\\R")) {
			String trimmed = line.trim();
			if (!StringUtils.hasText(trimmed)) {
				continue;
			}

			Matcher titleMatcher = TITLE_LABEL_PATTERN.matcher(trimmed);
			if (titleMatcher.matches()) {
				title = titleMatcher.group(1).trim();
				contentSection = false;
				matchedAnyLabel = true;
				continue;
			}

			Matcher contentMatcher = CONTENT_LABEL_PATTERN.matcher(trimmed);
			if (contentMatcher.matches()) {
				String firstContentLine = contentMatcher.group(1).trim();
				if (StringUtils.hasText(firstContentLine)) {
					if (contentBuilder.length() > 0) {
						contentBuilder.append(System.lineSeparator());
					}
					contentBuilder.append(firstContentLine);
				}
				contentSection = true;
				matchedAnyLabel = true;
				continue;
			}

			if (contentSection) {
				if (contentBuilder.length() > 0) {
					contentBuilder.append(System.lineSeparator());
				}
				contentBuilder.append(trimmed);
			}
		}

		AiDraft labeledDraft = buildDraft(title, contentBuilder.toString());
		if (labeledDraft != null) {
			return DraftParseAttempt.success(labeledDraft);
		}

		if (matchedAnyLabel) {
			return DraftParseAttempt.failure("labeled_title_or_content_missing");
		}

		String[] paragraphs = response.split("(?:\\R\\s*){2,}", 2);
		if (paragraphs.length == 2) {
			AiDraft paragraphDraft = buildDraft(paragraphs[0], paragraphs[1]);
			if (paragraphDraft != null) {
				return DraftParseAttempt.success(paragraphDraft);
			}
			return DraftParseAttempt.failure("paragraph_title_or_content_missing");
		}

		return DraftParseAttempt.failure("labels_not_found");
	}

	private DraftParseAttempt parseHeuristicDraft(String response) {
		if (!StringUtils.hasText(response)) {
			return DraftParseAttempt.failure("heuristic_response_empty");
		}

		List<String> lines = Arrays.stream(response.split("\\R"))
				.map(String::trim)
				.filter(StringUtils::hasText)
				.toList();
		if (lines.size() < 2) {
			return DraftParseAttempt.failure("heuristic_not_applicable");
		}

		String title = lines.get(0);
		String content = String.join(System.lineSeparator(), lines.subList(1, lines.size()));
		AiDraft heuristicDraft = buildDraft(title, content);
		if (heuristicDraft != null) {
			return DraftParseAttempt.success(heuristicDraft);
		}
		return DraftParseAttempt.failure("heuristic_title_or_content_missing");
	}

	private AiDraft buildRawFallbackDraft(BoardDraftProfile profile, String response) {
		String normalizedContent = normalizeContent(response);
		if (!StringUtils.hasText(normalizedContent)) {
			return null;
		}
		return new AiDraft(profile.fallbackTitle(), normalizedContent);
	}

	private AiDraft buildPromptFallbackDraft(BoardDraftProfile profile, String prompt) {
		String fallbackContent = """
				오늘 이런 일이 있어서 글을 남깁니다.
				사용자 요청: %s
				자유롭게 의견 남겨주세요.
				""".formatted(prompt);
		return new AiDraft(profile.fallbackTitle(), normalizeContent(fallbackContent));
	}

	private AiDraft buildDraft(String title, String content) {
		String normalizedTitle = normalizeTitle(title);
		String normalizedContent = normalizeContent(content);
		if (!StringUtils.hasText(normalizedTitle) || !StringUtils.hasText(normalizedContent)) {
			return null;
		}
		return new AiDraft(normalizedTitle, normalizedContent);
	}

	private String normalizeTitle(String title) {
		if (!StringUtils.hasText(title)) {
			return "";
		}

		String normalized = stripWrappingQuotes(title).replaceAll("\\s+", " ").trim();
		if (normalized.length() > 200) {
			normalized = normalized.substring(0, 200).trim();
		}
		return normalized;
	}

	private String normalizeContent(String content) {
		if (!StringUtils.hasText(content)) {
			return "";
		}

		String normalized = stripWrappingQuotes(content)
				.replace("\r\n", "\n")
				.replace('\r', '\n')
				.trim();
		normalized = Arrays.stream(normalized.split("\n"))
				.map(String::stripTrailing)
				.collect(Collectors.joining("\n"))
				.replaceAll("\n{3,}", "\n\n")
				.trim();
		return normalized;
	}

	private String stripWrappingQuotes(String value) {
		if (!StringUtils.hasText(value)) {
			return "";
		}
		return value.replaceAll("^[\"'“”]+|[\"'“”]+$", "").trim();
	}

	private String firstNonBlank(String... values) {
		for (String value : values) {
			if (StringUtils.hasText(value)) {
				return value;
			}
		}
		return null;
	}

	private void logGeminiFailure(BoardCategory category, String prompt, GeminiRequestException exception) {
		String failureType = hasCause(exception, SocketTimeoutException.class) ? "timeout" : "api_call_failure";
		log.warn("AI draft {} board={} status={} promptPreview={} message={}",
				failureType,
				category.getCode(),
				exception.getStatus(),
				preview(prompt),
				exception.getMessage(),
				exception);
	}

	private boolean hasCause(Throwable throwable, Class<? extends Throwable> targetType) {
		Throwable current = throwable;
		while (current != null) {
			if (targetType.isInstance(current)) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	private String preview(String value) {
		if (!StringUtils.hasText(value)) {
			return "";
		}
		String normalized = value.replaceAll("\\s+", " ").trim();
		return normalized.length() <= 140 ? normalized : normalized.substring(0, 140) + "...";
	}

	public record AiDraft(String title, String content) {
	}

	private record BoardDraftProfile(String assistantName, String boardLabel, String toneGuide, String extraGuide,
			String fallbackTitle) {
	}

	private record DraftParseAttempt(AiDraft draft, String failureReason) {

		private static DraftParseAttempt success(AiDraft draft) {
			return new DraftParseAttempt(draft, null);
		}

		private static DraftParseAttempt failure(String failureReason) {
			return new DraftParseAttempt(null, failureReason);
		}

		private boolean success() {
			return this.draft != null;
		}
	}
}
