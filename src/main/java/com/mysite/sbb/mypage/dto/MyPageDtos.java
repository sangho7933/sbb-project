package com.mysite.sbb.mypage.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class MyPageDtos {

	private MyPageDtos() {
	}

	public record ItemStatChipDto(String label, String value) {
	}

	public record ItemCardResponse(
			Long id,
			String slotCode,
			String slotLabel,
			String name,
			String icon,
			String gradeName,
			String gradeKey,
			String categoryName,
			String description,
			String raceName,
			Integer equipLevel,
			Double powerScore,
			List<ItemStatChipDto> statChips,
			boolean owned,
			boolean saved) {
	}

	public record MyPageUserResponse(Long id, String username, Integer gold, String race) {
	}

	public record SlotSummaryResponse(
			String slotCode,
			String slotLabel,
			int displayOrder,
			long totalItemCount,
			long ownedItemCount,
			Double bestOverallPowerScore,
			Double bestOwnedPowerScore,
			ItemCardResponse savedItem) {
	}

	public record SimulatorSlotResponse(
			String slotCode,
			String slotLabel,
			ItemCardResponse equippedItem) {
	}

	public record PurchaseVaultItemResponse(
			String name,
			String icon,
			String categoryLabel,
			Integer price,
			LocalDateTime purchasedAt,
			String slotCode,
			String slotLabel,
			String linkedItemName,
			String raceName,
			Long linkedItemId,
			boolean linked) {
	}

	public record UserStatResponse(
			Double totalAttackMin,
			Double totalAttackMax,
			Double totalDefense,
			Double totalAccuracy,
			Double totalCritical,
			Double totalHealth,
			Double totalMagicBoost,
			Double totalMagicAccuracy,
			Double totalPveAttack,
			Double totalHealingBoost,
			Double powerScore,
			Map<String, Double> summary) {
	}

	public record SyncStatusResponse(
			long totalItems,
			long equippableItems,
			LocalDateTime lastSyncedAt) {
	}

	public record MyPageDashboardResponse(
			MyPageUserResponse user,
			List<SlotSummaryResponse> slots,
			List<SimulatorSlotResponse> savedEquipment,
			List<PurchaseVaultItemResponse> purchaseVault,
			SyncStatusResponse syncStatus,
			String defaultSlotCode,
			boolean admin) {
	}

	public record ItemCatalogResponse(
			String slotCode,
			String slotLabel,
			boolean ownedOnly,
			String keyword,
			String emptyMessage,
			long ownedPurchaseCount,
			List<ItemCardResponse> items) {
	}

	public record SimulatorStateRequest(Map<String, Long> equippedItemIds) {
	}

	public record SimulatorResponse(
			List<SimulatorSlotResponse> equippedSlots,
			UserStatResponse totalStats) {
	}

	public record GrowthPriorityResponse(
			String slotCode,
			String slotLabel,
			String priorityGroup,
			String currentItemIcon,
			String currentItemName,
			String targetItemIcon,
			String targetItemName,
			String targetScope,
			String headline,
			String description,
			Double gapPowerScore,
			Double currentPowerScore,
			Double benchmarkPowerScore,
			List<ItemStatChipDto> deltaHighlights,
			Double recommendationScore,
			boolean ownedUpgradeAvailable,
			boolean emptySlot) {
	}

	public record GrowthRecommendationResponse(List<GrowthPriorityResponse> priorities) {
	}

	public record CatalogSyncResponse(
			int requestedCount,
			int savedCount,
			int failedCount,
			LocalDateTime syncedAt) {
	}
}
