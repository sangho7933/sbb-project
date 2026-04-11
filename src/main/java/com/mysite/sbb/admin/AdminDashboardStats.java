package com.mysite.sbb.admin;

import java.util.List;

public record AdminDashboardStats(
		long totalUsers,
		long totalPosts,
		long totalComments,
		long totalTradeItems,
		List<ChartStat> loginTimeStats,
		List<ChartStat> userRaceStats,
		List<ChartStat> boardCategoryStats,
		List<ChartStat> commentStatusStats,
		List<ChartStat> tradeStatusStats) {

	public record ChartStat(String label, long value) {
	}
}
