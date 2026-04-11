/*
 * 관리자 대시보드에서 쓰는 단순 집계 통계를 조합하는 서비스이다.
 */
package com.mysite.sbb.admin;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mysite.sbb.board.BoardCategory;
import com.mysite.sbb.board.BoardPostRepository;
import com.mysite.sbb.comment.BoardCommentRepository;
import com.mysite.sbb.trade.entity.TradeItem;
import com.mysite.sbb.trade.repository.TradeItemRepository;
import com.mysite.sbb.user.UserLoginLogRepository;
import com.mysite.sbb.user.UserRace;
import com.mysite.sbb.user.UserRepository;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
/**
 * 관리자 화면 차트와 요약 카드에 필요한 집계 결과를 만든다.
 */
public class AdminDashboardService {

	private final UserRepository userRepository;
	private final BoardPostRepository boardPostRepository;
	private final BoardCommentRepository boardCommentRepository;
	private final TradeItemRepository tradeItemRepository;
	private final UserLoginLogRepository userLoginLogRepository;

	public AdminDashboardStats getDashboardStats() {
		long totalUsers = this.userRepository.count();
		long totalPosts = this.boardPostRepository.count();
		long totalComments = this.boardCommentRepository.count();
		long totalTradeItems = this.tradeItemRepository.count();

		return new AdminDashboardStats(
				totalUsers,
				totalPosts,
				totalComments,
				totalTradeItems,
				buildTodayLoginTimeStats(),
				buildUserRaceStats(totalUsers),
				buildBoardCategoryStats(totalPosts),
				buildCommentStatusStats(totalComments),
				buildTradeStatusStats(totalTradeItems));
	}

	// 차트에 넘길 label/value 묶음을 간단히 만든다.
	private AdminDashboardStats.ChartStat chartStat(String label, long value) {
		return new AdminDashboardStats.ChartStat(label, value);
	}

	private List<AdminDashboardStats.ChartStat> buildTodayLoginTimeStats() {
		LocalDateTime startOfToday = LocalDate.now().atStartOfDay();

		return List.of(
				chartStat("\uC0C8\uBCBD", countDistinctLoginsBetween(startOfToday, startOfToday.plusHours(6))),
				chartStat("\uC624\uC804", countDistinctLoginsBetween(startOfToday.plusHours(6), startOfToday.plusHours(12))),
				chartStat("\uC624\uD6C4", countDistinctLoginsBetween(startOfToday.plusHours(12), startOfToday.plusHours(18))),
				chartStat("\uC800\uB141", countDistinctLoginsBetween(startOfToday.plusHours(18), startOfToday.plusDays(1))));
	}

	// 종족 분포는 기본 종족 보정 규칙을 반영해 두 구간으로 단순화한다.
	private List<AdminDashboardStats.ChartStat> buildUserRaceStats(long totalUsers) {
		long elyosUsers = this.userRepository.countByRaceValues(
				UserRace.defaultRace().getLabel(),
				normalizeValues(UserRace.ELYOS.getCode(), UserRace.ELYOS.getLabel()));
		long asmodianUsers = Math.max(totalUsers - elyosUsers, 0L);

		return List.of(
				chartStat(UserRace.ELYOS.getLabel(), elyosUsers),
				chartStat(UserRace.ASMODIAN.getLabel(), asmodianUsers));
	}

	// 게시판 차트는 주요 카테고리를 분리하고 나머지를 자유게시판으로 묶는다.
	private List<AdminDashboardStats.ChartStat> buildBoardCategoryStats(long totalPosts) {
		long guildRecruitmentPosts = this.boardPostRepository.countByCategory(BoardCategory.GUILD_RECRUITMENT);
		long bossGuidePosts = this.boardPostRepository.countByCategory(BoardCategory.BOSS_GUIDE);
		long highlightPosts = this.boardPostRepository.countByCategory(BoardCategory.HIGHLIGHT);
		long freeBoardPosts = Math.max(totalPosts - guildRecruitmentPosts - bossGuidePosts - highlightPosts, 0L);

		return List.of(
				chartStat(BoardCategory.FREE_BOARD.getLabel(), freeBoardPosts),
				chartStat(BoardCategory.GUILD_RECRUITMENT.getLabel(), guildRecruitmentPosts),
				chartStat(BoardCategory.BOSS_GUIDE.getLabel(), bossGuidePosts),
				chartStat(BoardCategory.HIGHLIGHT.getLabel(), highlightPosts));
	}

	// 댓글 차트는 노출 여부만 빠르게 비교할 수 있게 두 상태로 나눈다.
	private List<AdminDashboardStats.ChartStat> buildCommentStatusStats(long totalComments) {
		long deletedComments = this.boardCommentRepository.countByDeleted(true);
		long visibleComments = Math.max(totalComments - deletedComments, 0L);

		return List.of(
				chartStat("노출 중", visibleComments),
				chartStat("삭제됨", deletedComments));
	}

	// 거래글 차트는 판매 상태와 숨김 수치를 함께 확인할 수 있게 묶는다.
	private List<AdminDashboardStats.ChartStat> buildTradeStatusStats(long totalTradeItems) {
		long soldOutItems = this.tradeItemRepository.countByStatus(TradeItem.STATUS_SOLD_OUT);
		long onSaleItems = Math.max(totalTradeItems - soldOutItems, 0L);
		long hiddenItems = this.tradeItemRepository.countByHidden(true);

		return List.of(
				chartStat(TradeItem.STATUS_ON_SALE, onSaleItems),
				chartStat(TradeItem.STATUS_SOLD_OUT, soldOutItems),
				chartStat("숨김", hiddenItems));
	}

	// 사용자 종족 집계는 코드/라벨 입력 차이를 같은 비교 기준으로 맞춘다.
	private List<String> normalizeValues(String... values) {
		return Arrays.stream(values)
				.filter(Objects::nonNull)
				.map(String::trim)
				.filter(value -> !value.isBlank())
				.map(value -> value.toLowerCase(Locale.ROOT))
				.toList();
	}

	private long countDistinctLoginsBetween(LocalDateTime start, LocalDateTime end) {
		return this.userLoginLogRepository.countDistinctUsersBetween(start, end);
	}
}
