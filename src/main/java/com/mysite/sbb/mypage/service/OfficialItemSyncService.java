package com.mysite.sbb.mypage.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysite.sbb.mypage.entity.EquipmentSlot;
import com.mysite.sbb.mypage.entity.Item;
import com.mysite.sbb.mypage.repository.ItemRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Service
public class OfficialItemSyncService {

	private static final String LIST_ENDPOINT = "https://api-goats.plaync.com/aion2/v2.0/dict/search/item";
	private static final String DETAIL_ENDPOINT = "https://aion2.plaync.com/api/gameconst/item?id=%d&enchantLevel=0";
	private static final int PAGE_SIZE = 100;
	private static final int DETAIL_THREADS = 8;

	private final ItemRepository itemRepository;
	private final RecommendationService recommendationService;
	private final ObjectMapper objectMapper;

	@PersistenceContext
	private EntityManager entityManager;

	private final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.version(HttpClient.Version.HTTP_2)
			.build();

	public SyncResult syncAllItems() {
		LocalDateTime syncedAt = LocalDateTime.now();
		JsonNode firstPage = fetchJson(LIST_ENDPOINT + "?size=" + PAGE_SIZE + "&page=1");
		int total = firstPage.path("pagination").path("total").asInt(0);
		int lastPage = Math.max(1, (int) Math.ceil(total / (double) PAGE_SIZE));

		int requestedCount = 0;
		int savedCount = 0;
		int failedCount = 0;

		ExecutorService executor = Executors.newFixedThreadPool(DETAIL_THREADS);
		try {
			for (int page = 1; page <= lastPage; page++) {
				JsonNode pageNode = page == 1
						? firstPage
						: fetchJson(LIST_ENDPOINT + "?size=" + PAGE_SIZE + "&page=" + page);

				List<ListItemPayload> listItems = parseListItems(pageNode);
				requestedCount += listItems.size();
				List<Item> batch = fetchDetailBatch(listItems, syncedAt, executor);
				failedCount += Math.max(0, listItems.size() - batch.size());

				if (!batch.isEmpty()) {
					this.itemRepository.saveAll(batch);
					this.itemRepository.flush();
					this.entityManager.clear();
					savedCount += batch.size();
				}

				log.info("Official item sync page {}/{} saved {}/{} items", page, lastPage, batch.size(), listItems.size());
			}
		} finally {
			executor.shutdown();
			try {
				executor.awaitTermination(30, TimeUnit.SECONDS);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
			}
		}

		return new SyncResult(requestedCount, savedCount, failedCount, syncedAt);
	}

	private List<Item> fetchDetailBatch(List<ListItemPayload> listItems, LocalDateTime syncedAt, ExecutorService executor) {
		List<CompletableFuture<Item>> futures = listItems.stream()
				.map(listItem -> CompletableFuture.supplyAsync(() -> safeFetchItem(listItem, syncedAt), executor))
				.toList();

		List<Item> items = new ArrayList<>();
		for (CompletableFuture<Item> future : futures) {
			Item item = future.join();
			if (item != null) {
				items.add(item);
			}
		}
		return items;
	}

	private Item safeFetchItem(ListItemPayload listItem, LocalDateTime syncedAt) {
		try {
			JsonNode detail = fetchJson(DETAIL_ENDPOINT.formatted(listItem.id()));
			return mapItem(listItem, detail, syncedAt);
		} catch (Exception exception) {
			log.warn("Failed to sync item {}", listItem.id(), exception);
			return null;
		}
	}

