package com.mysite.sbb.mypage.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysite.sbb.DataNotFoundException;
import com.mysite.sbb.board.BoardPost;
import com.mysite.sbb.board.BoardPostRepository;
import com.mysite.sbb.comment.BoardComment;
import com.mysite.sbb.comment.BoardCommentRepository;
import com.mysite.sbb.mypage.dto.MyPageDtos;
import com.mysite.sbb.mypage.dto.MyPageView;
import com.mysite.sbb.mypage.entity.EquipmentSlot;
import com.mysite.sbb.mypage.entity.Item;
import com.mysite.sbb.mypage.entity.UserEquipment;
import com.mysite.sbb.mypage.repository.ItemRepository;
import com.mysite.sbb.mypage.repository.UserEquipmentRepository;
import com.mysite.sbb.skilltree.SkillTreePost;
import com.mysite.sbb.skilltree.SkillTreePostRepository;
import com.mysite.sbb.skilltree.comment.SkillTreeComment;
import com.mysite.sbb.skilltree.comment.SkillTreeCommentRepository;
import com.mysite.sbb.trade.entity.TradeCatalogItem;
import com.mysite.sbb.trade.entity.TradeItem;
import com.mysite.sbb.trade.entity.TradeTransaction;
import com.mysite.sbb.trade.repository.TradeItemRepository;
import com.mysite.sbb.trade.repository.TradeTransactionRepository;
import com.mysite.sbb.user.SiteUser;
import com.mysite.sbb.user.UserRace;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class MyPageService {

	private static final String DEFAULT_SLOT_CODE = EquipmentSlot.WEAPON.getCode();
	private static final List<String> SLOTTABLE_TYPES = List.of("Equip", "Accessory");
	private static final Pattern TRAILING_NUMBER_PATTERN = Pattern.compile("(\\d+)$");
	private static final int LEGACY_TIER_MAX = 8;
	private static final int MAX_GROWTH_STEPS = 5;
	private static final double TARGET_GROWTH_POWER_DELTA = 500d;

	private final ItemRepository itemRepository;
	private final UserEquipmentRepository userEquipmentRepository;
	private final TradeTransactionRepository tradeTransactionRepository;
	private final TradeItemRepository tradeItemRepository;
	private final BoardPostRepository boardPostRepository;
	private final BoardCommentRepository boardCommentRepository;
	private final SkillTreePostRepository skillTreePostRepository;
	private final SkillTreeCommentRepository skillTreeCommentRepository;
	private final RecommendationService recommendationService;
	private final OfficialItemSyncService officialItemSyncService;
	private final ObjectMapper objectMapper;

	private volatile boolean slotAssignmentsChecked;

	@Transactional
	public MyPageDtos.MyPageDashboardResponse getDashboard(SiteUser user) {
		prepareMyPageData(user);

		Map<String, Item> savedEquipmentBySlot = getSavedEquipmentBySlot(user);
		OwnedItemContext ownedItemContext = getOwnedItemContext(user);

		List<MyPageDtos.SlotSummaryResponse> slots = EquipmentSlot.orderedValues().stream()
				.map(slot -> buildSlotSummary(slot, savedEquipmentBySlot.get(slot.getCode()), ownedItemContext, user))
				.toList();

		List<MyPageDtos.SimulatorSlotResponse> savedEquipment = EquipmentSlot.orderedValues().stream()
				.map(slot -> new MyPageDtos.SimulatorSlotResponse(
						slot.getCode(),
						slot.getLabel(),
						toItemCardResponse(savedEquipmentBySlot.get(slot.getCode()),
								isOwnedItem(savedEquipmentBySlot.get(slot.getCode()), ownedItemContext),
								true)))
				.toList();

		return new MyPageDtos.MyPageDashboardResponse(
				new MyPageDtos.MyPageUserResponse(user.getId(), user.getUsername(), user.getGold(), userRaceLabel(user)),
				slots,
				savedEquipment,
				ownedItemContext.purchaseVault(),
				new MyPageDtos.SyncStatusResponse(
						this.itemRepository.count(),
						this.itemRepository.countBySlotCodeIsNotNull(),
						this.itemRepository.findLastSyncedAt()),
				DEFAULT_SLOT_CODE,
				"admin".equalsIgnoreCase(user.getUsername()));
	}

	@Transactional
	public MyPageView.Overview getOverview(SiteUser user) {
		prepareMyPageData(user);

		Map<String, Item> savedEquipmentBySlot = getSavedEquipmentBySlot(user);
		OwnedItemContext ownedItemContext = getOwnedItemContext(user);
		List<UserEquipment> savedEquipmentRows = this.userEquipmentRepository.findByUserOrderBySlotCodeAsc(user);
		List<TradeTransaction> purchaseHistory = this.tradeTransactionRepository.findPurchaseHistoryByBuyerUserId(user.getId());
		List<TradeTransaction> salesHistory = this.tradeTransactionRepository.findSalesHistoryBySellerUserId(user.getId());
		List<TradeTransaction> relatedHistory = this.tradeTransactionRepository.findRelatedHistoryByUserId(user.getId());

		long boardPostCount = this.boardPostRepository.countByAuthor(user);
		long skillTreePostCount = this.skillTreePostRepository.countByAuthor(user);
		long boardCommentCount = this.boardCommentRepository.countByAuthor(user);
		long skillTreeCommentCount = this.skillTreeCommentRepository.countByAuthor(user);

		long postCount = boardPostCount + skillTreePostCount;
		long commentCount = boardCommentCount + skillTreeCommentCount;
		long transactionCount = relatedHistory.size();
		long presetCount = savedEquipmentRows.isEmpty() ? 0L : 1L;
		long equippedCount = savedEquipmentBySlot.size();
		LocalDateTime lastEquipmentUpdatedAt = savedEquipmentRows.stream()
				.map(UserEquipment::getUpdatedAt)
				.filter(Objects::nonNull)
				.max(LocalDateTime::compareTo)
				.orElse(null);

		List<MyPageView.EquipmentEntry> equippedItems = EquipmentSlot.orderedValues().stream()
				.map(slot -> toEquipmentEntry(slot, savedEquipmentBySlot.get(slot.getCode())))
				.filter(Objects::nonNull)
				.toList();

		List<MyPageView.OwnedGearSection> ownedGearSections = buildOwnedGearSections(
				user,
				savedEquipmentBySlot,
				ownedItemContext);

		List<MyPageView.TradeEntry> purchaseItems = purchaseHistory.stream()
				.limit(10)
				.map(this::toPurchaseEntry)
				.filter(Objects::nonNull)
				.toList();

		List<MyPageView.TradeEntry> saleItems = buildSaleEntries(user);

		List<MyPageView.TransactionEntry> transactionHistory = relatedHistory.stream()
				.limit(10)
				.map(transaction -> toTransactionEntry(user, transaction))
				.filter(Objects::nonNull)
				.toList();

		List<MyPageView.ActivityEntry> recentPosts = buildRecentPostEntries(user);
		List<MyPageView.ActivityEntry> recentComments = buildRecentCommentEntries(user);

		return new MyPageView.Overview(
				user.getUsername(),
				user.getEmail(),
				userRaceLabel(user),
				user.getGold(),
				deriveRepresentativeLabel(savedEquipmentBySlot),
				buildActivityLabel(postCount, commentCount, transactionCount),
				presetCount,
				equippedCount,
				lastEquipmentUpdatedAt,
				this.itemRepository.findLastSyncedAt(),
				equippedItems,
				ownedGearSections,
				purchaseItems,
				saleItems,
				transactionHistory,
				recentPosts,
				recentComments,
				new MyPageView.ActivitySummary(postCount, commentCount, transactionCount));
	}

	@Transactional
	public String equipOwnedItem(SiteUser user, Long itemId) {
		prepareMyPageData(user);

		if (itemId == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "장착할 장비를 선택해 주세요.");
		}

		Item item = this.itemRepository.findById(itemId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "장비 정보를 찾을 수 없습니다."));
		if (!StringUtils.hasText(item.getSlotCode())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "장착할 수 없는 장비입니다.");
		}
		if (!isRaceAllowed(item, user)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "현재 종족으로는 장착할 수 없는 장비입니다.");
		}

		OwnedItemContext ownedItemContext = getOwnedItemContext(user);
		if (!isOwnedItem(item, ownedItemContext)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "보유 중인 장비만 장착할 수 있습니다.");
		}

		EquipmentSlot slot = resolveSlot(item.getSlotCode());
		UserEquipment userEquipment = this.userEquipmentRepository.findByUserAndSlotCode(user, slot.getCode())
				.orElseGet(() -> {
					UserEquipment created = new UserEquipment();
					created.setUser(user);
					created.setSlotCode(slot.getCode());
					return created;
				});

		if (userEquipment.getItem() != null && Objects.equals(userEquipment.getItem().getId(), item.getId())) {
			return slot.getLabel() + " 슬롯은 이미 " + item.getName() + " 장비를 사용 중입니다.";
		}

		userEquipment.setItem(item);
		userEquipment.setUpdatedAt(LocalDateTime.now());
		this.userEquipmentRepository.save(userEquipment);
		return slot.getLabel() + " 슬롯 장비를 " + item.getName() + "(으)로 변경했습니다.";
	}

	@Transactional
	public MyPageDtos.ItemCatalogResponse getSlotCatalog(SiteUser user, String slotCode, boolean ownedOnly, String keyword) {
		prepareMyPageData(user);

		EquipmentSlot slot = resolveSlot(slotCode);
		OwnedItemContext ownedItemContext = getOwnedItemContext(user);
		String normalizedKeyword = keyword == null ? "" : keyword.trim();

		List<Item> items = StringUtils.hasText(normalizedKeyword)
				? this.itemRepository.findBySlotCodeAndNameContainingIgnoreCaseOrderByPowerScoreDescNameAsc(slot.getCode(),
						normalizedKeyword)
				: this.itemRepository.findBySlotCodeOrderByPowerScoreDescNameAsc(slot.getCode());

		List<MyPageDtos.ItemCardResponse> responses = items.stream()
				.filter(item -> isRaceAllowed(item, user))
				.filter(item -> !ownedOnly || isOwnedItem(item, ownedItemContext))
				.map(item -> toItemCardResponse(item, isOwnedItem(item, ownedItemContext), false))
				.toList();

		return new MyPageDtos.ItemCatalogResponse(
				slot.getCode(),
				slot.getLabel(),
				ownedOnly,
				normalizedKeyword,
				buildCatalogEmptyMessage(slot, ownedOnly, normalizedKeyword, ownedItemContext, responses),
				ownedItemContext.purchaseVault().size(),
				responses);
	}

	@Transactional
	public MyPageDtos.SimulatorResponse simulate(SiteUser user, MyPageDtos.SimulatorStateRequest request) {
		prepareMyPageData(user);

		Map<String, Item> equippedBySlot = resolveSimulationItems(user, request);
		OwnedItemContext ownedItemContext = getOwnedItemContext(user);

		return new MyPageDtos.SimulatorResponse(
				buildSimulatorSlots(equippedBySlot, ownedItemContext),
				toUserStatResponse(this.recommendationService.calculateTotals(equippedBySlot.values())));
	}

	@Transactional
	public MyPageDtos.GrowthRecommendationResponse recommendGrowth(
			SiteUser user,
			MyPageDtos.SimulatorStateRequest request) {
		prepareMyPageData(user);

		Map<String, Item> equippedBySlot = resolveSimulationItems(user, request);
		OwnedItemContext ownedItemContext = getOwnedItemContext(user);
		List<PriorityCandidate> candidates = buildStepwisePriorityCandidates(
				new LinkedHashMap<>(equippedBySlot),
				ownedItemContext,
				user);

		List<MyPageDtos.GrowthPriorityResponse> priorities = new ArrayList<>();
		for (PriorityCandidate candidate : candidates) {
			priorities.add(new MyPageDtos.GrowthPriorityResponse(
					candidate.slot().getCode(),
					candidate.slot().getLabel(),
					candidate.slot().getPriorityGroup(),
					normalizedText(candidate.currentItem() != null ? candidate.currentItem().getIcon() : ""),
					currentItemName(candidate),
					normalizedText(candidate.targetItem() != null ? candidate.targetItem().getIcon() : ""),
					targetItemName(candidate),
					candidate.ownedUpgradeAvailable() ? "보유 아이템 기준" : "전체 DB 기준",
					formatPriorityHeadline(candidate.stepOrder(), candidate),
					formatPriorityDescription(candidate),
					round(candidate.gapPowerScore()),
					round(candidate.currentPowerScore()),
					round(candidate.targetPowerScore()),
					buildDeltaHighlights(candidate.delta()),
					round(candidate.recommendationScore()),
					candidate.ownedUpgradeAvailable(),
					candidate.currentItem() == null));
		}

		return new MyPageDtos.GrowthRecommendationResponse(priorities);
	}

	private List<PriorityCandidate> buildStepwisePriorityCandidates(
			Map<String, Item> equippedBySlot,
			OwnedItemContext ownedItemContext,
			SiteUser user) {
		List<PriorityCandidate> candidates = new ArrayList<>();
		Map<String, List<Item>> slotItemsCache = new LinkedHashMap<>();

		for (int stepOrder = 1; stepOrder <= MAX_GROWTH_STEPS; stepOrder++) {
			final int currentStepOrder = stepOrder;
			PriorityCandidate nextCandidate = EquipmentSlot.orderedValues().stream()
					.map(slot -> buildPriorityCandidate(
							currentStepOrder,
							slot,
							equippedBySlot.get(slot.getCode()),
							ownedItemContext,
							user,
							slotItemsCache))
					.filter(PriorityCandidate::hasTargetUpgrade)
					.min(growthPriorityComparator())
					.orElse(null);

			if (nextCandidate == null) {
				break;
			}

			candidates.add(nextCandidate);
			equippedBySlot.put(nextCandidate.slot().getCode(), nextCandidate.targetItem());
		}

		return candidates;
	}

	@Transactional
	public MyPageDtos.CatalogSyncResponse syncOfficialCatalog() {
		OfficialItemSyncService.SyncResult result = this.officialItemSyncService.syncAllItems();
		this.slotAssignmentsChecked = false;
		return new MyPageDtos.CatalogSyncResponse(
				result.requestedCount(),
				result.savedCount(),
				result.failedCount(),
				result.syncedAt());
	}

	private void prepareMyPageData(SiteUser user) {
		ensureSlotAssignmentsReady();
		ensureUserEquipmentReady(user);
	}

	private synchronized void ensureSlotAssignmentsReady() {
		if (this.slotAssignmentsChecked) {
			return;
		}

		List<Item> candidates = this.itemRepository.findByTypeIn(SLOTTABLE_TYPES);
		List<Item> updates = new ArrayList<>();

		for (Item item : candidates) {
			String resolvedSlotCode = resolveSlotCode(item);
			if (!Objects.equals(item.getSlotCode(), resolvedSlotCode)) {
				item.setSlotCode(resolvedSlotCode);
				updates.add(item);
			}
		}

		if (!updates.isEmpty()) {
			this.itemRepository.saveAll(updates);
		}

		this.slotAssignmentsChecked = true;
	}

	private MyPageDtos.SlotSummaryResponse buildSlotSummary(
			EquipmentSlot slot,
			Item savedItem,
			OwnedItemContext ownedItemContext,
			SiteUser user) {
		List<Item> slotItems = allowedSlotItemsForUser(user, slot);
		List<Item> ownedItems = slotItems.stream()
				.filter(item -> isOwnedItem(item, ownedItemContext))
				.toList();

		return new MyPageDtos.SlotSummaryResponse(
				slot.getCode(),
				slot.getLabel(),
				slot.getDisplayOrder(),
				slotItems.size(),
				ownedItems.size(),
				bestPower(slotItems),
				bestPower(ownedItems),
				toItemCardResponse(savedItem, isOwnedItem(savedItem, ownedItemContext), true));
	}

	private List<MyPageDtos.SimulatorSlotResponse> buildSimulatorSlots(
			Map<String, Item> equippedBySlot,
			OwnedItemContext ownedItemContext) {
		return EquipmentSlot.orderedValues().stream()
				.map(slot -> new MyPageDtos.SimulatorSlotResponse(
						slot.getCode(),
						slot.getLabel(),
						toItemCardResponse(equippedBySlot.get(slot.getCode()),
								isOwnedItem(equippedBySlot.get(slot.getCode()), ownedItemContext),
								false)))
				.toList();
	}

	private MyPageView.EquipmentEntry toEquipmentEntry(EquipmentSlot slot, Item item) {
		if (item == null) {
			return null;
		}

		String optionSummary = buildOptionSummary(item);
		return new MyPageView.EquipmentEntry(
				slot.getCode(),
				slot.getLabel(),
				item.getName(),
				firstText(item.getGradeName(), item.getGrade(), "일반"),
				optionSummary,
				roundedPower(item),
				item.getIcon());
	}

	private List<MyPageView.OwnedGearSection> buildOwnedGearSections(
			SiteUser user,
			Map<String, Item> savedEquipmentBySlot,
			OwnedItemContext ownedItemContext) {
		Map<Long, OwnedPurchaseMeta> purchaseMetaByItemId = buildOwnedPurchaseMeta(ownedItemContext.purchaseVault());

		return EquipmentSlot.orderedValues().stream()
				.map(slot -> buildOwnedGearSection(
						slot,
						user,
						savedEquipmentBySlot.get(slot.getCode()),
						ownedItemContext,
						purchaseMetaByItemId))
				.filter(section -> !section.items().isEmpty())
				.toList();
	}

	private MyPageView.OwnedGearSection buildOwnedGearSection(
			EquipmentSlot slot,
			SiteUser user,
			Item equippedItem,
			OwnedItemContext ownedItemContext,
			Map<Long, OwnedPurchaseMeta> purchaseMetaByItemId) {
		List<MyPageView.OwnedGearEntry> items = allowedSlotItemsForUser(user, slot).stream()
				.filter(item -> isOwnedItem(item, ownedItemContext))
				.map(item -> toOwnedGearEntry(slot, item, equippedItem, purchaseMetaByItemId.get(item.getId())))
				.sorted(ownedGearEntryComparator())
				.toList();

		return new MyPageView.OwnedGearSection(
				slot.getCode(),
				slot.getLabel(),
				equippedItem != null ? equippedItem.getName() : null,
				items.size(),
				items);
	}

	private Map<Long, OwnedPurchaseMeta> buildOwnedPurchaseMeta(
			List<MyPageDtos.PurchaseVaultItemResponse> purchaseVault) {
		Map<Long, OwnedPurchaseMeta> purchaseMetaByItemId = new LinkedHashMap<>();

		for (MyPageDtos.PurchaseVaultItemResponse purchaseItem : purchaseVault) {
			if (purchaseItem == null || !purchaseItem.linked() || purchaseItem.linkedItemId() == null) {
				continue;
			}
			purchaseMetaByItemId.merge(
					purchaseItem.linkedItemId(),
					new OwnedPurchaseMeta("구매", purchaseItem.price(), purchaseItem.purchasedAt()),
					this::preferOwnedPurchaseMeta);
		}

		return purchaseMetaByItemId;
	}

	private OwnedPurchaseMeta preferOwnedPurchaseMeta(OwnedPurchaseMeta current, OwnedPurchaseMeta candidate) {
		if (current == null) {
			return candidate;
		}
		if (candidate == null) {
			return current;
		}
		return compareNullableDate(candidate.purchasedAt(), current.purchasedAt()) >= 0 ? candidate : current;
	}

	private MyPageView.OwnedGearEntry toOwnedGearEntry(
			EquipmentSlot slot,
			Item item,
			Item equippedItem,
			OwnedPurchaseMeta purchaseMeta) {
		boolean equipped = equippedItem != null && Objects.equals(equippedItem.getId(), item.getId());
		String gradeName = firstText(item.getGradeName(), item.getGrade(), "일반");
		String sourceLabel = purchaseMeta != null ? purchaseMeta.sourceLabel() : "기본";
		String goldLabel = purchaseMeta != null && purchaseMeta.price() != null
				? formatGold(purchaseMeta.price())
				: "기본 지급";

		return new MyPageView.OwnedGearEntry(
				item.getId(),
				slot.getCode(),
				slot.getLabel(),
				item.getName(),
				gradeName,
				roundedPower(item),
				normalizedText(item.getIcon()),
				buildViewStatEntries(item),
				goldLabel,
				sourceLabel,
				equipped);
	}

	private List<MyPageView.StatEntry> buildViewStatEntries(Item item) {
		return buildStatChips(item).stream()
				.map(chip -> new MyPageView.StatEntry(chip.label(), chip.value()))
				.toList();
	}

	private MyPageView.TradeEntry toPurchaseEntry(TradeTransaction transaction) {
		TradeItem tradeItem = transaction.getItem();
		if (tradeItem == null) {
			return null;
		}

		return new MyPageView.TradeEntry(
				tradeItemName(tradeItem),
				tradeCategoryLabel(tradeItem),
				normalizedText(transaction.getStatus()),
				transaction.getPrice(),
				transaction.getCreatedAt(),
				tradeImageUrl(tradeItem));
	}

	private List<MyPageView.TradeEntry> buildSaleEntries(SiteUser user) {
		return this.tradeItemRepository.findTop10BySeller_UserIdAndHiddenFalseOrderByIdDesc(user.getId()).stream()
				.map(this::toSaleEntry)
				.toList();
	}

	private MyPageView.TransactionEntry toTransactionEntry(SiteUser user, TradeTransaction transaction) {
		TradeItem tradeItem = transaction.getItem();
		if (tradeItem == null) {
			return null;
		}

		boolean buyer = transaction.getBuyer() != null && Objects.equals(transaction.getBuyer().getUserId(), user.getId());
		String role = buyer ? "구매" : "판매";
		String partnerName = buyer
				? normalizedText(transaction.getSeller() != null ? transaction.getSeller().getUsername() : "")
				: normalizedText(transaction.getBuyer() != null ? transaction.getBuyer().getUsername() : "");
		return new MyPageView.TransactionEntry(
				role,
				tradeItemName(tradeItem),
				partnerName,
				normalizedText(transaction.getStatus()),
				transaction.getPrice(),
				transaction.getCreatedAt());
	}

	private List<MyPageView.ActivityEntry> buildRecentPostEntries(SiteUser user) {
		List<MyPageView.ActivityEntry> entries = new ArrayList<>();

		for (BoardPost post : this.boardPostRepository.findTop5ByAuthorOrderByCreateDateDesc(user)) {
			entries.add(new MyPageView.ActivityEntry(
					"게시판 글",
					normalizedText(post.getSubject()),
					normalizedText(post.getCategory() != null ? post.getCategory().getLabel() : "게시판"),
					post.getCreateDate(),
					buildBoardPostUrl(post)));
		}

		for (SkillTreePost post : this.skillTreePostRepository.findTop5ByAuthorOrderByCreateDateDesc(user)) {
			entries.add(new MyPageView.ActivityEntry(
					"스킬트리 글",
					normalizedText(post.getSubject()),
					normalizedText(post.getJob() != null ? post.getJob().getLabel() : "스킬트리"),
					post.getCreateDate(),
					buildSkillTreePostUrl(post)));
		}

		return sortRecentActivities(entries);
	}

	private List<MyPageView.ActivityEntry> buildRecentCommentEntries(SiteUser user) {
		List<MyPageView.ActivityEntry> entries = new ArrayList<>();

		for (BoardComment comment : this.boardCommentRepository.findTop5ByAuthorOrderByCreateDateDesc(user)) {
			BoardPost post = comment.getPost();
			entries.add(new MyPageView.ActivityEntry(
					"게시판 댓글",
					shortText(stripTags(comment.getContent()), 70),
					normalizedText(post != null && post.getSubject() != null ? post.getSubject() : "원문 없음"),
					comment.getCreateDate(),
					buildBoardPostUrl(post)));
		}

		for (SkillTreeComment comment : this.skillTreeCommentRepository.findTop5ByAuthorOrderByCreateDateDesc(user)) {
			SkillTreePost post = comment.getPost();
			entries.add(new MyPageView.ActivityEntry(
					"스킬트리 댓글",
					shortText(stripTags(comment.getContent()), 70),
					normalizedText(post != null && post.getSubject() != null ? post.getSubject() : "원문 없음"),
					comment.getCreateDate(),
					buildSkillTreePostUrl(post)));
		}

		return sortRecentActivities(entries);
	}

	private String deriveRepresentativeLabel(Map<String, Item> savedEquipmentBySlot) {
		Item weapon = savedEquipmentBySlot.get(EquipmentSlot.WEAPON.getCode());
		if (weapon != null && StringUtils.hasText(weapon.getCategoryName())) {
			return normalizedCategoryLabel(weapon.getCategoryName());
		}
		Item firstItem = savedEquipmentBySlot.values().stream().filter(Objects::nonNull).findFirst().orElse(null);
		if (firstItem != null && StringUtils.hasText(firstItem.getCategoryName())) {
			return normalizedCategoryLabel(firstItem.getCategoryName());
		}
		return "장비 미설정";
	}

	private String buildActivityLabel(long postCount, long commentCount, long transactionCount) {
		return "글 " + postCount + " · 댓글 " + commentCount + " · 거래 " + transactionCount;
	}

	private String buildOptionSummary(Item item) {
		List<MyPageDtos.ItemStatChipDto> chips = buildStatChips(item);
		if (chips.isEmpty()) {
			return "옵션 정보 없음";
		}
		return chips.stream()
				.map(chip -> chip.label() + " " + chip.value())
				.reduce((left, right) -> left + " / " + right)
				.orElse("옵션 정보 없음");
	}

	private int compareNullableDate(LocalDateTime left, LocalDateTime right) {
		if (left == null && right == null) {
			return 0;
		}
		if (left == null) {
			return -1;
		}
		if (right == null) {
			return 1;
		}
		return left.compareTo(right);
	}

	private Map<String, Item> getSavedEquipmentBySlot(SiteUser user) {
		return this.userEquipmentRepository.findByUserOrderBySlotCodeAsc(user).stream()
				.filter(userEquipment -> userEquipment.getItem() != null)
				.collect(LinkedHashMap::new,
						(map, userEquipment) -> map.put(userEquipment.getSlotCode(), userEquipment.getItem()),
						Map::putAll);
	}

	@Transactional
	protected void sanitizeEquipment(SiteUser user) {
		for (UserEquipment userEquipment : this.userEquipmentRepository.findByUserOrderBySlotCodeAsc(user)) {
			Item item = userEquipment.getItem();
			if (item == null
					|| !Objects.equals(userEquipment.getSlotCode(), item.getSlotCode())
					|| !isRaceAllowed(item, user)) {
				this.userEquipmentRepository.delete(userEquipment);
			}
		}
	}

	@Transactional
	protected void ensureUserEquipmentReady(SiteUser user) {
		migrateLegacyEquipmentSlots(user);
		sanitizeEquipment(user);

		LocalDateTime now = LocalDateTime.now();
		Map<String, UserEquipment> existingBySlot = this.userEquipmentRepository.findByUserOrderBySlotCodeAsc(user).stream()
				.filter(userEquipment -> userEquipment.getItem() != null)
				.collect(LinkedHashMap::new,
						(map, userEquipment) -> map.putIfAbsent(userEquipment.getSlotCode(), userEquipment),
						Map::putAll);
		List<UserEquipment> defaults = new ArrayList<>();
		for (EquipmentSlot slot : EquipmentSlot.orderedValues()) {
			if (existingBySlot.containsKey(slot.getCode())) {
				continue;
			}
			Item weakestItem = weakestAllowedItemForUser(user, slot);
			if (weakestItem == null) {
				continue;
			}

			UserEquipment userEquipment = new UserEquipment();
			userEquipment.setUser(user);
			userEquipment.setSlotCode(slot.getCode());
			userEquipment.setItem(weakestItem);
			userEquipment.setUpdatedAt(now);
			defaults.add(userEquipment);
		}

		if (!defaults.isEmpty()) {
			this.userEquipmentRepository.saveAll(defaults);
		}
	}

	@Transactional
	protected void migrateLegacyEquipmentSlots(SiteUser user) {
		List<UserEquipment> rows = this.userEquipmentRepository.findByUserOrderBySlotCodeAsc(user);
		if (rows.isEmpty()) {
			return;
		}

		Map<String, UserEquipment> occupiedSlots = new LinkedHashMap<>();
		rows.forEach(row -> occupiedSlots.putIfAbsent(row.getSlotCode(), row));

		List<UserEquipment> updates = new ArrayList<>();
		List<UserEquipment> deletions = new ArrayList<>();
		LocalDateTime now = LocalDateTime.now();

		for (UserEquipment row : rows) {
			Item item = row.getItem();
			String targetSlotCode = item != null ? normalizedText(item.getSlotCode()) : "";
			if (!StringUtils.hasText(targetSlotCode) || Objects.equals(row.getSlotCode(), targetSlotCode)) {
				continue;
			}

			UserEquipment occupied = occupiedSlots.get(targetSlotCode);
			if (occupied == null || Objects.equals(occupied.getId(), row.getId())) {
				occupiedSlots.remove(row.getSlotCode());
				row.setSlotCode(targetSlotCode);
				row.setUpdatedAt(now);
				occupiedSlots.put(targetSlotCode, row);
				updates.add(row);
				continue;
			}

			deletions.add(row);
		}

		if (!updates.isEmpty()) {
			this.userEquipmentRepository.saveAll(updates);
		}
		if (!deletions.isEmpty()) {
			this.userEquipmentRepository.deleteAll(deletions);
		}
	}

	private Map<String, Item> resolveSimulationItems(SiteUser user, MyPageDtos.SimulatorStateRequest request) {
		Map<String, Long> equippedItemIds = request == null || request.equippedItemIds() == null
				? Map.of()
				: request.equippedItemIds();

		Map<Long, Item> itemsById = new LinkedHashMap<>();
		this.itemRepository.findAllById(equippedItemIds.values()).forEach(item -> itemsById.put(item.getId(), item));

		Map<String, Item> equippedBySlot = new LinkedHashMap<>();
		for (Map.Entry<String, Long> entry : equippedItemIds.entrySet()) {
			EquipmentSlot slot = resolveSlot(entry.getKey());
			Item item = itemsById.get(entry.getValue());
			if (item == null) {
				throw new DataNotFoundException("장착하려는 아이템을 찾을 수 없습니다.");
			}
			if (!slot.getCode().equalsIgnoreCase(item.getSlotCode())) {
				throw new IllegalStateException("아이템 슬롯 정보가 일치하지 않습니다.");
			}
			if (!isRaceAllowed(item, user)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "현재 종족과 맞지 않는 장비는 장착할 수 없습니다.");
			}
			equippedBySlot.put(slot.getCode(), item);
		}
		return equippedBySlot;
	}

	private PriorityCandidate buildPriorityCandidate(
			int stepOrder,
			EquipmentSlot slot,
			Item currentItem,
			OwnedItemContext ownedItemContext,
			SiteUser user,
			Map<String, List<Item>> slotItemsCache) {
		List<Item> slotItems = slotItemsFor(slot, user, slotItemsCache);
		double currentPower = safePower(currentItem);

		UpgradeAssessment selected = slotItems.stream()
				.filter(item -> currentItem == null || !Objects.equals(item.getId(), currentItem.getId()))
				.filter(item -> safePower(item) > currentPower)
				.map(item -> buildUpgradeAssessment(currentItem, item, isOwnedItem(item, ownedItemContext)))
				.filter(UpgradeAssessment::upgrade)
				.sorted(upgradeAssessmentComparator())
				.findFirst()
				.orElse(null);

		return new PriorityCandidate(
				stepOrder,
				slot,
				currentItem,
				selected != null ? selected.item() : null,
				currentPower,
				safePower(selected != null ? selected.item() : null),
				selected != null ? round(selected.delta().powerScoreDelta()) : 0d,
				selected != null && selected.owned(),
				selected != null ? selected.score() : 0d,
				selected != null ? selected.delta() : emptyDelta(),
				currentItem == null);
	}

	private String currentItemName(PriorityCandidate candidate) {
		return candidate.currentItem() != null
				? candidate.currentItem().getName()
				: candidate.slot().getLabel() + " 빈 슬롯";
	}

	private String targetItemName(PriorityCandidate candidate) {
		if (candidate.targetItem() == null) {
			return "대상 장비 없음";
		}
		return candidate.targetItem().getName();
	}

	private UpgradeAssessment buildUpgradeAssessment(
			Item currentItem,
			Item candidateItem,
			boolean owned) {
		RecommendationService.StatDelta delta = this.recommendationService.compare(candidateItem, currentItem);
		double powerDelta = round(delta.powerScoreDelta());
		double distanceFromTarget = Math.abs(powerDelta - TARGET_GROWTH_POWER_DELTA);
		double score = calculateRecommendationScore(powerDelta, distanceFromTarget, owned);
		boolean upgrade = powerDelta > 0.01d;
		return new UpgradeAssessment(candidateItem, delta, owned, powerDelta, distanceFromTarget, round(score), upgrade);
	}

	private double calculateRecommendationScore(
			double powerDelta,
			double distanceFromTarget,
			boolean owned) {
		double closenessScore = Math.max(0d, TARGET_GROWTH_POWER_DELTA - distanceFromTarget);
		double gradualBonus = Math.max(0d, TARGET_GROWTH_POWER_DELTA - powerDelta) * 0.15d;
		double ownedBonus = owned ? 5d : 0d;
		return closenessScore + gradualBonus + ownedBonus;
	}

	private String formatPriorityHeadline(int rank, PriorityCandidate candidate) {
		if (candidate.targetItem() == null) {
			return rank + "단계: " + candidate.slot().getLabel() + " 유지";
		}
		if (candidate.emptySlot()) {
			return rank + "단계 추천: " + candidate.slot().getLabel() + " 장착";
		}
		return rank + "단계 추천: " + candidate.slot().getLabel() + " 업그레이드";
	}

	private String formatPriorityDescription(PriorityCandidate candidate) {
		if (candidate.targetItem() == null) {
			return "현재 장비 구성이 안정적이라 다음 단계로 추천할 교체 후보를 찾지 못했습니다.";
		}

		String scope = candidate.ownedUpgradeAvailable()
				? "보유 중인 장비라 바로 적용할 수 있습니다."
				: "전체 아이템 기준으로 비교한 추천입니다.";
		String gradualGrowth = "한 번에 과도한 점프보다 목표 상승치 +"
				+ formatNumber(TARGET_GROWTH_POWER_DELTA)
				+ "에 가까운 단계형 업그레이드를 우선 선택했습니다.";

		if (candidate.emptySlot()) {
			return "현재 우선 교체할 부위는 " + candidate.slot().getLabel() + "입니다. "
					+ targetItemName(candidate) + " 장착 시 현재 P "
					+ formatNumber(candidate.currentPowerScore()) + "에서 P "
					+ formatNumber(candidate.targetPowerScore()) + "로 올라갑니다. "
					+ gradualGrowth + " " + scope;
		}

		return "현재 우선 교체할 부위는 " + candidate.slot().getLabel() + "입니다. "
				+ currentItemName(candidate) + "보다 적절히 높은 성능의 "
				+ targetItemName(candidate) + "를 추천했습니다. "
				+ "현재 P " + formatNumber(candidate.currentPowerScore()) + "에서 P "
				+ formatNumber(candidate.targetPowerScore()) + "로, 예상 상승치 "
				+ formatSignedPower(candidate.gapPowerScore()) + "입니다. "
				+ gradualGrowth + " " + scope;
	}

	private List<MyPageDtos.ItemStatChipDto> buildDeltaHighlights(RecommendationService.StatDelta delta) {
		List<MyPageDtos.ItemStatChipDto> highlights = new ArrayList<>();
		appendPositiveDelta(highlights, "공격력", delta.attackMaxDelta());
		appendPositiveDelta(highlights, "방어력", delta.defenseDelta());
		appendPositiveDelta(highlights, "명중", delta.accuracyDelta());
		appendPositiveDelta(highlights, "치명타", delta.criticalDelta());
		appendPositiveDelta(highlights, "생명력", delta.healthDelta());
		appendPositiveDelta(highlights, "마법 적중", delta.magicAccuracyDelta());
		appendPositiveDelta(highlights, "마법 증폭", delta.magicBoostDelta());
		appendPositiveDelta(highlights, "전투력", delta.powerScoreDelta());

		if (highlights.isEmpty()) {
			highlights.add(new MyPageDtos.ItemStatChipDto("전투력", "+" + formatNumber(delta.powerScoreDelta())));
		}
		return highlights.stream().limit(4).toList();
	}

	private void appendPositiveDelta(List<MyPageDtos.ItemStatChipDto> highlights, String label, double value) {
		if (value <= 0.01d) {
			return;
		}
		highlights.add(new MyPageDtos.ItemStatChipDto(label, "+" + formatNumber(value)));
	}

	private String buildDeltaSummary(RecommendationService.StatDelta delta) {
		List<String> parts = buildDeltaHighlights(delta).stream()
				.map(chip -> chip.label() + " " + chip.value())
				.toList();

		if (parts.isEmpty()) {
			return "전투력 +" + formatNumber(delta.powerScoreDelta());
		}
		return String.join(", ", parts);
	}

	private RecommendationService.StatDelta emptyDelta() {
		return new RecommendationService.StatDelta(0d, 0d, 0d, 0d, 0d, 0d, 0d, 0d, 0d, 0d, 0d, Map.of());
	}

	private MyPageDtos.UserStatResponse toUserStatResponse(RecommendationService.StatSummary summary) {
		return new MyPageDtos.UserStatResponse(
				round(summary.attackMin()),
				round(summary.attackMax()),
				round(summary.defense()),
				round(summary.accuracy()),
				round(summary.critical()),
				round(summary.health()),
				round(summary.magicBoost()),
				round(summary.magicAccuracy()),
				round(summary.pveAttack()),
				round(summary.healingBoost()),
				round(summary.powerScore()),
				summary.summary());
	}

	private MyPageDtos.ItemCardResponse toItemCardResponse(Item item, boolean owned, boolean saved) {
		if (item == null) {
			return null;
		}

		EquipmentSlot slot = EquipmentSlot.fromCode(item.getSlotCode()).orElse(null);
		String gradeName = firstText(item.getGradeName(), item.getGrade(), "일반");
		String categoryName = normalizedCategoryLabel(item.getCategoryName());

		return new MyPageDtos.ItemCardResponse(
				item.getId(),
				item.getSlotCode(),
				slot != null ? slot.getLabel() : categoryName,
				item.getName(),
				item.getIcon(),
				gradeName,
				normalizeGradeKey(gradeName),
				categoryName,
				shortText(stripTags(item.getDescription()), 120),
				firstText(item.getRaceName(), "전체", "전체"),
				item.getEquipLevel(),
				roundedPower(item),
				buildStatChips(item),
				owned,
				saved);
	}

	private List<MyPageDtos.ItemStatChipDto> buildStatChips(Item item) {
		List<MyPageDtos.ItemStatChipDto> chips = new ArrayList<>();
		appendStatChips(item.getMainStatsJson(), chips);
		if (chips.size() < 3) {
			appendStatChips(item.getSubStatsJson(), chips);
		}
		if (chips.isEmpty() && safePower(item) > 0d) {
			chips.add(new MyPageDtos.ItemStatChipDto("전투력", formattedPower(item)));
		}
		return chips.stream().limit(3).toList();
	}

	private void appendStatChips(String json, List<MyPageDtos.ItemStatChipDto> chips) {
		JsonNode root = parseJson(json);
		if (root == null || !root.isArray()) {
			return;
		}

		for (JsonNode node : root) {
			String name = text(node, "name");
			String value = formatAttributeValue(node);
			if (!StringUtils.hasText(name) || !StringUtils.hasText(value)) {
				continue;
			}
			chips.add(new MyPageDtos.ItemStatChipDto(name, value));
			if (chips.size() >= 3) {
				return;
			}
		}
	}

	private String formatAttributeValue(JsonNode node) {
		String minValue = text(node, "minValue");
		String value = text(node, "value");
		String extra = text(node, "extra");

		if (StringUtils.hasText(minValue) && StringUtils.hasText(value) && !minValue.equals(value)) {
			return minValue + " ~ " + value;
		}
		if (StringUtils.hasText(value)) {
			return value;
		}
		return extra;
	}

	private OwnedItemContext getOwnedItemContext(SiteUser user) {
		List<TradeTransaction> transactions = this.tradeTransactionRepository.findPurchaseHistoryByBuyerUserId(user.getId());
		List<Item> tradableItems = this.itemRepository.findByTradableTrueAndTypeIn(SLOTTABLE_TYPES).stream()
				.filter(item -> StringUtils.hasText(item.getSlotCode()))
				.filter(item -> isRaceAllowed(item, user))
				.toList();
		Map<String, Item> ownedTradeItemIndex = buildOwnedTradeItemIndex(tradableItems);
		Set<String> ownedKeys = new LinkedHashSet<>();
		Set<Long> ownedItemIds = new LinkedHashSet<>();
		Set<String> purchaseVaultKeys = new LinkedHashSet<>();
		List<MyPageDtos.PurchaseVaultItemResponse> purchaseVault = new ArrayList<>();
		Map<String, List<Item>> slotItemsCache = new LinkedHashMap<>();
		Map<String, List<Item>> categoryItemsCache = new LinkedHashMap<>();

		registerBaseOwnedItems(user, ownedKeys, ownedItemIds);
		registerSavedEquipment(user, ownedKeys, ownedItemIds);

		for (TradeTransaction transaction : transactions) {
			TradeItem tradeItem = transaction.getItem();
			if (tradeItem == null) {
				continue;
			}

			String itemName = tradeItemName(tradeItem);
			String categoryLabel = tradeCategoryLabel(tradeItem);
			String tradeKey = tradeKey(categoryLabel, itemName);
			if (!StringUtils.hasText(itemName) || !StringUtils.hasText(tradeKey)) {
				continue;
			}

			ResolvedOwnedItem resolvedOwnedItem = resolveOwnedItem(
					tradeItem,
					itemName,
					categoryLabel,
					user,
					ownedTradeItemIndex,
					slotItemsCache,
					categoryItemsCache);
			if (resolvedOwnedItem != null && resolvedOwnedItem.item() != null) {
				ownedItemIds.add(resolvedOwnedItem.item().getId());
				ownedKeys.add(itemTradeKey(resolvedOwnedItem.item()));
			}

			ownedKeys.add(tradeKey);
			String purchaseVaultKey = tradeKey + "::"
					+ (resolvedOwnedItem != null && resolvedOwnedItem.item() != null ? resolvedOwnedItem.item().getId() : 0L);
			boolean firstPurchase = purchaseVaultKeys.add(purchaseVaultKey);
			if (firstPurchase) {
				purchaseVault.add(toPurchaseVaultItemResponse(transaction, tradeItem, itemName, categoryLabel, resolvedOwnedItem));
			}
		}

		return new OwnedItemContext(ownedKeys, ownedItemIds, purchaseVault);
	}

	private void registerBaseOwnedItems(SiteUser user, Set<String> ownedKeys, Set<Long> ownedItemIds) {
		for (EquipmentSlot slot : EquipmentSlot.orderedValues()) {
			addOwnedItem(weakestAllowedItemForUser(user, slot), ownedKeys, ownedItemIds);
		}
	}

	private void registerSavedEquipment(SiteUser user, Set<String> ownedKeys, Set<Long> ownedItemIds) {
		getSavedEquipmentBySlot(user).values().forEach(item -> addOwnedItem(item, ownedKeys, ownedItemIds));
	}

	private void addOwnedItem(Item item, Set<String> ownedKeys, Set<Long> ownedItemIds) {
		if (item == null) {
			return;
		}

		ownedItemIds.add(item.getId());
		String itemKey = itemTradeKey(item);
		if (StringUtils.hasText(itemKey)) {
			ownedKeys.add(itemKey);
		}
	}

	private Map<String, Item> buildOwnedTradeItemIndex(List<Item> tradableItems) {
		Map<String, Item> itemIndex = new LinkedHashMap<>();
		for (Item item : tradableItems) {
			String key = itemTradeKey(item);
			if (!StringUtils.hasText(key)) {
				continue;
			}
			itemIndex.merge(key, item, this::preferOwnedTradeItem);
		}
		return itemIndex;
	}

	private Item preferOwnedTradeItem(Item current, Item candidate) {
		if (current == null) {
			return candidate;
		}
		if (candidate == null) {
			return current;
		}

		boolean currentHasIcon = StringUtils.hasText(current.getIcon());
		boolean candidateHasIcon = StringUtils.hasText(candidate.getIcon());
		if (candidateHasIcon != currentHasIcon) {
			return candidateHasIcon ? candidate : current;
		}

		double currentPower = safePower(current);
		double candidatePower = safePower(candidate);
		if (candidatePower != currentPower) {
			return candidatePower > currentPower ? candidate : current;
		}

		return candidate.getId() < current.getId() ? candidate : current;
	}

	private ResolvedOwnedItem resolveOwnedItem(
			TradeItem tradeItem,
			String itemName,
			String categoryLabel,
			SiteUser user,
			Map<String, Item> ownedTradeItemIndex,
			Map<String, List<Item>> slotItemsCache,
			Map<String, List<Item>> categoryItemsCache) {
		ResolvedOwnedItem exactTradeMatch = resolveOwnedItemFromTradeIndex(
				tradeItem,
				itemName,
				categoryLabel,
				ownedTradeItemIndex);
		if (exactTradeMatch != null) {
			return exactTradeMatch;
		}

		return resolveOwnedItem(
				itemName,
				categoryLabel,
				user,
				slotItemsCache,
				categoryItemsCache);
	}

	private ResolvedOwnedItem resolveOwnedItemFromTradeIndex(
			TradeItem tradeItem,
			String itemName,
			String categoryLabel,
			Map<String, Item> ownedTradeItemIndex) {
		if (ownedTradeItemIndex.isEmpty()) {
			return null;
		}

		LinkedHashSet<String> lookupKeys = new LinkedHashSet<>();
		addTradeLookupKeys(lookupKeys, categoryLabel, itemName);
		addTradeLookupKeys(lookupKeys, tradeItem != null ? tradeItem.getCategory() : null, tradeItemName(tradeItem));
		addTradeLookupKeys(lookupKeys, tradeItem != null ? tradeItem.getSubCategory() : null, tradeItemName(tradeItem));

		TradeCatalogItem catalogItem = tradeCatalogItem(tradeItem);
		if (catalogItem != null) {
			addTradeLookupKeys(lookupKeys, catalogItem.getCategory(), catalogItem.getItemName());
		}

		for (EquipmentSlot candidateSlot : inferOwnedItemSlots(tradeItem, categoryLabel, itemName)) {
			addTradeLookupKeys(lookupKeys, candidateSlot.getPriorityGroup(), itemName);
			addTradeLookupKeys(lookupKeys, candidateSlot.getPriorityGroup(), tradeItemName(tradeItem));
			if (catalogItem != null) {
				addTradeLookupKeys(lookupKeys, candidateSlot.getPriorityGroup(), catalogItem.getItemName());
			}
		}

		for (String lookupKey : lookupKeys) {
			Item linkedItem = ownedTradeItemIndex.get(lookupKey);
			if (linkedItem == null) {
				continue;
			}
			return toResolvedOwnedItem(linkedItem, "trade-index");
		}

		return null;
	}

	private void addTradeLookupKeys(Set<String> lookupKeys, String categoryLabel, String itemName) {
		if (!StringUtils.hasText(itemName)) {
			return;
		}

		String directKey = tradeKey(categoryLabel, itemName);
		if (StringUtils.hasText(directKey)) {
			lookupKeys.add(directKey);
		}

		EquipmentSlot.infer(categoryLabel, itemName)
				.ifPresent(slot -> lookupKeys.add(tradeKey(slot.getPriorityGroup(), itemName)));
		EquipmentSlot.fromCode(categoryLabel)
				.ifPresent(slot -> lookupKeys.add(tradeKey(slot.getPriorityGroup(), itemName)));
	}

	private List<EquipmentSlot> inferOwnedItemSlots(TradeItem tradeItem, String categoryLabel, String itemName) {
		LinkedHashSet<EquipmentSlot> candidateSlots = new LinkedHashSet<>();
		EquipmentSlot.infer(categoryLabel, itemName).ifPresent(candidateSlots::add);
		EquipmentSlot.infer(categoryLabel, categoryLabel).ifPresent(candidateSlots::add);

		if (tradeItem != null) {
			EquipmentSlot.infer(tradeItem.getCategory(), tradeItem.getTitle()).ifPresent(candidateSlots::add);
			EquipmentSlot.infer(tradeItem.getCategory(), tradeItem.getSubCategory()).ifPresent(candidateSlots::add);
			EquipmentSlot.fromCode(tradeItem.getCategory()).ifPresent(candidateSlots::add);
		}

		TradeCatalogItem catalogItem = tradeCatalogItem(tradeItem);
		if (catalogItem != null) {
			EquipmentSlot.infer(catalogItem.getCategory(), catalogItem.getItemName()).ifPresent(candidateSlots::add);
			EquipmentSlot.fromCode(catalogItem.getCategory()).ifPresent(candidateSlots::add);
			candidateSlots.addAll(EquipmentSlot.slotsInGroup(catalogItem.getCategory()));
		}

		candidateSlots.addAll(EquipmentSlot.slotsInGroup(categoryLabel));
		return candidateSlots.stream().toList();
	}

	private ResolvedOwnedItem toResolvedOwnedItem(Item item, String matchType) {
		if (item == null) {
			return null;
		}

		EquipmentSlot slot = EquipmentSlot.fromCode(item.getSlotCode()).orElse(null);
		if (slot == null) {
			return null;
		}

		return new ResolvedOwnedItem(slot, item, matchType);
	}

	private boolean isOwnedItem(Item item, OwnedItemContext ownedItemContext) {
		if (item == null || ownedItemContext == null) {
			return false;
		}
		return ownedItemContext.ownedItemIds().contains(item.getId())
				|| ownedItemContext.ownedKeys().contains(itemTradeKey(item));
	}

	private String buildCatalogEmptyMessage(
			EquipmentSlot slot,
			boolean ownedOnly,
			String keyword,
			OwnedItemContext ownedItemContext,
			List<MyPageDtos.ItemCardResponse> responses) {
		if (!responses.isEmpty()) {
			return "";
		}
		if (ownedOnly) {
			if (ownedItemContext.purchaseVault().isEmpty()) {
				return "구매한 아이템이 없습니다.";
			}
			if (StringUtils.hasText(keyword)) {
				return slot.getLabel() + " 슬롯에서 검색 조건에 맞는 구매 아이템이 없습니다.";
			}
			return slot.getLabel() + " 슬롯에 장착 가능한 구매 아이템이 없습니다.";
		}
		if (StringUtils.hasText(keyword)) {
			return "검색 조건에 맞는 아이템이 없습니다.";
		}
		return slot.getLabel() + " 슬롯 아이템이 없습니다.";
	}

	private String resolveSlotCode(Item item) {
		if (!isSlottableType(item != null ? item.getType() : null)) {
			return null;
		}
		return EquipmentSlot.infer(
				item != null ? item.getCategoryName() : null,
				item != null ? item.getEquipCategory() : null)
				.map(EquipmentSlot::getCode)
				.orElse(null);
	}

	private boolean isSlottableType(String type) {
		return SLOTTABLE_TYPES.stream().anyMatch(candidate -> candidate.equalsIgnoreCase(normalizedText(type)));
	}

	private List<Item> allowedSlotItemsForUser(SiteUser user, EquipmentSlot slot) {
		return this.itemRepository.findBySlotCodeOrderByPowerScoreDescNameAsc(slot.getCode()).stream()
				.filter(item -> isRaceAllowed(item, user))
				.toList();
	}

	private Item weakestAllowedItemForUser(SiteUser user, EquipmentSlot slot) {
		return allowedSlotItemsForUser(user, slot).stream()
				.sorted(weakestItemComparator())
				.findFirst()
				.orElse(null);
	}

	private boolean isRaceAllowed(Item item, SiteUser user) {
		if (item == null) {
			return false;
		}

		String itemRace = normalizedText(item.getRaceName());
		if (!StringUtils.hasText(itemRace) || "전체".equals(itemRace)) {
			return true;
		}
		return normalizeRaceValue(itemRace).equals(normalizeRaceValue(userRaceLabel(user)));
	}

	private String userRaceLabel(SiteUser user) {
		return UserRace.from(user != null ? user.getRace() : null).getLabel();
	}

	private String normalizeRaceValue(String value) {
		return normalizedText(value)
				.replace(" ", "")
				.replace("-", "")
				.toLowerCase(Locale.ROOT);
	}

	private ResolvedOwnedItem resolveOwnedItem(
			String itemName,
			String categoryLabel,
			SiteUser user,
			Map<String, List<Item>> slotItemsCache,
			Map<String, List<Item>> categoryItemsCache) {
		if (!StringUtils.hasText(itemName)) {
			return null;
		}

		LinkedHashSet<EquipmentSlot> candidateSlots = new LinkedHashSet<>();
		EquipmentSlot.infer(categoryLabel, itemName).ifPresent(candidateSlots::add);
		EquipmentSlot.infer(categoryLabel, categoryLabel).ifPresent(candidateSlots::add);
		if (candidateSlots.isEmpty()) {
			candidateSlots.addAll(EquipmentSlot.slotsInGroup(categoryLabel));
		}

		for (EquipmentSlot slot : candidateSlots) {
			ResolvedOwnedItem resolved = resolveOwnedItemInSlot(
					slot,
					itemName,
					categoryLabel,
					user,
					slotItemsCache,
					categoryItemsCache);
			if (resolved != null) {
				return resolved;
			}
		}

		return null;
	}

	private ResolvedOwnedItem resolveOwnedItemInSlot(
			EquipmentSlot slot,
			String itemName,
			String categoryLabel,
			SiteUser user,
			Map<String, List<Item>> slotItemsCache,
			Map<String, List<Item>> categoryItemsCache) {
		List<Item> categoryItems = categoryItemsFor(slot, categoryLabel, user, slotItemsCache, categoryItemsCache);
		List<Item> slotItems = slotItemsFor(slot, user, slotItemsCache);

		Item exactMatch = findExactOwnedItem(categoryItems, itemName);
		if (exactMatch != null) {
			return new ResolvedOwnedItem(slot, exactMatch, "exact");
		}

		Item fuzzyMatch = findFuzzyOwnedItem(categoryItems, itemName);
		if (fuzzyMatch != null) {
			return new ResolvedOwnedItem(slot, fuzzyMatch, "fuzzy");
		}

		Integer legacyTier = extractTrailingNumber(itemName);
		if (legacyTier != null) {
			Item tierMatch = findLegacyTierItem(categoryItems.isEmpty() ? slotItems : categoryItems, legacyTier);
			if (tierMatch != null) {
				return new ResolvedOwnedItem(slot, tierMatch, "legacy-tier");
			}
		}

		Item slotLevelMatch = findFuzzyOwnedItem(slotItems, itemName);
		if (slotLevelMatch != null) {
			return new ResolvedOwnedItem(slot, slotLevelMatch, "slot-fuzzy");
		}

		return null;
	}

	private List<Item> slotItemsFor(EquipmentSlot slot, SiteUser user, Map<String, List<Item>> slotItemsCache) {
		return slotItemsCache.computeIfAbsent(slot.getCode(),
				key -> this.itemRepository.findBySlotCodeOrderByPowerScoreDescNameAsc(key).stream()
						.filter(item -> isRaceAllowed(item, user))
						.toList());
	}

	private List<Item> categoryItemsFor(
			EquipmentSlot slot,
			String categoryLabel,
			SiteUser user,
			Map<String, List<Item>> slotItemsCache,
			Map<String, List<Item>> categoryItemsCache) {
		String cacheKey = slot.getCode() + "::" + normalizedCategoryKey(categoryLabel);
		return categoryItemsCache.computeIfAbsent(cacheKey, key -> slotItemsFor(slot, user, slotItemsCache).stream()
				.filter(item -> {
					String normalizedCategory = normalizedCategoryKey(categoryLabel);
					if (!StringUtils.hasText(normalizedCategory)
							|| normalizedCategory.equals(normalizedCategoryKey(slot.getPriorityGroup()))) {
						return true;
					}
					return normalizedCategoryKey(item.getCategoryName()).equals(normalizedCategory);
				})
				.toList());
	}

	private Item findExactOwnedItem(List<Item> items, String itemName) {
		String target = normalizedMatchKey(itemName);
		return items.stream()
				.filter(item -> normalizedMatchKey(item.getName()).equals(target))
				.max(Comparator.comparingDouble(this::safePower))
				.orElse(null);
	}

	private Item findFuzzyOwnedItem(List<Item> items, String itemName) {
		String target = normalizedMatchKey(itemName);
		if (!StringUtils.hasText(target)) {
			return null;
		}

		return items.stream()
				.map(item -> new ItemMatch(item, matchScore(target, normalizedMatchKey(item.getName()))))
				.filter(match -> match.score() >= 0.55d)
				.sorted(itemMatchComparator())
				.map(ItemMatch::item)
				.findFirst()
				.orElse(null);
	}

	private Item findLegacyTierItem(List<Item> items, int tier) {
		if (items.isEmpty()) {
			return null;
		}

		int boundedTier = Math.max(1, Math.min(LEGACY_TIER_MAX, tier));
		List<Item> ordered = items.stream()
				.sorted(legacyTierItemComparator())
				.toList();

		double ratio = LEGACY_TIER_MAX == 1 ? 0d : (double) (boundedTier - 1) / (double) (LEGACY_TIER_MAX - 1);
		int index = (int) Math.round(ratio * (ordered.size() - 1));
		return ordered.get(Math.max(0, Math.min(ordered.size() - 1, index)));
	}

	private Integer extractTrailingNumber(String value) {
		Matcher matcher = TRAILING_NUMBER_PATTERN.matcher(normalizedText(value));
		if (!matcher.find()) {
			return null;
		}
		try {
			return Integer.parseInt(matcher.group(1));
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	private String normalizedMatchKey(String value) {
		return normalizedText(value).toLowerCase(Locale.ROOT)
				.replace(" ", "")
				.replace("-", "")
				.replace("_", "")
				.replace("(", "")
				.replace(")", "");
	}

	private String normalizedCategoryKey(String value) {
		return normalizedMatchKey(normalizedCategoryLabel(value));
	}

	private double matchScore(String left, String right) {
		if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
			return 0d;
		}
		if (left.equals(right)) {
			return 1d;
		}
		if (left.contains(right) || right.contains(left)) {
			return 0.82d;
		}

		int common = 0;
		for (int index = 0; index < Math.min(left.length(), right.length()); index++) {
			if (left.charAt(index) == right.charAt(index)) {
				common++;
			}
		}
		return (double) common / (double) Math.max(left.length(), right.length());
	}

	private String itemTradeKey(Item item) {
		if (item == null) {
			return "";
		}
		return tradeKey(tradeCategoryLabel(item), normalizedText(item.getName()));
	}

	private String tradeCategoryLabel(Item item) {
		if (item == null) {
			return "";
		}
		return EquipmentSlot.fromCode(item.getSlotCode())
				.map(EquipmentSlot::getPriorityGroup)
				.orElseGet(() -> normalizedCategoryLabel(item.getCategoryName()));
	}

	private String tradeKey(String categoryLabel, String itemName) {
		String normalizedName = normalizedText(itemName).toLowerCase(Locale.ROOT);
		if (!StringUtils.hasText(normalizedName)) {
			return "";
		}
		String normalizedCategory = normalizedText(categoryLabel).toLowerCase(Locale.ROOT);
		return normalizedCategory + "::" + normalizedName;
	}

	private String normalizedCategoryLabel(String categoryName) {
		String normalized = normalizedText(categoryName);
		int bracketIndex = normalized.indexOf('(');
		if (bracketIndex > 0) {
			return normalized.substring(0, bracketIndex).trim();
		}
		return normalized;
	}

	private double safePower(Item item) {
		if (item == null) {
			return 0d;
		}
		if (item.getPowerScore() != null) {
			return item.getPowerScore();
		}
		return this.recommendationService.profileForItem(item).powerScore();
	}

	private Double bestPower(Collection<Item> items) {
		return round(items.stream()
				.mapToDouble(this::safePower)
				.max()
				.orElse(0d));
	}

	private EquipmentSlot resolveSlot(String slotCode) {
		return EquipmentSlot.fromCode(slotCode)
				.orElseThrow(() -> new DataNotFoundException("지원하지 않는 장비 슬롯입니다."));
	}

	private JsonNode parseJson(String json) {
		if (!StringUtils.hasText(json)) {
			return null;
		}
		try {
			return this.objectMapper.readTree(json);
		} catch (Exception exception) {
			return null;
		}
	}

	private String text(JsonNode node, String fieldName) {
		if (node == null || node.get(fieldName) == null || node.get(fieldName).isNull()) {
			return null;
		}
		return node.get(fieldName).asText();
	}

	private String normalizeGradeKey(String value) {
		return normalizedText(value).toLowerCase(Locale.ROOT)
				.replace(" ", "-");
	}

	private String firstText(String first, String second, String fallback) {
		if (StringUtils.hasText(first)) {
			return first.trim();
		}
		if (StringUtils.hasText(second)) {
			return second.trim();
		}
		return fallback;
	}

	private String normalizedText(String value) {
		return value == null ? "" : value.trim();
	}

	private String stripTags(String value) {
		if (!StringUtils.hasText(value)) {
			return "";
		}
		return value.replaceAll("<[^>]+>", " ")
				.replaceAll("\\s+", " ")
				.trim();
	}

	private String shortText(String value, int limit) {
		if (!StringUtils.hasText(value) || value.length() <= limit) {
			return value;
		}
		return value.substring(0, Math.max(0, limit - 3)).trim() + "...";
	}

	private double round(double value) {
		return Math.round(value * 100d) / 100d;
	}

	private String formatNumber(double value) {
		if (Math.abs(value - Math.rint(value)) < 0.0001d) {
			return String.valueOf((long) Math.rint(value));
		}
		return String.format(Locale.ROOT, "%.1f", value);
	}

	private String formatGold(Integer value) {
		if (value == null) {
			return "-";
		}
		return String.format(Locale.US, "%,dG", value);
	}

	private double roundedPower(Item item) {
		return round(safePower(item));
	}

	private String formattedPower(Item item) {
		return formatNumber(safePower(item));
	}

	private String formatSignedPower(double value) {
		return (value >= 0d ? "+" : "") + formatNumber(value);
	}

	private Comparator<PriorityCandidate> growthPriorityComparator() {
		return Comparator.comparing(PriorityCandidate::currentPowerScore)
				.thenComparing(candidate -> candidate.slot().getDisplayOrder())
				.thenComparing(PriorityCandidate::stepOrder)
				.thenComparing(PriorityCandidate::recommendationScore, Comparator.reverseOrder())
				.thenComparing(candidate -> candidate.slot().getCode());
	}

	private MyPageView.TradeEntry toSaleEntry(TradeItem tradeItem) {
		TradeTransaction lastTransaction = latestTradeTransaction(tradeItem);
		Integer price = lastTransaction != null ? lastTransaction.getPrice() : tradeItem.getPrice();
		LocalDateTime occurredAt = lastTransaction != null ? lastTransaction.getCreatedAt() : null;
		return new MyPageView.TradeEntry(
				tradeItemName(tradeItem),
				tradeCategoryLabel(tradeItem),
				normalizedText(tradeItem.getStatus()),
				price,
				occurredAt,
				tradeImageUrl(tradeItem));
	}

	private TradeTransaction latestTradeTransaction(TradeItem tradeItem) {
		return tradeItem.getTransactions().stream()
				.max(tradeTransactionCreatedAtComparator())
				.orElse(null);
	}

	private Comparator<TradeTransaction> tradeTransactionCreatedAtComparator() {
		return (left, right) -> left.getCreatedAt().compareTo(right.getCreatedAt());
	}

	private TradeCatalogItem tradeCatalogItem(TradeItem tradeItem) {
		return tradeItem == null ? null : tradeItem.getCatalogItem();
	}

	private String tradeItemName(TradeItem tradeItem) {
		TradeCatalogItem catalogItem = tradeCatalogItem(tradeItem);
		return normalizedText(catalogItem != null ? catalogItem.getItemName() : tradeItem != null ? tradeItem.getTitle() : "");
	}

	private String tradeCategoryLabel(TradeItem tradeItem) {
		TradeCatalogItem catalogItem = tradeCatalogItem(tradeItem);
		return normalizedText(catalogItem != null ? catalogItem.getCategory() : tradeItem != null ? tradeItem.getCategory() : "");
	}

	private String tradeImageUrl(TradeItem tradeItem) {
		return normalizedText(tradeItem != null ? tradeItem.getImageUrl() : "");
	}

	private String tradeImageUrl(TradeItem tradeItem, Item fallbackItem) {
		String imageUrl = tradeItem != null ? tradeItem.getImageUrl() : null;
		if (StringUtils.hasText(imageUrl)) {
			return normalizedText(imageUrl);
		}
		return normalizedText(fallbackItem != null ? fallbackItem.getIcon() : "");
	}

	private List<MyPageView.ActivityEntry> sortRecentActivities(List<MyPageView.ActivityEntry> entries) {
		return entries.stream()
				.sorted(recentActivityComparator())
				.limit(5)
				.toList();
	}

	private Comparator<MyPageView.ActivityEntry> recentActivityComparator() {
		return (left, right) -> compareNullableDate(right.occurredAt(), left.occurredAt());
	}

	private String buildBoardPostUrl(BoardPost post) {
		if (post == null) {
			return null;
		}
		return "/boards/" + (post.getCategory() != null ? post.getCategory().getCode() : "free") + "/posts/" + post.getId();
	}

	private String buildSkillTreePostUrl(SkillTreePost post) {
		if (post == null) {
			return null;
		}
		return "/skilltree/" + (post.getJob() != null ? post.getJob().getSlug() : "") + "/posts/" + post.getId();
	}

	private Comparator<UpgradeAssessment> upgradeAssessmentComparator() {
		return Comparator.comparing(UpgradeAssessment::distanceFromTarget)
				.thenComparing(UpgradeAssessment::powerDelta)
				.thenComparing(assessment -> assessment.owned() ? 0 : 1)
				.thenComparing(assessment -> normalizedText(assessment.item().getName()), String.CASE_INSENSITIVE_ORDER)
				.thenComparing(assessment -> assessment.item().getId());
	}

	private MyPageDtos.PurchaseVaultItemResponse toPurchaseVaultItemResponse(
			TradeTransaction transaction,
			TradeItem tradeItem,
			String itemName,
			String categoryLabel,
			ResolvedOwnedItem resolvedOwnedItem) {
		Item linkedItem = resolvedOwnedItem != null ? resolvedOwnedItem.item() : null;
		EquipmentSlot linkedSlot = resolvedOwnedItem != null ? resolvedOwnedItem.slot() : null;
		return new MyPageDtos.PurchaseVaultItemResponse(
				itemName,
				tradeImageUrl(tradeItem, linkedItem),
				categoryLabel,
				transaction.getPrice(),
				transaction.getCreatedAt(),
				linkedSlot != null ? linkedSlot.getCode() : null,
				linkedSlot != null ? linkedSlot.getLabel() : null,
				linkedItem != null ? linkedItem.getName() : null,
				linkedItem != null ? firstText(linkedItem.getRaceName(), "전체", "전체") : "전체",
				linkedItem != null ? linkedItem.getId() : null,
				linkedItem != null);
	}

	private Comparator<Item> weakestItemComparator() {
		return Comparator.comparing((Item item) -> item.getEquipLevel() == null ? Integer.MAX_VALUE : item.getEquipLevel())
				.thenComparing(this::safePower)
				.thenComparing(Item::getId);
	}

	private Comparator<MyPageView.OwnedGearEntry> ownedGearEntryComparator() {
		return Comparator.comparing(MyPageView.OwnedGearEntry::equipped)
				.reversed()
				.thenComparing(MyPageView.OwnedGearEntry::powerScore, Comparator.nullsLast(Comparator.reverseOrder()))
				.thenComparing(MyPageView.OwnedGearEntry::itemName, String.CASE_INSENSITIVE_ORDER);
	}

	private Comparator<ItemMatch> itemMatchComparator() {
		return Comparator.comparing(ItemMatch::score).reversed()
				.thenComparing(match -> safePower(match.item())).reversed()
				.thenComparing(match -> match.item().getId());
	}

	private Comparator<Item> legacyTierItemComparator() {
		return Comparator.comparingDouble(this::safePower)
				.thenComparing(item -> normalizedText(item.getName()), String.CASE_INSENSITIVE_ORDER)
				.thenComparing(Item::getId);
	}

	private record OwnedItemContext(
			Set<String> ownedKeys,
			Set<Long> ownedItemIds,
			List<MyPageDtos.PurchaseVaultItemResponse> purchaseVault) {
	}

	private record OwnedPurchaseMeta(
			String sourceLabel,
			Integer price,
			LocalDateTime purchasedAt) {
	}

	private record PriorityCandidate(
			int stepOrder,
			EquipmentSlot slot,
			Item currentItem,
			Item targetItem,
			double currentPowerScore,
			double targetPowerScore,
			double gapPowerScore,
			boolean ownedUpgradeAvailable,
			double recommendationScore,
			RecommendationService.StatDelta delta,
			boolean emptySlot) {

		private boolean hasTargetUpgrade() {
			return this.targetItem != null;
		}
	}

	private record UpgradeAssessment(
			Item item,
			RecommendationService.StatDelta delta,
			boolean owned,
			double powerDelta,
			double distanceFromTarget,
			double score,
			boolean upgrade) {
	}

	private record ResolvedOwnedItem(
			EquipmentSlot slot,
			Item item,
			String matchType) {
	}

	private record ItemMatch(
			Item item,
			double score) {
	}
}
