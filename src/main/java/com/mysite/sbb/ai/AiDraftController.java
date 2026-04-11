package com.mysite.sbb.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mysite.sbb.board.BoardCategory;
import com.mysite.sbb.service.GeminiRequestException;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ai/boards")
public class AiDraftController {

	private static final Logger log = LoggerFactory.getLogger(AiDraftController.class);
	private static final String DRAFT_FAILURE_MESSAGE = "AI 초안 생성에 실패했습니다. 잠시 후 다시 시도해 주세요.";

	private final AiDraftService aiDraftService;

	@PreAuthorize("isAuthenticated()")
	@PostMapping(value = "/{categoryCode}/draft", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<DraftResponse> createDraft(@PathVariable("categoryCode") String categoryCode,
			@RequestBody DraftRequest request) {
		String prompt = request != null ? request.prompt() : null;
		if (!StringUtils.hasText(prompt)) {
			return ResponseEntity.badRequest().body(DraftResponse.failure("요청 내용을 입력해 주세요."));
		}

		BoardCategory category;
		try {
			category = BoardCategory.fromCode(categoryCode);
		} catch (IllegalArgumentException exception) {
			return ResponseEntity.badRequest().body(DraftResponse.failure("지원하지 않는 게시판입니다."));
		}

		try {
			AiDraftService.AiDraft draft = this.aiDraftService.generateBoardDraft(category, prompt);
			return ResponseEntity.ok(DraftResponse.success(draft.title(), draft.content()));
		} catch (IllegalArgumentException exception) {
			return ResponseEntity.badRequest().body(DraftResponse.failure(exception.getMessage()));
		} catch (GeminiRequestException exception) {
			log.warn("{} board AI draft generation failed", category.getCode(), exception);
			return ResponseEntity.status(exception.getStatus()).body(DraftResponse.failure(DRAFT_FAILURE_MESSAGE));
		} catch (Exception exception) {
			log.error("Unexpected error while generating {} board AI draft", category.getCode(), exception);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(DraftResponse.failure(DRAFT_FAILURE_MESSAGE));
		}
	}

	public record DraftRequest(String prompt) {
	}

	public record DraftResponse(String title, String content, String message) {

		private static DraftResponse success(String title, String content) {
			return new DraftResponse(title, content, null);
		}

		private static DraftResponse failure(String message) {
			return new DraftResponse(null, null, message);
		}
	}
}
