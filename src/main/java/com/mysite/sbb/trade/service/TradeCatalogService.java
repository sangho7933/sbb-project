package com.mysite.sbb.trade.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mysite.sbb.DataNotFoundException;
import com.mysite.sbb.trade.entity.TradeCatalogItem;
import com.mysite.sbb.trade.repository.TradeCatalogItemRepository;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class TradeCatalogService {

	private static final List<String> CATEGORY_ORDER = List.of("무기", "가더", "장신구", "방어구");

	private final TradeCatalogItemRepository tradeCatalogItemRepository;

	public List<String> getCategories() {
		return this.tradeCatalogItemRepository.findDistinctCategories().stream()
				.sorted(categoryComparator())
				.toList();
	}

	public List<TradeCatalogItem> getCatalogItems() {
		return this.tradeCatalogItemRepository.findAll().stream()
				.sorted(Comparator
						.comparing(TradeCatalogItem::getCategory, categoryComparator())
						.thenComparing(item -> item.getDisplayOrder() == null ? Integer.MAX_VALUE : item.getDisplayOrder())
						.thenComparing(TradeCatalogItem::getItemName, String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	public TradeCatalogItem getCatalogItem(Long id) {
		return this.tradeCatalogItemRepository.findById(id)
				.orElseThrow(() -> new DataNotFoundException("아이템 카탈로그 정보를 찾을 수 없습니다."));
	}

	private Comparator<String> categoryComparator() {
		return Comparator
				.comparingInt(this::categoryOrder)
				.thenComparing(category -> category == null ? "" : category, String.CASE_INSENSITIVE_ORDER);
	}

	private int categoryOrder(String category) {
		int index = CATEGORY_ORDER.indexOf(category);
		return index >= 0 ? index : CATEGORY_ORDER.size();
	}
}
