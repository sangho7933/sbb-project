package com.mysite.sbb.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysite.sbb.board.BoardCategory;
import com.mysite.sbb.service.GeminiRequestException;
import com.mysite.sbb.service.GeminiService;

class AiDraftServiceTest {

	private final GeminiService geminiService = mock(GeminiService.class);
	private final AiDraftService aiDraftService = new AiDraftService(this.geminiService, new ObjectMapper());

	@Test
	void generateBoardDraftAcceptsEnglishLabels() {
		when(this.geminiService.ask(anyString())).thenReturn("""
				Title: 빛나는 장화 구해봅니다
				Content: 혹시 거래 가능하신 분 있으면 옵션이랑 가격 같이 알려주세요.
				""");

		AiDraftService.AiDraft draft = this.aiDraftService.generateBoardDraft(BoardCategory.FREE_BOARD, "빛나는 장화");

		assertEquals("빛나는 장화 구해봅니다", draft.title());
		assertEquals("혹시 거래 가능하신 분 있으면 옵션이랑 가격 같이 알려주세요.", draft.content());
	}

	@Test
	void generateBoardDraftUsesFirstLineHeuristicWhenLabelsAreMissing() {
		when(this.geminiService.ask(anyString())).thenReturn("""
				빛나는 장화 매물 찾습니다
				최근에 써보신 분 있으면 가격대나 옵션 괜찮은지 같이 알려주세요.
				""");

		AiDraftService.AiDraft draft = this.aiDraftService.generateBoardDraft(BoardCategory.FREE_BOARD, "빛나는 장화");

		assertEquals("빛나는 장화 매물 찾습니다", draft.title());
		assertTrue(draft.content().contains("가격대나 옵션"));
	}

	@Test
	void generateBoardDraftFallsBackToRawContentWhenParsingFails() {
		when(this.geminiService.ask(anyString())).thenReturn(
				"빛나는 장화 쪽 보고 있는데 괜찮은 매물 있으면 댓글이나 쪽지 부탁드립니다.");

		AiDraftService.AiDraft draft = this.aiDraftService.generateBoardDraft(BoardCategory.GUILD_RECRUITMENT, "빛나는 장화");

		assertEquals("길드 모집 글", draft.title());
		assertEquals("빛나는 장화 쪽 보고 있는데 괜찮은 매물 있으면 댓글이나 쪽지 부탁드립니다.", draft.content());
	}

	@Test
	void generateBoardDraftFallsBackWhenGeminiCallFails() {
		when(this.geminiService.ask(anyString()))
				.thenThrow(new GeminiRequestException(HttpStatus.SERVICE_UNAVAILABLE, "timeout"));

		AiDraftService.AiDraft draft = this.aiDraftService.generateBoardDraft(BoardCategory.FREE_BOARD, "오늘 사기당했는데 글 써줘");

		assertEquals("자유게시판 글", draft.title());
		assertTrue(draft.content().contains("사용자 요청: 오늘 사기당했는데 글 써줘"));
		assertTrue(draft.content().contains("자유롭게 의견 남겨주세요."));
	}
}