	private Item mapItem(ListItemPayload listItem, JsonNode detail, LocalDateTime syncedAt) throws IOException {
		Item item = new Item();
		item.setId(listItem.id());
		item.setName(firstText(detail, listItem.name(), "name"));
		item.setIcon(firstText(detail, listItem.image(), "icon"));
		item.setGrade(firstText(detail, listItem.grade(), "grade"));
		item.setGradeName(firstText(detail, null, "gradeName"));
		item.setCategoryName(firstText(detail, listItem.categoryName(), "categoryName"));
		item.setEquipCategory(firstText(detail, null, "equipCategory"));
		item.setDescription(firstNonBlank(text(detail, "description"), text(detail, "desc")));
		item.setClassNamesJson(writeJson(detail.get("classNames")));
		item.setRaceName(firstText(detail, null, "raceName"));
		item.setEquipLevel(integerValue(detail.get("equipLevel")));
		item.setMainStatsJson(writeJson(detail.get("mainStats")));
		item.setSubStatsJson(writeJson(detail.get("subStats")));
		item.setSubSkillsJson(writeJson(detail.get("subSkills")));
		item.setEnchantLevel(integerValue(detail.get("enchantLevel")));
		item.setMaxEnchantLevel(integerValue(detail.get("maxEnchantLevel")));
		item.setSafeEnchantLevel(integerValue(detail.get("safeEnchantLevel")));
		item.setEnchantable(booleanValue(detail.get("enchantable")));
		item.setMagicStoneSlotCount(integerValue(detail.get("magicStoneSlotCount")));
		item.setGodStoneSlotCount(integerValue(detail.get("godStoneSlotCount")));
		item.setMagicStoneStatJson(writeJson(detail.get("magicStoneStat")));
		item.setGodStoneStatJson(writeJson(detail.get("godStoneStat")));
		item.setSetJson(writeJson(detail.get("set")));
		item.setSetItemJson(writeJson(detail.get("SetItem")));
		item.setTradable(booleanValueOrDefault(detail.get("tradable"), listItem.tradable()));
		item.setType(firstText(detail, null, "type"));
		item.setSlotCode(resolveSlotCode(item));
		item.setSyncedAt(syncedAt);
		item.setPowerScore(this.recommendationService.profileForItem(item).powerScore());
		return item;
	}

	private String resolveSlotCode(Item item) {
		if (!isSlottableType(item.getType())) {
			return null;
		}
		return EquipmentSlot.infer(item.getCategoryName(), item.getEquipCategory())
				.map(EquipmentSlot::getCode)
				.orElse(null);
	}

	private boolean isSlottableType(String type) {
		return "Equip".equalsIgnoreCase(type) || "Accessory".equalsIgnoreCase(type);
	}

	private List<ListItemPayload> parseListItems(JsonNode pageNode) {
		List<ListItemPayload> listItems = new ArrayList<>();
		JsonNode contents = pageNode.get("contents");
		if (contents == null || !contents.isArray()) {
			return listItems;
		}

		for (JsonNode node : contents) {
			long id = node.path("id").asLong(0L);
			if (id <= 0L) {
				continue;
			}
			listItems.add(new ListItemPayload(
					id,
					text(node, "name"),
					text(node, "image"),
					text(node, "grade"),
					text(node, "categoryName"),
					booleanValue(node.get("tradable"))));
		}
		return listItems;
	}

	private JsonNode fetchJson(String url) {
		Exception lastException = null;
		for (int attempt = 1; attempt <= 3; attempt++) {
			try {
				HttpRequest request = HttpRequest.newBuilder()
						.uri(URI.create(url))
						.timeout(Duration.ofSeconds(20))
						.header("Accept", "application/json")
						.GET()
						.build();

				HttpResponse<String> response = this.httpClient.send(request,
						HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
				if (response.statusCode() >= 200 && response.statusCode() < 300) {
					return this.objectMapper.readTree(response.body());
				}
				lastException = new IllegalStateException("Unexpected status " + response.statusCode() + " for " + url);
			} catch (Exception exception) {
				lastException = exception;
			}

			try {
				Thread.sleep(250L * attempt);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("Interrupted while syncing official items", exception);
			}
		}
		throw new IllegalStateException("Failed to fetch official item payload: " + url, lastException);
	}

	private String writeJson(JsonNode node) throws IOException {
		if (node == null || node.isNull()) {
			return null;
		}
		return this.objectMapper.writeValueAsString(node);
	}

	private String firstText(JsonNode node, String fallback, String fieldName) {
		String value = text(node, fieldName);
		return firstNonBlank(value, fallback);
	}

	private String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

	private String text(JsonNode node, String fieldName) {
		if (node == null) {
			return null;
		}
		JsonNode value = node.get(fieldName);
		return value == null || value.isNull() ? null : value.asText();
	}

	private Integer integerValue(JsonNode node) {
		if (node == null || node.isNull()) {
			return null;
		}
		if (node.isInt() || node.isLong()) {
			return node.asInt();
		}
		String value = node.asText();
		if (value == null || value.isBlank()) {
			return null;
		}
		return Integer.valueOf(value);
	}

	private Boolean booleanValue(JsonNode node) {
		if (node == null || node.isNull()) {
			return null;
		}
		return node.asBoolean();
	}

	private Boolean booleanValueOrDefault(JsonNode node, Boolean fallback) {
		Boolean value = booleanValue(node);
		return value != null ? value : fallback;
	}

	public record SyncResult(int requestedCount, int savedCount, int failedCount, LocalDateTime syncedAt) {
	}

	private record ListItemPayload(
			long id,
			String name,
			String image,
			String grade,
			String categoryName,
			Boolean tradable) {
	}
}
