package com.mysite.sbb.trade.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mysite.sbb.trade.entity.TradeCatalogItem;
import com.mysite.sbb.trade.repository.TradeCatalogItemRepository;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Component
public class TradeCatalogInitializer implements ApplicationRunner {

	private static final List<SeedCatalogItem> ADDITIONAL_CATALOG_ITEMS = List.of(
			new SeedCatalogItem("무기", "초승달 치유 지팡이", "/images/법봉1.png"),
			new SeedCatalogItem("무기", "보랏빛 사명 지팡이", "/images/법봉2.png"),
			new SeedCatalogItem("무기", "찬란한 구원 지팡이", "/images/법봉3.png"),
			new SeedCatalogItem("방어구", "수호자 투구", null),
			new SeedCatalogItem("방어구", "성전사 투구", null),
			new SeedCatalogItem("방어구", "빛의 투구", null),
			new SeedCatalogItem("방어구", "정화의 갑옷", null),
			new SeedCatalogItem("방어구", "수호자 갑옷", null),
			new SeedCatalogItem("방어구", "강철의 갑옷", null),
			new SeedCatalogItem("방어구", "민첩의 신발", null),
			new SeedCatalogItem("방어구", "수호자 신발", null),
			new SeedCatalogItem("방어구", "생명의 신발", null),
			new SeedCatalogItem("장신구", "봉인의 반지", null),
			new SeedCatalogItem("장신구", "결의의 반지", null),
			new SeedCatalogItem("장신구", "광휘의 반지", null),
			new SeedCatalogItem("방어구", "숙련 장갑", null),
			new SeedCatalogItem("방어구", "빙결 장갑", null),
			new SeedCatalogItem("방어구", "대천사의 장갑", null),
			new SeedCatalogItem("장신구", "기원의 목걸이", null),
			new SeedCatalogItem("장신구", "빛샘 목걸이", null),
			new SeedCatalogItem("장신구", "천공의 목걸이", null),
			new SeedCatalogItem("방어구", "방패 견갑", null),
			new SeedCatalogItem("방어구", "성령 견갑", null),
			new SeedCatalogItem("방어구", "영웅 견갑", null));

	private final TradeCatalogItemRepository tradeCatalogItemRepository;
	private final TradeCatalogSyncService tradeCatalogSyncService;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (this.tradeCatalogItemRepository.count() == 0) {
			seedLegacyCatalog();
		}
		appendAdditionalCatalogItems();
		this.tradeCatalogSyncService.syncFromOfficialItems();
	}

	private void seedLegacyCatalog() {
		List<TradeCatalogItem> catalogItems = new ArrayList<>();
		String[] categories = { "가더", "단검", "대검", "법봉", "법서", "보주", "장검", "전곤", "활" };
		int displayOrder = 1;
		for (String category : categories) {
			for (int i = 1; i <= 8; i++) {
				TradeCatalogItem catalogItem = new TradeCatalogItem();
				catalogItem.setCategory(category);
				catalogItem.setItemName(category + " " + i);
				catalogItem.setImageUrl("/images/" + category + i + ".png");
				catalogItem.setDisplayOrder(displayOrder++);
				catalogItems.add(catalogItem);
			}
		}
		this.tradeCatalogItemRepository.saveAll(catalogItems);
	}

	private void appendAdditionalCatalogItems() {
		int displayOrder = this.tradeCatalogItemRepository.findTopByOrderByDisplayOrderDesc()
				.map(item -> item.getDisplayOrder() + 1)
				.orElse(1);

		List<TradeCatalogItem> missingItems = new ArrayList<>();
		for (SeedCatalogItem seedItem : ADDITIONAL_CATALOG_ITEMS) {
			if (this.tradeCatalogItemRepository.existsByCategoryAndItemName(seedItem.category(), seedItem.itemName())) {
				continue;
			}

			TradeCatalogItem catalogItem = new TradeCatalogItem();
			catalogItem.setCategory(seedItem.category());
			catalogItem.setItemName(seedItem.itemName());
			catalogItem.setImageUrl(seedItem.imageUrl());
			catalogItem.setDisplayOrder(displayOrder++);
			missingItems.add(catalogItem);
		}

		if (!missingItems.isEmpty()) {
			this.tradeCatalogItemRepository.saveAll(missingItems);
		}
	}

	private record SeedCatalogItem(String category, String itemName, String imageUrl) {
	}
}
