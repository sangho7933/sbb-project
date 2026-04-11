package com.mysite.sbb.mypage.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class MyPageView {

	private MyPageView() {
	}

	public record Overview(
			String username,
			String email,
			String race,
			Integer gold,
			String representativeLabel,
			String activityLabel,
			long presetCount,
			long equippedCount,
			LocalDateTime lastEquipmentUpdatedAt,
			LocalDateTime lastSyncedAt,
			List<EquipmentEntry> equippedItems,
			List<OwnedGearSection> ownedGearSections,
			List<TradeEntry> purchaseItems,
			List<TradeEntry> saleItems,
			List<TransactionEntry> transactionHistory,
			List<ActivityEntry> recentPosts,
			List<ActivityEntry> recentComments,
			ActivitySummary activitySummary) {
	}

	public record EquipmentEntry(
			String slotCode,
			String slotLabel,
			String itemName,
			String gradeName,
			String optionSummary,
			Double powerScore,
			String icon) {
	}

	public record OwnedGearSection(
			String slotCode,
			String slotLabel,
			String equippedItemName,
			int itemCount,
			List<OwnedGearEntry> items) {
	}

	public record OwnedGearEntry(
			Long itemId,
			String slotCode,
			String slotLabel,
			String itemName,
			String gradeName,
			Double powerScore,
			String icon,
			List<StatEntry> stats,
			String goldLabel,
			String sourceLabel,
			boolean equipped) {
	}

	public record StatEntry(
			String label,
			String value) {
	}

	public record TradeEntry(
			String itemName,
			String categoryLabel,
			String status,
			Integer price,
			LocalDateTime occurredAt,
			String icon) {
	}

	public record TransactionEntry(
			String role,
			String itemName,
			String partnerName,
			String status,
			Integer price,
			LocalDateTime occurredAt) {
	}

	public record ActivityEntry(
			String sectionLabel,
			String title,
			String detail,
			LocalDateTime occurredAt,
			String url) {
	}

	public record ActivitySummary(
			long postCount,
			long commentCount,
			long transactionCount) {
	}
}
