package com.mysite.sbb.trade.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.mysite.sbb.mypage.entity.EquipmentSlot;
import com.mysite.sbb.mypage.entity.Item;
import com.mysite.sbb.mypage.repository.ItemRepository;
import com.mysite.sbb.trade.entity.TradeCatalogItem;
import com.mysite.sbb.trade.entity.TradeItem;
import com.mysite.sbb.trade.repository.TradeCatalogItemRepository;
import com.mysite.sbb.trade.repository.TradeItemRepository;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class TradeCatalogSyncService {

	private static final List<String> SLOTTABLE_TYPES = List.of("Equip", "Accessory");

	private final TradeCatalogItemRepository tradeCatalogItemRepository;
	private final TradeItemRepository tradeItemRepository;
	private final ItemRepository itemRepository;

	@Transactional
	public SyncResult syncFromOfficialItems() {
		int normalizedCatalogCount = normalizeExistingCatalogItems();
		int normalizedTradeItemCount = normalizeExistingTradeItems();

		List<Item> sourceItems = this.itemRepository.findByTradableTrueAndTypeIn(SLOTTABLE_TYPES);
		if (sourceItems.isEmpty()) {
			return new SyncResult(0, 0, normalizedCatalogCount + normalizedTradeItemCount);
		}

		Map<String, Item> deduplicatedSource = new LinkedHashMap<>();
		sourceItems.stream()
				.filter(this::isEligibleTradeItem)
				.sorted(sourceComparator())
				.forEach(item -> deduplicatedSource.merge(tradeKey(item), item, this::preferSource));

		Map<String, TradeCatalogItem> existingCatalog = new LinkedHashMap<>();
		for (TradeCatalogItem catalogItem : this.tradeCatalogItemRepository.findAll()) {
			existingCatalog.putIfAbsent(key(catalogItem.getCategory(), catalogItem.getItemName()), catalogItem);
		}

		int nextDisplayOrder = this.tradeCatalogItemRepository.findTopByOrderByDisplayOrderDesc()
				.map(item -> item.getDisplayOrder() + 1)
				.orElse(1);

		List<TradeCatalogItem> newItems = new ArrayList<>();
		int updatedCount = 0;

		for (Item sourceItem : deduplicatedSource.values()) {
			String category = tradeCategory(sourceItem);
			String itemName = normalizedText(sourceItem.getName());
			String imageUrl = normalizedText(sourceItem.getIcon());
			String catalogKey = key(category, itemName);

			TradeCatalogItem existingItem = existingCatalog.get(catalogKey);
			if (existingItem != null) {
				if (!Objects.equals(existingItem.getCategory(), category)) {
					existingItem.setCategory(category);
					updatedCount++;
				}
				if (shouldRefreshImage(existingItem.getImageUrl(), imageUrl)) {
					existingItem.setImageUrl(imageUrl);
					updatedCount++;
				}
				continue;
			}

			TradeCatalogItem catalogItem = new TradeCatalogItem();
			catalogItem.setCategory(category);
			catalogItem.setItemName(itemName);
			catalogItem.setImageUrl(imageUrl);
			catalogItem.setDisplayOrder(nextDisplayOrder++);
			newItems.add(catalogItem);
			existingCatalog.put(catalogKey, catalogItem);
		}

		if (!newItems.isEmpty()) {
			this.tradeCatalogItemRepository.saveAll(newItems);
		}

		return new SyncResult(deduplicatedSource.size(), newItems.size(), updatedCount + normalizedCatalogCount + normalizedTradeItemCount);
	}

	private boolean isEligibleTradeItem(Item item) {
		EquipmentSlot slot = resolveTradeSlot(item).orElse(null);
		return item != null
				&& slot != null
				&& Boolean.TRUE.equals(item.getTradable())
				&& StringUtils.hasText(item.getName())
				&& StringUtils.hasText(slot.getPriorityGroup());
	}

	private Comparator<Item> sourceComparator() {
		return Comparator
				.comparingInt((Item item) -> resolveTradeSlot(item)
						.map(EquipmentSlot::getDisplayOrder)
						.orElse(999))
				.thenComparing(this::tradeCategory, String.CASE_INSENSITIVE_ORDER)
				.thenComparing(item -> normalizedText(item.getName()), String.CASE_INSENSITIVE_ORDER)
				.thenComparing((Item item) -> Boolean.TRUE.equals(item.getTradable()) ? 0 : 1)
				.thenComparing((Item item) -> item.getPowerScore() == null ? 0d : -item.getPowerScore())
				.thenComparing(Item::getId);
	}

	private Item preferSource(Item current, Item candidate) {
		if (current == null) {
			return candidate;
		}
		if (candidate == null) {
			return current;
		}

		boolean currentTradable = Boolean.TRUE.equals(current.getTradable());
		boolean candidateTradable = Boolean.TRUE.equals(candidate.getTradable());
		if (candidateTradable != currentTradable) {
			return candidateTradable ? candidate : current;
		}

		boolean currentHasIcon = StringUtils.hasText(current.getIcon());
		boolean candidateHasIcon = StringUtils.hasText(candidate.getIcon());
		if (candidateHasIcon != currentHasIcon) {
			return candidateHasIcon ? candidate : current;
		}

		double currentPower = current.getPowerScore() == null ? 0d : current.getPowerScore();
		double candidatePower = candidate.getPowerScore() == null ? 0d : candidate.getPowerScore();
		if (candidatePower != currentPower) {
			return candidatePower > currentPower ? candidate : current;
		}

		return candidate.getId() < current.getId() ? candidate : current;
	}

	private boolean shouldRefreshImage(String currentImageUrl, String candidateImageUrl) {
		if (!StringUtils.hasText(candidateImageUrl)) {
			return false;
		}
		if (!StringUtils.hasText(currentImageUrl)) {
			return true;
		}
		return currentImageUrl.startsWith("/images/");
	}

	private String tradeCategory(Item item) {
		return resolveTradeSlot(item)
				.map(EquipmentSlot::getPriorityGroup)
				.orElse(null);
	}

	private String tradeKey(Item item) {
		return key(tradeCategory(item), item.getName());
	}

	private Optional<EquipmentSlot> resolveTradeSlot(Item item) {
		if (item == null) {
			return Optional.empty();
		}
		return EquipmentSlot.infer(item.getCategoryName(), item.getEquipCategory())
				.or(() -> EquipmentSlot.fromCode(item.getSlotCode()));
	}

	private int normalizeExistingCatalogItems() {
		List<TradeCatalogItem> updates = new ArrayList<>();
		for (TradeCatalogItem catalogItem : this.tradeCatalogItemRepository.findAll()) {
			String normalizedCategory = normalizeTradeCategory(catalogItem.getCategory(), catalogItem.getItemName());
			if (!StringUtils.hasText(normalizedCategory)
					|| Objects.equals(normalizedCategory, normalizedText(catalogItem.getCategory()))) {
				continue;
			}
			catalogItem.setCategory(normalizedCategory);
			updates.add(catalogItem);
		}

		if (!updates.isEmpty()) {
			this.tradeCatalogItemRepository.saveAll(updates);
		}
		return updates.size();
	}

	private int normalizeExistingTradeItems() {
		List<TradeItem> updates = new ArrayList<>();
		for (TradeItem tradeItem : this.tradeItemRepository.findAll()) {
			String categorySource = tradeItem.getCatalogItem() != null
					? tradeItem.getCatalogItem().getCategory()
					: tradeItem.getCategory();
			String itemName = tradeItem.getCatalogItem() != null
					? tradeItem.getCatalogItem().getItemName()
					: tradeItem.getTitle();
			String normalizedCategory = normalizeTradeCategory(categorySource, itemName);
			if (!StringUtils.hasText(normalizedCategory)
					|| Objects.equals(normalizedCategory, normalizedText(tradeItem.getCategory()))) {
				continue;
			}
			tradeItem.setCategory(normalizedCategory);
			updates.add(tradeItem);
		}

		if (!updates.isEmpty()) {
			this.tradeItemRepository.saveAll(updates);
		}
		return updates.size();
	}

	private String normalizeTradeCategory(String category, String itemName) {
		EquipmentSlot slot = EquipmentSlot.infer(category, itemName)
				.orElse(null);
		if (slot != null) {
			return slot.getPriorityGroup();
		}

		String normalizedCategory = normalizedText(category);
		if (!StringUtils.hasText(normalizedCategory)) {
			return null;
		}

		int bracketIndex = normalizedCategory.indexOf('(');
		if (bracketIndex > 0) {
			normalizedCategory = normalizedCategory.substring(0, bracketIndex).trim();
		}
		return normalizedCategory;
	}

	private String key(String category, String itemName) {
		return normalizedText(category).toLowerCase(Locale.ROOT) + "::" + normalizedText(itemName).toLowerCase(Locale.ROOT);
	}

	private String normalizedText(String value) {
		return value == null ? "" : value.trim();
	}

	public record SyncResult(int distinctSourceCount, int insertedCount, int updatedCount) {
	}
}
