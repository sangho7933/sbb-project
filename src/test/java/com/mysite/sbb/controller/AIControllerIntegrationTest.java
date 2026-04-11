package com.mysite.sbb.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
		"spring.datasource.url=jdbc:h2:mem:ai-search-testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
		"spring.datasource.driverClassName=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"app.upload.dir=./build/test-uploads"
})
class AIControllerIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void searchRedirectsForSingleGuideResult() throws Exception {
		this.mockMvc.perform(get("/ai/search").param("q", "뉴비 가이드 보여줘"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/guide?step=1"));
	}

	@Test
	void searchPageRendersRecommendationListForAmbiguousQuery() throws Exception {
		this.mockMvc.perform(get("/ai/search").param("q", "장비 세팅 추천"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("href=\"/simulator\"")))
				.andExpect(content().string(containsString("href=\"/guide?step=5\"")));
	}

	@Test
	void chatApiReturnsRedirectActionForDirectMatch() throws Exception {
		this.mockMvc.perform(post("/ai/chat")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"question\":\"시세 좀 보고싶다\"}"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("\"action\":\"redirect\"")))
				.andExpect(content().string(containsString("\"redirectUrl\":\"/trade/items\"")));
	}

	@Test
	void searchRedirectsToFreeWritePageForWriteIntent() throws Exception {
		this.mockMvc.perform(get("/ai/search").param("q", "자유게시판에 글 써줘"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/boards/free/write?aiPrompt=%EC%9E%90%EC%9C%A0%EA%B2%8C%EC%8B%9C%ED%8C%90%EC%9A%A9%20%EA%B8%80%20%EC%B4%88%EC%95%88%20%EC%9E%91%EC%84%B1"));
	}

	@Test
	void searchRedirectsToSkillWritePageForJobWriteIntent() throws Exception {
		this.mockMvc.perform(get("/ai/search").param("q", "마도성 스킬 글 써줘"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/skilltree/write?job=sorcerer&returnUrl=/skilltree/sorcerer"));
	}

	@Test
	void chatApiReturnsFreeWriteRedirectWithAiPrompt() throws Exception {
		this.mockMvc.perform(post("/ai/chat")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"question\":\"오늘 사기당했는데 자유게시판에 글써줘\"}"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("\"action\":\"redirect\"")))
				.andExpect(content().string(containsString("/boards/free/write?aiPrompt=%EC%98%A4%EB%8A%98%20%EC%82%AC%EA%B8%B0%EB%8B%B9%ED%96%88%EB%8A%94%EB%8D%B0")));
	}

	@Test
	void searchRedirectsToGuildWritePageWithAiPrompt() throws Exception {
		this.mockMvc.perform(get("/ai/search").param("q", "길드창에 모집글 적을래"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/boards/guild/write?aiPrompt=%EA%B8%B8%EB%93%9C%EA%B2%8C%EC%8B%9C%ED%8C%90%EC%9A%A9%20%EA%B8%80%20%EC%B4%88%EC%95%88%20%EC%9E%91%EC%84%B1"));
	}

	@Test
	void chatApiReturnsBossWriteRedirectWithAiPrompt() throws Exception {
		this.mockMvc.perform(post("/ai/chat")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"question\":\"안트릭샤 처음 가는데 보스 공략 글 써줘\"}"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("\"action\":\"redirect\"")))
				.andExpect(content().string(containsString("/boards/boss/write?aiPrompt=%EC%95%88%ED%8A%B8%EB%A6%AD%EC%83%A4%20%EC%B2%98%EC%9D%8C%20%EA%B0%80%EB%8A%94%EB%8D%B0")));
	}

	@Test
	void searchRedirectsToTradeBoardWithExtractedKeyword() throws Exception {
		this.mockMvc.perform(get("/ai/search").param("q", "빛나는 활 살래"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/trade/items?kw=%EB%B9%9B%EB%82%98%EB%8A%94%20%ED%99%9C"));
	}

	@Test
	void chatApiReturnsTradeSearchRedirectWithExtractedKeyword() throws Exception {
		this.mockMvc.perform(post("/ai/chat")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"question\":\"견갑 사고싶어\"}"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("\"action\":\"redirect\"")))
				.andExpect(content().string(containsString("/trade/items?kw=%EA%B2%AC%EA%B0%91")));
	}

	@Test
	void searchRedirectsTradeAliasToCanonicalKeyword() throws Exception {
		this.mockMvc.perform(get("/ai/search").param("q", "모자 검색"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/trade/items?kw=%ED%88%AC%EA%B5%AC"));
	}
}
