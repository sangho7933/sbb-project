package com.mysite.sbb.ai;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class AiSearchService {

	private static final AiSearchRecommendation FREE_BOARD_PAGE = recommendation(
			"게시판", "자유게시판", "질문, 잡담, 정보 공유를 자유롭게 나눌 수 있는 게시판입니다.", "/boards/free");
	private static final AiSearchRecommendation FREE_WRITE_PAGE = recommendation(
			"게시판", "자유게시판 글쓰기", "자유게시판에 바로 글을 작성할 수 있습니다.", "/boards/free/write", true);
	private static final AiSearchRecommendation GUILD_BOARD_PAGE = recommendation(
			"게시판", "길드 모집 게시판", "길드 모집과 길드 관련 글을 확인할 수 있습니다.", "/boards/guild");
	private static final AiSearchRecommendation GUILD_WRITE_PAGE = recommendation(
			"게시판", "길드 모집 글쓰기", "길드 모집 글을 바로 작성할 수 있습니다.", "/boards/guild/write", true);
	private static final AiSearchRecommendation BOSS_BOARD_PAGE = recommendation(
			"게시판", "보스 공략 게시판", "보스 공략 글을 확인할 수 있습니다.", "/boards/boss");
	private static final AiSearchRecommendation BOSS_WRITE_PAGE = recommendation(
			"게시판", "보스 공략 글쓰기", "보스 공략 글을 바로 작성할 수 있습니다.", "/boards/boss/write", true);
	private static final AiSearchRecommendation HIGHLIGHT_PAGE = recommendation(
			"게시판", "하이라이트 게시판", "인상적인 플레이와 하이라이트 글을 볼 수 있습니다.", "/boards/highlight");
	private static final AiSearchRecommendation HIGHLIGHT_WRITE_PAGE = recommendation(
			"게시판", "하이라이트 글쓰기", "하이라이트 글을 바로 작성할 수 있습니다.", "/boards/highlight/write", true);

	private static final AiSearchRecommendation SKILLTREE_HOME_PAGE = recommendation(
			"직업", "스킬트리 메인", "직업별 스킬트리와 운영 글을 확인할 수 있습니다.", "/skilltree");
	private static final AiSearchRecommendation SKILLTREE_WRITE_PAGE = recommendation(
			"직업", "스킬트리 글쓰기", "직업/스킬 관련 글을 바로 작성할 수 있습니다.", "/skilltree/write", true);

	private static final AiSearchRecommendation GUIDE_HOME_PAGE = recommendation(
			"가이드", "뉴비 가이드", "처음 시작할 때 필요한 기본 가이드를 확인할 수 있습니다.", "/guide?step=1");
	private static final AiSearchRecommendation JOB_SELECTION_GUIDE_PAGE = recommendation(
			"가이드", "직업 선택 가이드", "직업 선택이 고민될 때 참고할 수 있는 가이드입니다.", "/guide?step=3");
	private static final AiSearchRecommendation LEVELING_GUIDE_PAGE = recommendation(
			"가이드", "레벨링 가이드", "초반 성장과 레벨업 동선을 빠르게 확인할 수 있습니다.", "/guide?step=4");
	private static final AiSearchRecommendation DUNGEON_GEAR_GUIDE_PAGE = recommendation(
			"가이드", "던전/장비 가이드", "장비 파밍과 던전 보상 흐름을 정리한 가이드입니다.", "/guide?step=5");
	private static final AiSearchRecommendation TRADE_GUIDE_PAGE = recommendation(
			"가이드", "거래 이용 가이드", "거래 게시판 이용 방법을 단계별로 확인할 수 있습니다.", "/guide?step=6");
	private static final AiSearchRecommendation KINA_GUIDE_PAGE = recommendation(
			"가이드", "키나 수급 가이드", "키나를 모으는 방법을 정리한 가이드입니다.", "/guide?step=7");
	private static final AiSearchRecommendation GROWTH_ROUTE_PAGE = recommendation(
			"가이드", "성장 루트 가이드", "강화와 성장 루트를 정리한 가이드입니다.", "/guide?step=8");

	private static final AiSearchRecommendation TRADE_BOARD_PAGE = recommendation(
			"기능", "거래게시판", "거래 게시판과 시세 정보를 확인할 수 있습니다.", "/trade/items");
	private static final AiSearchRecommendation SIMULATOR_PAGE = recommendation(
			"기능", "장비 시뮬레이터", "장비 세팅과 능력치를 비교해 볼 수 있습니다.", "/simulator", true);
	private static final AiSearchRecommendation MYPAGE_PAGE = recommendation(
			"계정", "마이페이지", "내 캐릭터와 장비 상태를 확인할 수 있습니다.", "/mypage", true);
	private static final AiSearchRecommendation LOGIN_PAGE = recommendation(
			"계정", "로그인", "로그인 후 마이페이지와 시뮬레이터 같은 기능을 이용할 수 있습니다.", "/user/login");
	private static final AiSearchRecommendation SIGNUP_PAGE = recommendation(
			"계정", "회원가입", "계정을 만들어 커뮤니티 기능을 이용할 수 있습니다.", "/user/signup");

	private static final List<String> STRONG_WRITE_INTENT_KEYWORDS = List.of(
			"글쓰기", "글쓰", "글써", "써줘", "써줄래", "작성", "작성할래", "적어줘", "적을래", "올릴래",
			"등록", "등록할래", "모집글", "공략글", "초안", "포스팅", "포스트", "쓸래", "쓰러", "쓰고싶어");
	private static final List<String> WEAK_WRITE_INTENT_KEYWORDS = List.of("글");
	private static final List<String> VIEW_INTENT_KEYWORDS = List.of(
			"가줘", "보여줘", "열어줘", "보러가자", "보러", "이동", "들어가자", "가자", "보기", "보자", "열기");
	private static final List<String> TRADE_TARGET_KEYWORDS = List.of("거래게시판", "거래소", "거래", "trade", "market");
	private static final List<String> TRADE_ITEM_KEYWORDS = List.of(
			"빛나는", "가더", "단검", "대검", "장검", "활", "법서", "법봉", "보주", "전곤", "지팡이",
			"투구", "갑옷", "흉갑", "장화", "각반", "장갑", "견갑", "반지", "목걸이", "귀걸이", "방패");
	private static final List<TradeKeywordAlias> TRADE_KEYWORD_ALIASES = List.of(
			new TradeKeywordAlias("신발", "장화"),
			new TradeKeywordAlias("바지", "각반"),
			new TradeKeywordAlias("어깨", "견갑"),
			new TradeKeywordAlias("갑바", "흉갑"),
			new TradeKeywordAlias("모자", "투구"),
			new TradeKeywordAlias("귀", "귀걸이"),
			new TradeKeywordAlias("목", "목걸이"),
			new TradeKeywordAlias("링", "반지"),
			new TradeKeywordAlias("실드", "방패"),
			new TradeKeywordAlias("책", "법서"),
			new TradeKeywordAlias("오브", "보주"),
			new TradeKeywordAlias("스태프", "지팡이"),
			new TradeKeywordAlias("단도", "단검"));
	private static final List<String> TRADE_SEARCH_REMOVE_PHRASES = List.of(
			"거래 게시판", "거래게시판", "거래소", "거래", "trade", "market",
			"살래", "살까", "사고 싶어", "사고싶어", "볼래", "보자", "추천해줘", "추천", "보여줘",
			"검색", "찾고 싶어", "찾고싶어", "찾아줘", "템", "상품", "판매", "좀", "이거", "쫙");
	private static final List<String> TRADE_SEARCH_STOPWORDS = List.of("좀", "이거", "쫙");

	private static final List<String> BOARD_PROMPT_WRITE_PHRASES = List.of(
			"글 쓰고 싶어", "글쓰고싶어", "글 쓰기", "글쓰기", "글 써줄래", "글써줄래", "글 써줘", "글써줘",
			"글 쓰러", "글쓰러", "글 쓸래", "글쓸래", "글 써", "글써", "작성할래", "작성할게", "작성", "적어줘",
			"적을래", "올릴래", "등록할래", "등록", "모집글", "공략글", "초안", "포스팅", "포스트", "써줄래", "써줘", "쓸래", "쓰러",
			"쓰고 싶어", "쓰고싶어", "글");
	private static final List<String> BOARD_PROMPT_STOPWORDS = List.of(
			"에", "에서", "으로", "로", "용", "관련", "좀", "하나", "한번", "부탁", "부탁해", "부탁해줘", "해주세요", "해줘",
			"가줘", "보여줘", "열어줘", "보러가자", "보러", "이동", "들어가자", "가자", "보기", "보자", "열기");
	private static final List<BoardWritePromptConfig> BOARD_WRITE_PROMPT_CONFIGS = List.of(
			new BoardWritePromptConfig(
					"free",
					"자유게시판용 글 초안 작성",
					List.of("자유게시판", "자유 게시판", "자유 글", "자유글", "자게")),
			new BoardWritePromptConfig(
					"guild",
					"길드게시판용 글 초안 작성",
					List.of("길드게시판", "길드 게시판", "길드창", "길드 창", "길드모집", "길드 모집", "길드구인", "길드 구인",
							"길드 글", "길드글", "길드에", "길드에서", "길드로", "길드용", "길드 관련")),
			new BoardWritePromptConfig(
					"boss",
					"보스게시판용 글 초안 작성",
					List.of("보스게시판", "보스 게시판", "보스공략", "보스 공략", "보스 글", "보스글", "보스에", "보스에서",
							"보스로", "보스용", "보스 관련")),
			new BoardWritePromptConfig(
					"highlight",
					"하이라이트용 글 초안 작성",
					List.of("하이라이트게시판", "하이라이트 게시판", "하이라이트 글", "하이라이트글", "하이라이트에",
							"하이라이트에서", "하이라이트로", "하이라이트용", "하이라이트 관련")));

	private static final List<NavigationTarget> NAVIGATION_TARGETS = List.of(
			jobTarget("gladiator", "검성", List.of("검성", "gladiator")),
			jobTarget("templar", "수호성", List.of("수호성", "수호", "templar")),
			jobTarget("assassin", "살성", List.of("살성", "assassin")),
			jobTarget("ranger", "궁성", List.of("궁성", "ranger")),
			jobTarget("sorcerer", "마도성", List.of("마도성", "마도", "마법사", "법사", "sorcerer")),
			jobTarget("spiritmaster", "정령성", List.of("정령성", "정령", "spiritmaster")),
			jobTarget("cleric", "치유성", List.of("치유성", "치유", "힐러", "cleric")),
			jobTarget("chanter", "호법성", List.of("호법성", "호법", "chanter")),
			boardTarget("free", "자유게시판",
					List.of("자유게시판", "자유글", "자유", "자게", "free"),
					FREE_BOARD_PAGE,
					FREE_WRITE_PAGE),
			boardTarget("guild", "길드 모집 게시판",
					List.of("길드게시판", "길드창", "길드모집", "길드구인", "길드 모집", "길드 구인", "길드", "guild"),
					GUILD_BOARD_PAGE,
					GUILD_WRITE_PAGE),
			boardTarget("boss", "보스 공략 게시판",
					List.of("보스공략", "보스게시판", "보스 글", "보스공략글", "보스", "raid", "boss"),
					BOSS_BOARD_PAGE,
					BOSS_WRITE_PAGE),
			boardTarget("highlight", "하이라이트 게시판",
					List.of("하이라이트게시판", "하이라이트 글", "하이라이트", "highlight"),
					HIGHLIGHT_PAGE,
					HIGHLIGHT_WRITE_PAGE),
			target("skill/common",
					"직업",
					List.of("직업스킬트리", "직업 스킬트리", "직업스킬", "직업 스킬", "스킬트리", "스킬", "skilltree", "skill"),
					SKILLTREE_HOME_PAGE,
					SKILLTREE_WRITE_PAGE));

	private static final List<KeywordGroup> KEYWORD_GROUPS = List.of(
			group("가이드 키워드",
					rule(List.of("뉴비", "초보", "입문", "처음시작", "newbie"), GUIDE_HOME_PAGE),
					rule(List.of("직업추천", "직업선택"), JOB_SELECTION_GUIDE_PAGE),
					rule(List.of("레벨링", "레벨업", "성장", "leveling", "level"), LEVELING_GUIDE_PAGE, GROWTH_ROUTE_PAGE),
					rule(List.of("장비세팅", "세팅추천", "강화", "옵션"), SIMULATOR_PAGE, DUNGEON_GEAR_GUIDE_PAGE, GROWTH_ROUTE_PAGE),
					rule(List.of("키나", "골드"), KINA_GUIDE_PAGE, TRADE_GUIDE_PAGE),
					rule(List.of("루트"), GROWTH_ROUTE_PAGE)),
			group("기능 키워드",
					rule(List.of("시세", "거래게시판", "tradeboard"), TRADE_BOARD_PAGE),
					rule(List.of("거래방법", "거래이용", "거래가이드"), TRADE_GUIDE_PAGE, TRADE_BOARD_PAGE),
					rule(List.of("거래", "trade", "market"), TRADE_BOARD_PAGE),
					rule(List.of("시뮬레이터", "시뮬", "장비비교", "세팅비교"), SIMULATOR_PAGE),
					rule(List.of("마이페이지", "내정보", "mypage"), MYPAGE_PAGE)),
			group("커뮤니티 키워드",
					rule(List.of("질문"), FREE_BOARD_PAGE),
					rule(List.of("추천"), GUIDE_HOME_PAGE, SKILLTREE_HOME_PAGE, FREE_BOARD_PAGE)),
			group("계정 키워드",
					rule(List.of("로그인", "login"), LOGIN_PAGE),
					rule(List.of("회원가입", "가입", "signup", "join"), SIGNUP_PAGE)));

	public AiSearchResult search(String query) {
		String originalQuery = query == null ? "" : query.trim();
		String normalizedQuery = normalize(query);
		if (normalizedQuery.isBlank()) {
			return new AiSearchResult(originalQuery, normalizedQuery, "", List.of(), List.of());
		}

		AiSearchResult targetNavigation = detectTargetNavigation(originalQuery, normalizedQuery);
		if (targetNavigation != null) {
			return targetNavigation;
		}

		AiSearchResult tradeSearchNavigation = detectTradeSearchNavigation(originalQuery, normalizedQuery);
		if (tradeSearchNavigation != null) {
			return tradeSearchNavigation;
		}

		for (KeywordGroup group : KEYWORD_GROUPS) {
			LinkedHashSet<String> matchedKeywords = new LinkedHashSet<>();
			LinkedHashMap<String, AiSearchRecommendation> recommendations = new LinkedHashMap<>();

			for (KeywordRule rule : group.rules()) {
				List<String> hits = rule.findMatches(normalizedQuery);
				if (hits.isEmpty()) {
					continue;
				}
				matchedKeywords.addAll(hits);
				for (AiSearchRecommendation recommendation : rule.recommendations()) {
					recommendations.putIfAbsent(recommendation.url(), recommendation);
				}
			}

			if (!recommendations.isEmpty()) {
				return new AiSearchResult(
						originalQuery,
						normalizedQuery,
						group.priorityLabel(),
						List.copyOf(matchedKeywords),
						List.copyOf(recommendations.values()));
			}
		}

		return new AiSearchResult(originalQuery, normalizedQuery, "기타 키워드", List.of(), List.of());
	}

	private AiSearchResult detectTargetNavigation(String originalQuery, String normalizedQuery) {
		IntentMatch intentMatch = detectIntent(normalizedQuery);

		for (NavigationTarget target : NAVIGATION_TARGETS) {
			List<String> targetMatches = target.findMatches(normalizedQuery);
			if (targetMatches.isEmpty()) {
				continue;
			}

			LinkedHashSet<String> matchedKeywords = new LinkedHashSet<>(targetMatches);
			matchedKeywords.addAll(intentMatch.matchedKeywords());
			AiSearchRecommendation recommendation = resolveNavigationRecommendation(target, intentMatch.writeIntent(),
					originalQuery);
			return new AiSearchResult(
					originalQuery,
					normalizedQuery,
					target.priorityLabel(intentMatch.writeIntent()),
					List.copyOf(matchedKeywords),
					List.of(recommendation));
		}

		return null;
	}

	private AiSearchResult detectTradeSearchNavigation(String originalQuery, String normalizedQuery) {
		List<String> tradeTargetMatches = findMatches(normalizedQuery, TRADE_TARGET_KEYWORDS);
		String keyword = extractTradeSearchKeyword(originalQuery);
		List<String> tradeItemMatches = findTradeKeywordMatches(keyword.isBlank() ? originalQuery : keyword);
		if (tradeTargetMatches.isEmpty() && tradeItemMatches.isEmpty()) {
			return null;
		}

		if (tradeItemMatches.isEmpty()) {
			if (keyword.isBlank()) {
				return new AiSearchResult(
						originalQuery,
						normalizedQuery,
						"거래 검색",
						List.copyOf(tradeTargetMatches),
						List.of(TRADE_BOARD_PAGE));
			}
			return null;
		}

		LinkedHashSet<String> matchedKeywords = new LinkedHashSet<>(tradeItemMatches);
		matchedKeywords.addAll(tradeTargetMatches);
		if (keyword.isBlank()) {
			keyword = tradeItemMatches.get(0);
		}

		AiSearchRecommendation recommendation = recommendation(
				TRADE_BOARD_PAGE.categoryLabel(),
				TRADE_BOARD_PAGE.title(),
				TRADE_BOARD_PAGE.description(),
				buildTradeSearchUrl(keyword),
				TRADE_BOARD_PAGE.loginRequired());
		return new AiSearchResult(
				originalQuery,
				normalizedQuery,
				"거래 검색",
				List.copyOf(matchedKeywords),
				List.of(recommendation));
	}

	private AiSearchRecommendation resolveNavigationRecommendation(NavigationTarget target, boolean writeIntent,
			String originalQuery) {
		AiSearchRecommendation recommendation = target.recommendationFor(writeIntent);
		if (!writeIntent) {
			return recommendation;
		}

		BoardWritePromptConfig promptConfig = findBoardWritePromptConfig(target.key());
		if (promptConfig == null) {
			return recommendation;
		}

		return recommendation(
				recommendation.categoryLabel(),
				recommendation.title(),
				recommendation.description(),
				buildBoardWriteUrl(recommendation.url(), originalQuery, promptConfig),
				recommendation.loginRequired());
	}

	private BoardWritePromptConfig findBoardWritePromptConfig(String targetKey) {
		return BOARD_WRITE_PROMPT_CONFIGS.stream()
				.filter(config -> config.key().equals(targetKey))
				.findFirst()
				.orElse(null);
	}

	private String buildBoardWriteUrl(String baseWriteUrl, String originalQuery, BoardWritePromptConfig promptConfig) {
		return UriComponentsBuilder.fromUriString(baseWriteUrl)
				.queryParam("aiPrompt", extractBoardAiPrompt(originalQuery, promptConfig))
				.build()
				.encode()
				.toUriString();
	}

	private String buildTradeSearchUrl(String keyword) {
		return UriComponentsBuilder.fromPath("/trade/items")
				.queryParam("kw", keyword)
				.build()
				.encode()
				.toUriString();
	}

	private String extractBoardAiPrompt(String originalQuery, BoardWritePromptConfig promptConfig) {
		String cleaned = originalQuery == null ? "" : originalQuery.trim();
		cleaned = removePromptPhrases(cleaned, promptConfig.targetPhrases(), true);
		cleaned = removePromptPhrases(cleaned, BOARD_PROMPT_WRITE_PHRASES, false);
		cleaned = cleaned.replaceAll("[\\r\\n\\t]+", " ");
		cleaned = cleaned.replaceAll("[!?,.]+", " ");
		cleaned = cleaned.replaceAll("\\s+", " ").trim();
		cleaned = stripPromptStopwords(cleaned);
		return cleaned.isBlank() ? promptConfig.defaultPrompt() : cleaned;
	}

	private String extractTradeSearchKeyword(String originalQuery) {
		String cleaned = originalQuery == null ? "" : originalQuery.trim();
		cleaned = removePromptPhrases(cleaned, TRADE_SEARCH_REMOVE_PHRASES, false);
		cleaned = cleaned.replaceAll("[\\r\\n\\t]+", " ");
		cleaned = cleaned.replaceAll("[!?,.]+", " ");
		cleaned = cleaned.replaceAll("\\s+", " ").trim();
		cleaned = stripTradeSearchStopwords(cleaned);
		return canonicalizeTradeSearchKeyword(cleaned);
	}

	private String removePromptPhrases(String source, List<String> phrases, boolean trimBoardParticles) {
		String cleaned = source;
		for (String phrase : phrases) {
			String pattern = flexiblePhrasePattern(phrase);
			if (trimBoardParticles) {
				pattern = pattern + "(?:에|에서|으로|로|용|관련)?";
			}
			cleaned = cleaned.replaceAll("(?i)" + pattern, " ");
		}
		return cleaned;
	}

	private String stripPromptStopwords(String source) {
		if (source.isBlank()) {
			return "";
		}

		return java.util.Arrays.stream(source.split("\\s+"))
				.filter(token -> !BOARD_PROMPT_STOPWORDS.contains(token))
				.reduce((left, right) -> left + " " + right)
				.orElse("")
				.trim();
	}

	private String stripTradeSearchStopwords(String source) {
		if (source.isBlank()) {
			return "";
		}

		return java.util.Arrays.stream(source.split("\\s+"))
				.filter(token -> !TRADE_SEARCH_STOPWORDS.contains(token))
				.reduce((left, right) -> left + " " + right)
				.orElse("")
				.trim();
	}

	private String canonicalizeTradeSearchKeyword(String source) {
		if (source.isBlank()) {
			return "";
		}

		if (source.contains(" ")) {
			return java.util.Arrays.stream(source.split("\\s+"))
					.map(this::canonicalizeTradeToken)
					.reduce((left, right) -> left + " " + right)
					.orElse("")
					.trim();
		}

		String canonicalToken = canonicalizeTradeToken(source);
		if (!canonicalToken.equals(source)) {
			return canonicalToken;
		}

		List<String> matches = findTradeKeywordMatches(source);
		if (!matches.isEmpty()) {
			return String.join(" ", matches);
		}

		return source;
	}

	private String canonicalizeTradeToken(String token) {
		String normalizedToken = normalizeToken(token);
		return TRADE_KEYWORD_ALIASES.stream()
				.filter(alias -> normalizeToken(alias.alias()).equals(normalizedToken))
				.map(TradeKeywordAlias::canonical)
				.findFirst()
				.orElse(token);
	}

	private List<String> findTradeKeywordMatches(String source) {
		if (source == null || source.isBlank()) {
			return List.of();
		}

		LinkedHashSet<String> matches = new LinkedHashSet<>();
		String normalizedSource = normalize(source);

		for (String keyword : TRADE_ITEM_KEYWORDS) {
			if (normalizedSource.contains(normalizeToken(keyword))) {
				matches.add(keyword);
			}
		}

		for (TradeKeywordAlias alias : TRADE_KEYWORD_ALIASES) {
			String normalizedAlias = normalizeToken(alias.alias());
			if (normalizedAlias.length() >= 2 && normalizedSource.contains(normalizedAlias)) {
				matches.add(alias.canonical());
			}
		}

		for (String token : source.split("\\s+")) {
			String canonicalToken = canonicalizeTradeToken(token);
			if (!canonicalToken.equals(token) && TRADE_ITEM_KEYWORDS.contains(canonicalToken)) {
				matches.add(canonicalToken);
			}
		}

		return List.copyOf(matches);
	}

	private String flexiblePhrasePattern(String phrase) {
		return java.util.Arrays.stream(phrase.trim().split("\\s+"))
				.map(Pattern::quote)
				.reduce((left, right) -> left + "\\s*" + right)
				.orElse("");
	}

	private IntentMatch detectIntent(String normalizedQuery) {
		List<String> strongWriteMatches = findMatches(normalizedQuery, STRONG_WRITE_INTENT_KEYWORDS);
		if (!strongWriteMatches.isEmpty()) {
			return new IntentMatch(true, List.copyOf(strongWriteMatches));
		}

		List<String> weakWriteMatches = findMatches(normalizedQuery, WEAK_WRITE_INTENT_KEYWORDS);
		List<String> viewMatches = findMatches(normalizedQuery, VIEW_INTENT_KEYWORDS);
		if (!weakWriteMatches.isEmpty() && viewMatches.isEmpty()) {
			return new IntentMatch(true, List.copyOf(weakWriteMatches));
		}
		if (!viewMatches.isEmpty()) {
			return new IntentMatch(false, List.copyOf(viewMatches));
		}
		return new IntentMatch(false, List.of());
	}

	private String normalize(String query) {
		return normalizeToken(query);
	}

	private List<String> findMatches(String normalizedQuery, List<String> keywords) {
		return keywords.stream()
				.filter(normalizedQuery::contains)
				.toList();
	}

	private static NavigationTarget boardTarget(String key, String priorityLabel, List<String> aliases,
			AiSearchRecommendation viewRecommendation, AiSearchRecommendation writeRecommendation) {
		return target(key, priorityLabel, aliases, viewRecommendation, writeRecommendation);
	}

	private static NavigationTarget jobTarget(String jobSlug, String jobLabel, List<String> aliases) {
		return target("job/" + jobSlug,
				"직업",
				aliases,
				recommendation("직업", jobLabel + " 스킬트리",
						jobLabel + " 관련 스킬트리와 운영 글을 확인할 수 있습니다.",
						"/skilltree/" + jobSlug),
				recommendation("직업", jobLabel + " 스킬 글쓰기",
						jobLabel + " 관련 스킬 글을 바로 작성할 수 있습니다.",
						skillWriteUrl(jobSlug),
						true));
	}

	private static NavigationTarget target(String key, String priorityLabel, List<String> aliases,
			AiSearchRecommendation viewRecommendation, AiSearchRecommendation writeRecommendation) {
		return new NavigationTarget(
				key,
				priorityLabel,
				aliases.stream().map(AiSearchService::normalizeToken).toList(),
				viewRecommendation,
				writeRecommendation);
	}

	private static KeywordGroup group(String priorityLabel, KeywordRule... rules) {
		return new KeywordGroup(priorityLabel, List.of(rules));
	}

	private static KeywordRule rule(List<String> keywords, AiSearchRecommendation... recommendations) {
		return new KeywordRule(
				keywords.stream().map(AiSearchService::normalizeToken).toList(),
				List.of(recommendations));
	}

	private static AiSearchRecommendation recommendation(String categoryLabel, String title, String description,
			String url) {
		return recommendation(categoryLabel, title, description, url, false);
	}

	private static AiSearchRecommendation recommendation(String categoryLabel, String title, String description,
			String url, boolean loginRequired) {
		return new AiSearchRecommendation(categoryLabel, title, description, url, loginRequired);
	}

	private static String normalizeToken(String value) {
		if (value == null) {
			return "";
		}
		return value.trim()
				.toLowerCase(Locale.ROOT)
				.replaceAll("[\\s\\p{Punct}\\p{IsPunctuation}]+", "");
	}

	private static String skillWriteUrl(String jobSlug) {
		return "/skilltree/write?job=" + jobSlug + "&returnUrl=/skilltree/" + jobSlug;
	}

	private record IntentMatch(boolean writeIntent, List<String> matchedKeywords) {
	}

	private record BoardWritePromptConfig(String key, String defaultPrompt, List<String> targetPhrases) {
	}

	private record TradeKeywordAlias(String alias, String canonical) {
	}

	private record NavigationTarget(
			String key,
			String priorityLabel,
			List<String> aliases,
			AiSearchRecommendation viewRecommendation,
			AiSearchRecommendation writeRecommendation) {

		private List<String> findMatches(String normalizedQuery) {
			return aliases.stream()
					.filter(normalizedQuery::contains)
					.toList();
		}

		private AiSearchRecommendation recommendationFor(boolean writeIntent) {
			if (writeIntent && writeRecommendation != null) {
				return writeRecommendation;
			}
			return viewRecommendation;
		}

		private String priorityLabel(boolean writeIntent) {
			return priorityLabel + (writeIntent ? " 작성 의도" : " 조회 의도");
		}
	}

	private record KeywordGroup(String priorityLabel, List<KeywordRule> rules) {
	}

	private record KeywordRule(List<String> keywords, List<AiSearchRecommendation> recommendations) {

		private List<String> findMatches(String normalizedQuery) {
			return keywords.stream()
					.filter(normalizedQuery::contains)
					.findFirst()
					.stream()
					.toList();
		}
	}
}
