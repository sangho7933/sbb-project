package com.mysite.sbb;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.mysite.sbb.admin.AdminDashboardService;
import com.mysite.sbb.admin.AdminDashboardStats;
import com.mysite.sbb.board.BoardCategory;
import com.mysite.sbb.board.BoardPost;
import com.mysite.sbb.board.BoardPostService;
import com.mysite.sbb.comment.BoardComment;
import com.mysite.sbb.comment.BoardCommentService;
import com.mysite.sbb.mypage.entity.Item;
import com.mysite.sbb.mypage.repository.ItemRepository;
import com.mysite.sbb.skilltree.SkillTreeJob;
import com.mysite.sbb.skilltree.SkillTreePost;
import com.mysite.sbb.skilltree.SkillTreePostService;
import com.mysite.sbb.skilltree.comment.SkillTreeComment;
import com.mysite.sbb.skilltree.comment.SkillTreeCommentService;
import com.mysite.sbb.trade.entity.TradeCatalogItem;
import com.mysite.sbb.trade.entity.TradeItem;
import com.mysite.sbb.trade.repository.TradeCatalogItemRepository;
import com.mysite.sbb.trade.repository.TradeItemRepository;
import com.mysite.sbb.trade.repository.TradeTransactionRepository;
import com.mysite.sbb.trade.service.TradeCatalogService;
import com.mysite.sbb.user.SiteUser;
import com.mysite.sbb.user.UserLoginLogService;
import com.mysite.sbb.user.UserService;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
		"spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
		"spring.datasource.driverClassName=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"app.upload.dir=./build/test-uploads"
})
class A2CApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserService userService;

	@Autowired
	private UserLoginLogService userLoginLogService;

	@Autowired
	private TradeCatalogService tradeCatalogService;

	@Autowired
	private TradeCatalogItemRepository tradeCatalogItemRepository;

	@Autowired
	private TradeItemRepository tradeItemRepository;

	@Autowired
	private TradeTransactionRepository tradeTransactionRepository;

	@Autowired
	private ItemRepository itemRepository;

	@Autowired
	private SkillTreePostService skillTreePostService;

	@Autowired
	private SkillTreeCommentService skillTreeCommentService;

	@Autowired
	private BoardPostService boardPostService;

	@Autowired
	private BoardCommentService boardCommentService;

	@Autowired
	private AdminDashboardService adminDashboardService;

	@Test
	void loginFlowWorksAndProtectedPagesOpen() throws Exception {
		String username = "tester" + System.nanoTime();
		this.userService.create(username, username + "@example.com", "secret123");

		MockHttpSession session = login(username, "secret123");

		this.mockMvc.perform(get("/mypage").session(session))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("/simulator")))
				.andExpect(content().string(org.hamcrest.Matchers.not(containsString("simulatorPage"))));

		this.mockMvc.perform(get("/simulator").session(session))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("simulatorPage")))
				.andExpect(content().string(containsString("/api/simulator/items")));
	}

	@Test
	@Transactional
	void todayLoginCountUsesDistinctUsersWithinTodayOnly() {
		long baselineCount = this.userLoginLogService.countTodayDistinctUsers();
		String firstUsername = "visitfirst" + System.nanoTime();
		String secondUsername = "visitsecond" + System.nanoTime();

		SiteUser firstUser = this.userService.create(firstUsername, firstUsername + "@example.com", "secret123");
		SiteUser secondUser = this.userService.create(secondUsername, secondUsername + "@example.com", "secret123");
		LocalDateTime startOfToday = LocalDate.now().atStartOfDay();

		this.userLoginLogService.saveLog(firstUser, startOfToday.plusHours(1));
		this.userLoginLogService.saveLog(firstUser, startOfToday.plusHours(2));
		this.userLoginLogService.saveLog(secondUser, startOfToday.plusHours(3));
		this.userLoginLogService.saveLog(secondUser, startOfToday.minusMinutes(1));

		assertEquals(baselineCount + 2, this.userLoginLogService.countTodayDistinctUsers());
	}

	@Test
	@Transactional
	void adminDashboardLoginTimeStatsSplitTodayIntoFourTimeBands() {
		List<Long> baselineCounts = this.adminDashboardService.getDashboardStats()
				.loginTimeStats()
				.stream()
				.map(AdminDashboardStats.ChartStat::value)
				.toList();
		String dawnUsername = "dawn" + System.nanoTime();
		String morningUsername = "morning" + System.nanoTime();
		String afternoonUsername = "afternoon" + System.nanoTime();
		String eveningUsername = "evening" + System.nanoTime();

		SiteUser dawnUser = this.userService.create(dawnUsername, dawnUsername + "@example.com", "secret123");
		SiteUser morningUser = this.userService.create(morningUsername, morningUsername + "@example.com", "secret123");
		SiteUser afternoonUser = this.userService.create(afternoonUsername, afternoonUsername + "@example.com", "secret123");
		SiteUser eveningUser = this.userService.create(eveningUsername, eveningUsername + "@example.com", "secret123");
		LocalDateTime startOfToday = LocalDate.now().atStartOfDay();

		this.userLoginLogService.saveLog(dawnUser, startOfToday.plusHours(1));
		this.userLoginLogService.saveLog(dawnUser, startOfToday.plusHours(5).plusMinutes(59));
		this.userLoginLogService.saveLog(morningUser, startOfToday.plusHours(6));
		this.userLoginLogService.saveLog(morningUser, startOfToday.plusHours(11).plusMinutes(59));
		this.userLoginLogService.saveLog(afternoonUser, startOfToday.plusHours(12));
		this.userLoginLogService.saveLog(afternoonUser, startOfToday.plusHours(17).plusMinutes(59));
		this.userLoginLogService.saveLog(eveningUser, startOfToday.plusHours(18));
		this.userLoginLogService.saveLog(eveningUser, startOfToday.plusHours(23).plusMinutes(59));
		this.userLoginLogService.saveLog(morningUser, startOfToday.minusMinutes(1));
		this.userLoginLogService.saveLog(eveningUser, startOfToday.plusDays(1));

		AdminDashboardStats dashboardStats = this.adminDashboardService.getDashboardStats();

		assertEquals(
				List.of("\uC0C8\uBCBD", "\uC624\uC804", "\uC624\uD6C4", "\uC800\uB141"),
				dashboardStats.loginTimeStats().stream().map(AdminDashboardStats.ChartStat::label).toList());
		assertEquals(
				List.of(
						baselineCounts.get(0) + 1,
						baselineCounts.get(1) + 1,
						baselineCounts.get(2) + 1,
						baselineCounts.get(3) + 1),
				dashboardStats.loginTimeStats().stream().map(AdminDashboardStats.ChartStat::value).toList());
	}

	@Test
	void headerShowsTodayLoginCountNextToGold() throws Exception {
		long baselineCount = this.userLoginLogService.countTodayDistinctUsers();
		String firstUsername = "headerfirst" + System.nanoTime();
		String secondUsername = "headersecond" + System.nanoTime();

		this.userService.create(firstUsername, firstUsername + "@example.com", "secret123");
		this.userService.create(secondUsername, secondUsername + "@example.com", "secret123");

		MockHttpSession session = login(firstUsername, "secret123");
		login(firstUsername, "secret123");
		login(secondUsername, "secret123");

		this.mockMvc.perform(get("/").session(session))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("오늘 접속자 " + (baselineCount + 2) + "명")));
	}

	@Test
	void adminMenuShowsOnlyForAdminAndAdminRouteIsProtected() throws Exception {
		this.userService.ensureUser("admin", "admin@example.com", "1234");
		String username = "member" + System.nanoTime();
		this.userService.create(username, username + "@example.com", "secret123");

		MockHttpSession adminSession = login("admin", "1234");
		MockHttpSession memberSession = login(username, "secret123");

		this.mockMvc.perform(get("/").session(adminSession))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("action=\"/admin/mode/enable\"")))
				.andExpect(content().string(org.hamcrest.Matchers.not(containsString("href=\"/admin\""))));

		this.mockMvc.perform(get("/").session(memberSession))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.not(containsString("/admin/mode/enable"))))
				.andExpect(content().string(org.hamcrest.Matchers.not(containsString("href=\"/admin\""))));

		this.mockMvc.perform(get("/admin"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrlPattern("**/user/login"));

		this.mockMvc.perform(get("/admin").session(memberSession))
				.andExpect(status().isForbidden());

		this.mockMvc.perform(get("/admin").session(adminSession))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("/admin/mode/enable")))
				.andExpect(content().string(org.hamcrest.Matchers.not(containsString("/admin/users/"))));

		enableAdminMode(adminSession);

		this.mockMvc.perform(get("/").session(adminSession))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("href=\"/admin\"")))
				.andExpect(content().string(org.hamcrest.Matchers.not(containsString("action=\"/admin/mode/enable\""))));

		this.mockMvc.perform(get("/admin").session(adminSession))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("/admin/mode/disable")))
				.andExpect(content().string(containsString("/admin/users/")));
	}

	@Test
	void guideRouteRendersOnlySelectedStepAndNavPointsToStepOne() throws Exception {
		String homeHtml = this.mockMvc.perform(get("/"))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();
		assertTrue(homeHtml.contains("id=\"guideNavLink\""));
		assertTrue(homeHtml.contains("href=\"/guide?step=1\""));
		assertTrue(homeHtml.matches("(?s).*href=\"/skilltree\".*?</a>\\s*</li>\\s*<li class=\"nav-shortcut-item\">\\s*<a class=\"nav-shortcut\" id=\"guideNavLink\".*"));

		String guideHtml = this.mockMvc.perform(get("/guide").param("step", "1"))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();
		assertTrue(countOccurrences(guideHtml, "guide-step-shell") == 1);
		assertTrue(guideHtml.contains("data-initial-step=\"1\""));
		assertTrue(!guideHtml.contains("거래소 이용 가이드"));
		assertTrue(!guideHtml.contains("전투력 상승 루트"));
	}

	@Test
	@Transactional
	void adminCanOpenUserDetailPageWithAuthoredContent() throws Exception {
		this.userService.ensureUser("admin", "admin@example.com", "1234");
		String username = "detailuser" + System.nanoTime();
		String memberName = "memberdetail" + System.nanoTime();
		this.userService.create(username, username + "@example.com", "secret123");
		this.userService.create(memberName, memberName + "@example.com", "secret123");

		SiteUser targetUser = this.userService.getUser(username);
		BoardPost boardPost = this.boardPostService.create(BoardCategory.FREE_BOARD, "Admin detail post", "Body", targetUser);
		this.boardCommentService.create(boardPost, "Admin detail comment body", targetUser);

		MockHttpSession adminSession = login("admin", "1234");
		MockHttpSession targetSession = login(username, "secret123");
		MockHttpSession memberSession = login(memberName, "secret123");
		TradeCatalogItem catalogItem = this.tradeCatalogService.getCatalogItems().stream().findFirst().orElseThrow();
		createTradeItem(targetSession, catalogItem, 333);
		enableAdminMode(adminSession);

		this.mockMvc.perform(get("/admin").session(adminSession))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("/admin/users/" + targetUser.getId())));

		this.mockMvc.perform(get("/admin/users/" + targetUser.getId()).session(memberSession))
				.andExpect(status().isForbidden());

		this.mockMvc.perform(get("/admin/users/" + targetUser.getId()).session(adminSession))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString(username)))
				.andExpect(content().string(containsString("Admin detail post")))
				.andExpect(content().string(containsString("Admin detail comment body")))
				.andExpect(content().string(containsString("작성한 거래글")))
				.andExpect(content().string(containsString("구매 이력")))
				.andExpect(content().string(containsString("판매 이력")));
	}

	@Test
	void skilltreeCommentRedirectUsesSlugUrl() throws Exception {
		String username = "skilluser" + System.nanoTime();
		this.userService.create(username, username + "@example.com", "secret123");
		MockHttpSession session = login(username, "secret123");
		SiteUser author = this.userService.getUser(username);

		SkillTreePost post = this.skillTreePostService.create(SkillTreeJob.SORCERER, "Skill Test", "Body", author);

		this.mockMvc.perform(post("/skilltree/comments/create/" + post.getId())
				.session(session)
				.with(csrf())
				.param("content", "Nice build"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrlPattern("/skilltree/sorcerer/posts/" + post.getId() + "#comment_*"));
	}

	@Test
	@Transactional
	void tradePurchaseCompletesWithoutServerError() throws Exception {
		String sellerName = "seller" + System.nanoTime();
		String buyerName = "buyer" + System.nanoTime();
		this.userService.create(sellerName, sellerName + "@example.com", "secret123");
		this.userService.create(buyerName, buyerName + "@example.com", "secret123");

		MockHttpSession sellerSession = login(sellerName, "secret123");
		MockHttpSession buyerSession = login(buyerName, "secret123");
		TradeCatalogItem catalogItem = this.tradeCatalogService.getCatalogItems().stream().findFirst().orElseThrow();

		MvcResult createResult = this.mockMvc.perform(post("/trade/items/new")
				.session(sellerSession)
				.with(csrf())
				.param("category", catalogItem.getCategory())
				.param("catalogItemId", String.valueOf(catalogItem.getId()))
				.param("price", "250")
				.param("options", "purchase test"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrlPattern("/trade/items/*"))
				.andReturn();

		Integer itemId = Integer.valueOf(createResult.getResponse().getRedirectedUrl()
				.substring(createResult.getResponse().getRedirectedUrl().lastIndexOf('/') + 1));

		this.mockMvc.perform(post("/trade/items/" + itemId + "/purchase")
				.session(buyerSession)
				.with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/trade/items/" + itemId));

		TradeItem purchasedItem = this.tradeItemRepository.findById(itemId).orElseThrow();
		assertTrue(purchasedItem.isSoldOut());
		assertEquals(1, this.tradeTransactionRepository.findAll().size());
	}

	@Test
	@Transactional
	void adminCanHideTradeItemAndHiddenItemCannotBeOpened() throws Exception {
		this.userService.ensureUser("admin", "admin@example.com", "1234");
		String sellerName = "hideseller" + System.nanoTime();
		this.userService.create(sellerName, sellerName + "@example.com", "secret123");

		MockHttpSession adminSession = login("admin", "1234");
		MockHttpSession sellerSession = login(sellerName, "secret123");
		TradeCatalogItem catalogItem = this.tradeCatalogService.getCatalogItems().stream().findFirst().orElseThrow();

		Integer itemId = createTradeItem(sellerSession, catalogItem, 275);
		enableAdminMode(adminSession);

		this.mockMvc.perform(post("/admin/trade-items/" + itemId + "/hide")
				.session(adminSession)
				.with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin?userPage=0&postPage=0&commentPage=0&tradePage=0"));

		TradeItem hiddenItem = this.tradeItemRepository.findById(itemId).orElseThrow();
		assertTrue(hiddenItem.isHidden());

		this.mockMvc.perform(get("/trade/items/" + itemId).session(sellerSession))
				.andExpect(status().isNotFound());
	}

	@Test
	@Transactional
	void adminSearchFiltersListsByName() throws Exception {
		this.userService.ensureUser("admin", "admin@example.com", "1234");
		String targetName = "findme" + System.nanoTime();
		String otherName = "skipme" + System.nanoTime();

		SiteUser targetUser = this.userService.create(targetName, targetName + "@example.com", "secret123");
		SiteUser otherUser = this.userService.create(otherName, otherName + "@example.com", "secret123");

		BoardPost targetPost = this.boardPostService.create(BoardCategory.FREE_BOARD, "Target board post", "body", targetUser);
		BoardPost otherPost = this.boardPostService.create(BoardCategory.FREE_BOARD, "Other board post", "body", otherUser);
		this.boardCommentService.create(targetPost, "Target comment", targetUser);
		this.boardCommentService.create(otherPost, "Other comment", otherUser);

		MockHttpSession adminSession = login("admin", "1234");
		MockHttpSession targetSession = login(targetName, "secret123");
		MockHttpSession otherSession = login(otherName, "secret123");
		TradeCatalogItem catalogItem = this.tradeCatalogService.getCatalogItems().stream().findFirst().orElseThrow();

		createTradeItem(targetSession, catalogItem, 111);
		createTradeItem(otherSession, catalogItem, 222);
		enableAdminMode(adminSession);

		this.mockMvc.perform(get("/admin")
				.session(adminSession)
				.param("kw", targetName))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Target board post")))
				.andExpect(content().string(containsString("Target comment")))
				.andExpect(content().string(containsString(targetName)))
				.andExpect(content().string(org.hamcrest.Matchers.not(containsString("Other board post"))))
				.andExpect(content().string(org.hamcrest.Matchers.not(containsString("Other comment"))))
				.andExpect(content().string(org.hamcrest.Matchers.not(containsString(otherName))));
	}

	@Test
	@Transactional
	void adminDashboardShowsSummaryCardsAndCharts() throws Exception {
		this.userService.ensureUser("admin", "admin@example.com", "1234");

		String elyosName = "elyos" + System.nanoTime();
		String asmodianName = "asmodian" + System.nanoTime();
		SiteUser elyosUser = this.userService.create(elyosName, elyosName + "@example.com", "secret123", "elyos");
		SiteUser asmodianUser = this.userService.create(asmodianName, asmodianName + "@example.com", "secret123", "asmodian");

		BoardPost freePost = this.boardPostService.create(BoardCategory.FREE_BOARD, "Free dashboard post", "Body", elyosUser);
		this.boardPostService.create(BoardCategory.GUILD_RECRUITMENT, "Guild dashboard post", "Body", asmodianUser);
		this.boardPostService.create(BoardCategory.BOSS_GUIDE, "Boss dashboard post", "Body", asmodianUser);
		this.boardPostService.create(BoardCategory.HIGHLIGHT, "Highlight dashboard post", "Body", asmodianUser);

		this.boardCommentService.create(freePost, "Visible dashboard comment", elyosUser);
		BoardComment parentComment = this.boardCommentService.create(freePost, "Parent dashboard comment", asmodianUser);
		this.boardCommentService.create(freePost, "Child dashboard comment", elyosUser, parentComment);
		this.boardCommentService.delete(parentComment);

		MockHttpSession adminSession = login("admin", "1234");
		MockHttpSession elyosSession = login(elyosName, "secret123");
		MockHttpSession asmodianSession = login(asmodianName, "secret123");
		TradeCatalogItem catalogItem = this.tradeCatalogService.getCatalogItems().stream().findFirst().orElseThrow();

		Integer hiddenItemId = createTradeItem(elyosSession, catalogItem, 150);
		Integer soldItemId = createTradeItem(asmodianSession, catalogItem, 260);

		this.mockMvc.perform(post("/trade/items/" + soldItemId + "/purchase")
				.session(elyosSession)
				.with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/trade/items/" + soldItemId));

		enableAdminMode(adminSession);

		this.mockMvc.perform(post("/admin/trade-items/" + hiddenItemId + "/hide")
				.session(adminSession)
				.with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin?userPage=0&postPage=0&commentPage=0&tradePage=0"));

		this.mockMvc.perform(get("/admin").session(adminSession))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("총 회원 수")))
				.andExpect(content().string(containsString("총 게시글 수")))
				.andExpect(content().string(containsString("총 댓글 수")))
				.andExpect(content().string(containsString("총 거래글 수")))
				.andExpect(content().string(containsString("회원 종족 비율")))
				.andExpect(content().string(containsString("게시판 카테고리별 게시글 수")))
				.andExpect(content().string(containsString("댓글 상태 통계")))
				.andExpect(content().string(containsString("거래글 상태 통계")))
				.andExpect(content().string(containsString("adminLoginTimeChart")))
				.andExpect(content().string(containsString("dashboardStats.loginTimeStats")))
				.andExpect(content().string(containsString("adminUserRaceChart")))
				.andExpect(content().string(containsString("adminBoardCategoryChart")))
				.andExpect(content().string(containsString("adminCommentStatusChart")))
				.andExpect(content().string(containsString("adminTradeStatusChart")))
				.andExpect(content().string(containsString("cdn.jsdelivr.net/npm/chart.js")));
	}

	@Test
	@Transactional
	void adminButtonsAppearOnDetailPagesOnlyWhenAdminModeIsEnabled() throws Exception {
		this.userService.ensureUser("admin", "admin@example.com", "1234");
		String username = "detailowner" + System.nanoTime();
		this.userService.create(username, username + "@example.com", "secret123");

		SiteUser author = this.userService.getUser(username);
		BoardPost post = this.boardPostService.create(BoardCategory.FREE_BOARD, "Mode board post", "Body", author);
		this.boardCommentService.create(post, "Mode comment", author);
		SkillTreePost skillTreePost = this.skillTreePostService.create(SkillTreeJob.SORCERER, "Mode skilltree post", "Body", author);
		SkillTreeComment skillTreeComment = this.skillTreeCommentService.create(skillTreePost, "Mode skilltree comment", author);

		MockHttpSession adminSession = login("admin", "1234");
		MockHttpSession authorSession = login(username, "secret123");
		TradeCatalogItem catalogItem = this.tradeCatalogService.getCatalogItems().stream().findFirst().orElseThrow();
		Integer itemId = createTradeItem(authorSession, catalogItem, 410);

		this.mockMvc.perform(get("/boards/free/posts/" + post.getId()).session(adminSession))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.not(containsString("/admin/posts/" + post.getId() + "/delete"))))
				.andExpect(content().string(org.hamcrest.Matchers.not(containsString("/admin/comments/"))));

		this.mockMvc.perform(get("/trade/items/" + itemId).session(adminSession))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.not(containsString("/admin/trade-items/" + itemId + "/hide"))));

		this.mockMvc.perform(get("/skilltree/sorcerer/posts/" + skillTreePost.getId()).session(adminSession))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.not(containsString("/admin/skilltree/posts/" + skillTreePost.getId() + "/delete"))))
				.andExpect(content().string(org.hamcrest.Matchers.not(containsString("/admin/skilltree/comments/" + skillTreeComment.getId() + "/delete"))));

		enableAdminMode(adminSession);

		this.mockMvc.perform(get("/boards/free/posts/" + post.getId()).session(adminSession))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("/admin/posts/" + post.getId() + "/delete")))
				.andExpect(content().string(containsString("/admin/comments/")));

		this.mockMvc.perform(get("/trade/items/" + itemId).session(adminSession))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("/admin/trade-items/" + itemId + "/hide")));

		this.mockMvc.perform(get("/skilltree/sorcerer/posts/" + skillTreePost.getId()).session(adminSession))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("/admin/skilltree/posts/" + skillTreePost.getId() + "/delete")))
				.andExpect(content().string(containsString("/admin/skilltree/comments/" + skillTreeComment.getId() + "/delete")));
	}

	@Test
	@Transactional
	void adminCanDeleteSkillTreeContentOnlyInAdminMode() throws Exception {
		this.userService.ensureUser("admin", "admin@example.com", "1234");
		String username = "skilladmin" + System.nanoTime();
		this.userService.create(username, username + "@example.com", "secret123");

		SiteUser author = this.userService.getUser(username);
		SkillTreePost post = this.skillTreePostService.create(SkillTreeJob.SORCERER, "Admin skilltree post", "Body", author);
		SkillTreeComment comment = this.skillTreeCommentService.create(post, "Admin skilltree comment", author);

		MockHttpSession adminSession = login("admin", "1234");

		this.mockMvc.perform(post("/admin/skilltree/posts/" + post.getId() + "/delete")
				.session(adminSession)
				.with(csrf())
				.param("redirect", "/skilltree/sorcerer/posts/" + post.getId()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin"));

		enableAdminMode(adminSession);

		this.mockMvc.perform(post("/admin/skilltree/comments/" + comment.getId() + "/delete")
				.session(adminSession)
				.with(csrf())
				.param("redirect", "/skilltree/sorcerer/posts/" + post.getId()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/skilltree/sorcerer/posts/" + post.getId()));

		this.mockMvc.perform(post("/admin/skilltree/posts/" + post.getId() + "/delete")
				.session(adminSession)
				.with(csrf())
				.param("redirect", "/skilltree/sorcerer"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/skilltree/sorcerer"));

		this.mockMvc.perform(get("/skilltree/sorcerer/posts/" + post.getId()).session(adminSession))
				.andExpect(status().isNotFound());
	}

	@Test
	@Transactional
	void ownedOnlyCatalogMapsLegacyPurchasedItemsToActualEquipment() throws Exception {
		String sellerName = "legacyseller" + System.nanoTime();
		String buyerName = "legacybuyer" + System.nanoTime();
		this.userService.create(sellerName, sellerName + "@example.com", "secret123");
		this.userService.create(buyerName, buyerName + "@example.com", "secret123");

		this.itemRepository.save(buildWeaponItem(9301L, "Bronze Blade", "weapon", 8, 16, 0, "Rare"));
		this.itemRepository.save(buildWeaponItem(9302L, "Silver Blade", "weapon", 12, 24, 1, "Epic"));

		TradeCatalogItem legacyCatalogItem = new TradeCatalogItem();
		legacyCatalogItem.setCategory("weapon");
		legacyCatalogItem.setItemName("weapon 1");
		legacyCatalogItem.setImageUrl("/images/legacy-weapon-1.png");
		legacyCatalogItem.setDisplayOrder(999_001);
		this.tradeCatalogItemRepository.save(legacyCatalogItem);

		MockHttpSession sellerSession = login(sellerName, "secret123");
		MockHttpSession buyerSession = login(buyerName, "secret123");

		Integer tradeItemId = createTradeItem(sellerSession, legacyCatalogItem, 250);

		this.mockMvc.perform(post("/trade/items/" + tradeItemId + "/purchase")
				.session(buyerSession)
				.with(csrf()))
				.andExpect(status().is3xxRedirection());

		this.mockMvc.perform(get("/api/mypage/items")
				.session(buyerSession)
				.param("slotCode", "weapon")
				.param("ownedOnly", "true"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Bronze Blade")))
				.andExpect(content().string(containsString("\"owned\":true")));
	}

	@Test
	@Transactional
	void growthRecommendationPrefersOwnedUpgradeAndReturnsDeltaHighlights() throws Exception {
		String sellerName = "recseller" + System.nanoTime();
		String buyerName = "recbuyer" + System.nanoTime();
		this.userService.create(sellerName, sellerName + "@example.com", "secret123");
		this.userService.create(buyerName, buyerName + "@example.com", "secret123");

		this.itemRepository.save(buildWeaponItem(9401L, "Bronze Blade", "weapon", 8, 16, 0, "Rare"));
		this.itemRepository.save(buildWeaponItem(9402L, "Silver Blade", "weapon", 12, 28, 2, "Epic"));
		this.itemRepository.save(buildWeaponItem(9403L, "Golden Blade", "weapon", 18, 40, 4, "Legend"));

		TradeCatalogItem legacyCatalogItem = new TradeCatalogItem();
		legacyCatalogItem.setCategory("weapon");
		legacyCatalogItem.setItemName("weapon 8");
		legacyCatalogItem.setImageUrl("/images/legacy-weapon-8.png");
		legacyCatalogItem.setDisplayOrder(999_002);
		this.tradeCatalogItemRepository.save(legacyCatalogItem);

		MockHttpSession sellerSession = login(sellerName, "secret123");
		MockHttpSession buyerSession = login(buyerName, "secret123");

		Integer tradeItemId = createTradeItem(sellerSession, legacyCatalogItem, 320);

		this.mockMvc.perform(post("/trade/items/" + tradeItemId + "/purchase")
				.session(buyerSession)
				.with(csrf()))
				.andExpect(status().is3xxRedirection());

		this.mockMvc.perform(post("/api/mypage/recommendations")
				.session(buyerSession)
				.with(csrf())
				.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
				.content("{\"equippedItemIds\":{\"weapon\":9401}}"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("\"targetItemName\":\"Golden Blade\"")))
				.andExpect(content().string(containsString("\"deltaHighlights\"")))
				.andExpect(content().string(containsString("\"ownedUpgradeAvailable\":true")));
	}

	@Test
	@Transactional
	void simulatorCatalogFiltersItemsByUserRace() throws Exception {
		String username = "elyosuser" + System.nanoTime();
		this.userService.create(username, username + "@example.com", "secret123", "elyos");

		Item elyosWeapon = buildWeaponItem(9501L, "Elyos Blade", "weapon", 10, 20, 2, "Rare");
		elyosWeapon.setRaceName("천족");
		this.itemRepository.save(elyosWeapon);

		Item asmodianWeapon = buildWeaponItem(9502L, "Asmodian Blade", "weapon", 12, 24, 3, "Rare");
		asmodianWeapon.setRaceName("마족");
		this.itemRepository.save(asmodianWeapon);

		Item sharedWeapon = buildWeaponItem(9503L, "Shared Blade", "weapon", 8, 18, 1, "Common");
		sharedWeapon.setRaceName("전체");
		this.itemRepository.save(sharedWeapon);

		MockHttpSession session = login(username, "secret123");

		this.mockMvc.perform(get("/api/simulator/items")
				.session(session)
				.param("slotCode", "weapon"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Elyos Blade")))
				.andExpect(content().string(containsString("Shared Blade")))
				.andExpect(content().string(org.hamcrest.Matchers.not(containsString("Asmodian Blade"))));
	}

	private Integer createTradeItem(MockHttpSession sellerSession, TradeCatalogItem catalogItem, int price) throws Exception {
		MvcResult createResult = this.mockMvc.perform(post("/trade/items/new")
				.session(sellerSession)
				.with(csrf())
				.param("category", catalogItem.getCategory())
				.param("catalogItemId", String.valueOf(catalogItem.getId()))
				.param("price", String.valueOf(price))
				.param("options", "trade setup"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrlPattern("/trade/items/*"))
				.andReturn();

		return Integer.valueOf(createResult.getResponse().getRedirectedUrl()
				.substring(createResult.getResponse().getRedirectedUrl().lastIndexOf('/') + 1));
	}

	private MockHttpSession login(String username, String password) throws Exception {
		MvcResult loginResult = this.mockMvc.perform(post("/user/login")
				.with(csrf())
				.param("username", username)
				.param("password", password))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/"))
				.andReturn();

		return (MockHttpSession) loginResult.getRequest().getSession(false);
	}

	private void enableAdminMode(MockHttpSession session) throws Exception {
		this.mockMvc.perform(post("/admin/mode/enable")
				.session(session)
				.with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin"));
	}

	private int countOccurrences(String source, String needle) {
		if (!StringUtils.hasText(source) || !StringUtils.hasText(needle)) {
			return 0;
		}

		int count = 0;
		int index = 0;
		while ((index = source.indexOf(needle, index)) >= 0) {
			count++;
			index += needle.length();
		}
		return count;
	}

	private Item buildWeaponItem(
			Long id,
			String name,
			String categoryName,
			int minAttack,
			int maxAttack,
			int critical,
			String grade) {
		Item item = new Item();
		item.setId(id);
		item.setName(name);
		item.setSlotCode("weapon");
		item.setCategoryName(categoryName);
		item.setGrade(grade);
		item.setGradeName(grade);
		item.setType("Equip");
		item.setTradable(Boolean.TRUE);
		item.setEquipLevel(1);
		item.setEnchantable(Boolean.TRUE);
		item.setMagicStoneSlotCount(1);
		item.setGodStoneSlotCount(0);
		item.setPowerScore((double) maxAttack * 4d);
		item.setMainStatsJson("[{\"name\":\"\\uacf5\\uaca9\\ub825\",\"minValue\":\"" + minAttack
				+ "\",\"value\":\"" + maxAttack
				+ "\"},{\"name\":\"\\uce58\\uba85\\ud0c0\",\"value\":\"" + critical + "\"}]");
		item.setSubStatsJson("[]");
		item.setSubSkillsJson("[]");
		item.setMagicStoneStatJson("[]");
		item.setGodStoneStatJson("[]");
		item.setSyncedAt(java.time.LocalDateTime.now());
		return item;
	}
}
