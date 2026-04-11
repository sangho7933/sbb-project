package com.mysite.sbb.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AiSearchServiceTest {

	private final AiSearchService aiSearchService = new AiSearchService();

	@Test
	void targetOnlyQueryDefaultsToViewPage() {
		AiSearchResult result = this.aiSearchService.search("자유게시판 가줘");

		assertTrue(result.hasSingleRecommendation());
		assertEquals("/boards/free", result.primaryRecommendation().url());
	}

	@Test
	void freeBoardWriteIntentIncludesDefaultAiPromptWhenNothingRemains() {
		AiSearchResult result = this.aiSearchService.search("자유게시판에 글써");

		assertTrue(result.hasSingleRecommendation());
		assertEquals("/boards/free/write?aiPrompt=%EC%9E%90%EC%9C%A0%EA%B2%8C%EC%8B%9C%ED%8C%90%EC%9A%A9%20%EA%B8%80%20%EC%B4%88%EC%95%88%20%EC%9E%91%EC%84%B1",
				result.primaryRecommendation().url());
	}

	@Test
	void freeBoardWriteIntentKeepsRemainingAiPromptText() {
		AiSearchResult result = this.aiSearchService.search("오늘 사기당했는데 자유게시판에 글써줘");

		assertTrue(result.hasSingleRecommendation());
		assertEquals("/boards/free/write?aiPrompt=%EC%98%A4%EB%8A%98%20%EC%82%AC%EA%B8%B0%EB%8B%B9%ED%96%88%EB%8A%94%EB%8D%B0",
				result.primaryRecommendation().url());
	}

	@Test
	void guildWriteIntentIncludesDefaultAiPromptWhenNothingRemains() {
		AiSearchResult result = this.aiSearchService.search("길드창에 모집글 적을래");

		assertTrue(result.hasSingleRecommendation());
		assertEquals("/boards/guild/write?aiPrompt=%EA%B8%B8%EB%93%9C%EA%B2%8C%EC%8B%9C%ED%8C%90%EC%9A%A9%20%EA%B8%80%20%EC%B4%88%EC%95%88%20%EC%9E%91%EC%84%B1",
				result.primaryRecommendation().url());
	}

	@Test
	void bossWriteIntentKeepsRemainingAiPromptText() {
		AiSearchResult result = this.aiSearchService.search("안트릭샤 처음 가는데 보스 공략 글 써줘");

		assertTrue(result.hasSingleRecommendation());
		assertEquals("/boards/boss/write?aiPrompt=%EC%95%88%ED%8A%B8%EB%A6%AD%EC%83%A4%20%EC%B2%98%EC%9D%8C%20%EA%B0%80%EB%8A%94%EB%8D%B0",
				result.primaryRecommendation().url());
	}

	@Test
	void writeIntentWinsEvenWhenViewWordsExistTogether() {
		AiSearchResult result = this.aiSearchService.search("하이라이트 글쓰러 가자");

		assertTrue(result.hasSingleRecommendation());
		assertEquals("/boards/highlight/write?aiPrompt=%ED%95%98%EC%9D%B4%EB%9D%BC%EC%9D%B4%ED%8A%B8%EC%9A%A9%20%EA%B8%80%20%EC%B4%88%EC%95%88%20%EC%9E%91%EC%84%B1",
				result.primaryRecommendation().url());
	}

	@Test
	void jobSpecificTargetBeatsCommonSkillTarget() {
		AiSearchResult result = this.aiSearchService.search("마도성 스킬트리 보여줘");

		assertTrue(result.hasSingleRecommendation());
		assertEquals("/skilltree/sorcerer", result.primaryRecommendation().url());
	}

	@Test
	void jobSpecificWriteIntentUsesExistingSkillWritePath() {
		AiSearchResult result = this.aiSearchService.search("검성 스킬 글 써줘");

		assertTrue(result.hasSingleRecommendation());
		assertEquals("/skilltree/write?job=gladiator&returnUrl=/skilltree/gladiator",
				result.primaryRecommendation().url());
	}

	@Test
	void tradeSearchUsesSingleKeyword() {
		AiSearchResult result = this.aiSearchService.search("가더 살래");

		assertTrue(result.hasSingleRecommendation());
		assertEquals("/trade/items?kw=%EA%B0%80%EB%8D%94", result.primaryRecommendation().url());
	}

	@Test
	void tradeSearchPreservesMultiWordKeyword() {
		AiSearchResult result = this.aiSearchService.search("빛나는 단검 볼래");

		assertTrue(result.hasSingleRecommendation());
		assertEquals("/trade/items?kw=%EB%B9%9B%EB%82%98%EB%8A%94%20%EB%8B%A8%EA%B2%80",
				result.primaryRecommendation().url());
	}

	@Test
	void tradeSearchCanUseItemKeywordWithoutExplicitTradeWord() {
		AiSearchResult result = this.aiSearchService.search("신발 추천해줘");

		assertTrue(result.hasSingleRecommendation());
		assertEquals("/trade/items?kw=%EC%9E%A5%ED%99%94", result.primaryRecommendation().url());
	}

	@Test
	void tradeSearchSupportsNewKeywordOnly() {
		AiSearchResult result = this.aiSearchService.search("빛나는");

		assertTrue(result.hasSingleRecommendation());
		assertEquals("/trade/items?kw=%EB%B9%9B%EB%82%98%EB%8A%94", result.primaryRecommendation().url());
	}

	@Test
	void tradeSearchMapsArmorAliasesToCanonicalKeywords() {
		assertEquals("/trade/items?kw=%EA%B0%81%EB%B0%98", this.aiSearchService.search("바지 볼래").primaryRecommendation().url());
		assertEquals("/trade/items?kw=%EA%B2%AC%EA%B0%91", this.aiSearchService.search("어깨 사고싶어").primaryRecommendation().url());
		assertEquals("/trade/items?kw=%ED%9D%89%EA%B0%91", this.aiSearchService.search("갑바 검색").primaryRecommendation().url());
		assertEquals("/trade/items?kw=%ED%88%AC%EA%B5%AC", this.aiSearchService.search("모자 추천해줘").primaryRecommendation().url());
	}

	@Test
	void tradeSearchMapsCandidateAliasesToCanonicalKeywords() {
		assertEquals("/trade/items?kw=%EA%B7%80%EA%B1%B8%EC%9D%B4", this.aiSearchService.search("귀 볼래").primaryRecommendation().url());
		assertEquals("/trade/items?kw=%EB%AA%A9%EA%B1%B8%EC%9D%B4", this.aiSearchService.search("목 볼래").primaryRecommendation().url());
		assertEquals("/trade/items?kw=%EB%B0%98%EC%A7%80", this.aiSearchService.search("링 살래").primaryRecommendation().url());
		assertEquals("/trade/items?kw=%EB%B0%A9%ED%8C%A8", this.aiSearchService.search("실드 찾고싶어").primaryRecommendation().url());
		assertEquals("/trade/items?kw=%EB%B2%95%EC%84%9C", this.aiSearchService.search("책 살까").primaryRecommendation().url());
		assertEquals("/trade/items?kw=%EB%B3%B4%EC%A3%BC", this.aiSearchService.search("오브 볼래").primaryRecommendation().url());
		assertEquals("/trade/items?kw=%EC%A7%80%ED%8C%A1%EC%9D%B4", this.aiSearchService.search("스태프 추천").primaryRecommendation().url());
		assertEquals("/trade/items?kw=%EB%8B%A8%EA%B2%80", this.aiSearchService.search("단도 검색").primaryRecommendation().url());
	}

	@Test
	void genericGearRecommendationStillReturnsMultipleResults() {
		AiSearchResult result = this.aiSearchService.search("장비 세팅 추천");

		assertTrue(result.hasRecommendations());
		assertTrue(result.recommendations().size() > 1);
		assertTrue(result.recommendations().stream().anyMatch(item -> "/simulator".equals(item.url())));
	}

	@Test
	void unknownKeywordReturnsEmptyResult() {
		AiSearchResult result = this.aiSearchService.search("외계어테스트 커맨드");

		assertFalse(result.hasRecommendations());
		assertFalse(result.hasMatchedKeywords());
	}
}
