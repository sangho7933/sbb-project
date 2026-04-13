package com.mysite.sbb.trade.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mysite.sbb.DataNotFoundException;
import com.mysite.sbb.trade.controller.TradeItemForm;
import com.mysite.sbb.trade.entity.TradeCatalogItem;
import com.mysite.sbb.trade.entity.TradeItem;
import com.mysite.sbb.trade.entity.TradeTransaction;
import com.mysite.sbb.trade.entity.TradeUser;
import com.mysite.sbb.trade.repository.TradeItemRepository;
import com.mysite.sbb.trade.repository.TradeTransactionRepository;
import com.mysite.sbb.user.SiteUser;
import com.mysite.sbb.user.UserRepository;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class TradeItemService {

	private static final int PAGE_SIZE = 10;

	private final TradeItemRepository tradeItemRepository;
	private final TradeTransactionRepository tradeTransactionRepository;
	private final TradeUserProfileService tradeUserProfileService;
	private final TradeCatalogService tradeCatalogService;
	private final UserRepository userRepository;

	public Page<TradeItem> getList(
			String category,
			String kw,
			String status,
			String sort,
			boolean mine,
			int page,
			SiteUser currentUser) {
		PageRequest pageable = createListPageRequest(page, sort);

		if (mine && currentUser != null) {
			return this.tradeItemRepository.findBySeller_UserIdAndHiddenFalse(currentUser.getId(), pageable);
		}

		Page<TradeItem> statusFiltered = getListByStatus(status, pageable);
		if (statusFiltered != null) {
			return statusFiltered;
		}

		return this.tradeItemRepository.search(normalize(category), normalize(kw), pageable);
	}

	public List<String> getCatalogCategories() {
		return this.tradeCatalogService.getCategories();
	}

	public List<TradeCatalogItem> getCatalogItems() {
		return this.tradeCatalogService.getCatalogItems();
	}

	public TradeItem getTradeItem(Integer id) {
		return this.tradeItemRepository.findDetailById(id)
				.orElseThrow(() -> new DataNotFoundException("trade item not found"));
	}

	public Page<TradeItem> getAdminList(int page, int size) {
		return getAdminList(page, size, "");
	}

	public Page<TradeItem> getAdminList(int page, int size, String kw) {
		int safePage = Math.max(page, 0);
		int safeSize = Math.max(size, 1);
		PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Order.desc("id")));
		String keyword = normalize(kw);
		if (keyword.isBlank()) {
			return this.tradeItemRepository.findAll(pageable);
		}
		return this.tradeItemRepository.findBySeller_UsernameContainingIgnoreCase(keyword, pageable);
	}

	public List<TradeItem> getItemsBySeller(SiteUser seller) {
		return this.tradeItemRepository.findBySeller_UserIdOrderByIdDesc(seller.getId());
	}

	@Transactional
	public TradeItem create(TradeItemForm form, SiteUser seller) {
		TradeCatalogItem catalogItem = this.tradeCatalogService.getCatalogItem(form.getCatalogItemId());
		TradeUser tradeSeller = this.tradeUserProfileService.sync(seller);

		TradeItem tradeItem = new TradeItem();
		tradeItem.setSeller(tradeSeller);
		tradeItem.setCatalogItem(catalogItem);
		tradeItem.setTitle(catalogItem.getItemName());
		tradeItem.setImageUrl(catalogItem.getImageUrl());
		tradeItem.setCategory(catalogItem.getCategory());
		tradeItem.setSubCategory(catalogItem.getItemName());
		tradeItem.setOptions(normalize(form.getOptions()));
		tradeItem.setPrice(form.getPrice());
		tradeItem.setStatus(TradeItem.STATUS_ON_SALE);
		return this.tradeItemRepository.save(tradeItem);
	}

	@Transactional
	public void purchase(Integer itemId, SiteUser buyer) {
		TradeItem tradeItem = this.tradeItemRepository.findLockedById(itemId)
				.orElseThrow(() -> new DataNotFoundException("trade item not found"));

		validatePurchasable(tradeItem);
		validateNotOwner(tradeItem, buyer);

		SiteUser buyerUser = this.userRepository.findById(buyer.getId())
				.orElseThrow(() -> new DataNotFoundException("buyer not found"));
		SiteUser sellerUser = this.userRepository.findById(tradeItem.getSeller().getUserId())
				.orElseThrow(() -> new DataNotFoundException("seller not found"));

		validateBuyerGold(buyerUser, tradeItem);

		TradeUser tradeBuyer = this.tradeUserProfileService.sync(buyerUser);
		this.tradeUserProfileService.sync(sellerUser);

		buyerUser.setGold(buyerUser.getGold() - tradeItem.getPrice());
		sellerUser.setGold(sellerUser.getGold() + tradeItem.getPrice());
		this.userRepository.save(buyerUser);
		this.userRepository.save(sellerUser);

		TradeTransaction tradeTransaction = new TradeTransaction();
		tradeTransaction.setItem(tradeItem);
		tradeTransaction.setBuyer(tradeBuyer);
		tradeTransaction.setSeller(tradeItem.getSeller());
		tradeTransaction.setPrice(tradeItem.getPrice());
		tradeTransaction.setStatus(TradeTransaction.STATUS_COMPLETED);

		this.tradeTransactionRepository.save(tradeTransaction);
		tradeItem.markSoldOut();
	}

	@Transactional
	public void hideByAdmin(Integer itemId) {
		TradeItem tradeItem = getTradeItemEntity(itemId);
		tradeItem.markHidden();
	}

	@Transactional
	public void deleteByAdmin(Integer itemId) {
		TradeItem tradeItem = getTradeItemEntity(itemId);
		this.tradeTransactionRepository.deleteByItem_Id(tradeItem.getId());
		this.tradeItemRepository.delete(tradeItem);
	}

	@Transactional
	public void deleteBySeller(Integer itemId, SiteUser seller) {
		TradeItem tradeItem = getTradeItemEntity(itemId);
		validateOwner(tradeItem, seller);
		validateSellerDeletionAllowed(tradeItem);
		this.tradeTransactionRepository.deleteByItem_Id(tradeItem.getId());
		this.tradeItemRepository.delete(tradeItem);
	}

	public List<TradeTransaction> getMyPurchases(SiteUser user) {
		return this.tradeTransactionRepository.findByBuyer_UserId(user.getId());
	}

	public List<TradeTransaction> getPurchaseHistory(SiteUser user) {
		return this.tradeTransactionRepository.findPurchaseHistoryByBuyerUserId(user.getId());
	}

	public List<TradeTransaction> getSalesHistory(SiteUser user) {
		return this.tradeTransactionRepository.findSalesHistoryBySellerUserId(user.getId());
	}

	private PageRequest createListPageRequest(int page, String sort) {
		return PageRequest.of(page, PAGE_SIZE, resolveListSort(sort));
	}

	private Sort resolveListSort(String sort) {
		if ("priceAsc".equals(sort)) {
			return Sort.by("price").ascending();
		}
		if ("priceDesc".equals(sort)) {
			return Sort.by("price").descending();
		}
		return Sort.by("id").descending();
	}

	private Page<TradeItem> getListByStatus(String status, PageRequest pageable) {
		if (isSellingStatus(status)) {
			return this.tradeItemRepository.findByStatusAndHiddenFalse(TradeItem.STATUS_ON_SALE, pageable);
		}
		if (isSoldStatus(status)) {
			return this.tradeItemRepository.findByStatusAndHiddenFalse(TradeItem.STATUS_SOLD_OUT, pageable);
		}
		return null;
	}

	private boolean isSellingStatus(String status) {
		return "SELLING".equals(status);
	}

	private boolean isSoldStatus(String status) {
		return "SOLD".equals(status);
	}

	private void validatePurchasable(TradeItem tradeItem) {
		if (tradeItem.isSoldOut()) {
			throw new TradeItemSoldOutException("이미 판매완료된 상품입니다.");
		}
		if (tradeItem.getPrice() == null) {
			throw new IllegalStateException("가격이 등록되지 않은 상품은 구매할 수 없습니다.");
		}
	}

	private void validateNotOwner(TradeItem tradeItem, SiteUser buyer) {
		if (tradeItem.getSeller() != null && tradeItem.getSeller().getUserId().equals(buyer.getId())) {
			throw new IllegalStateException("본인 상품은 구매할 수 없습니다.");
		}
	}

	private void validateBuyerGold(SiteUser buyerUser, TradeItem tradeItem) {
		if (buyerUser.getGold() < tradeItem.getPrice()) {
			throw new IllegalStateException("골드가 부족합니다. 현재 보유 골드를 확인해 주세요.");
		}
	}

	private void validateOwner(TradeItem tradeItem, SiteUser user) {
		if (tradeItem.getSeller() == null || !tradeItem.getSeller().getUserId().equals(user.getId())) {
			throw new IllegalStateException("본인 거래글만 삭제할 수 있습니다.");
		}
	}

	private void validateSellerDeletionAllowed(TradeItem tradeItem) {
		if (tradeItem.isSoldOut()) {
			throw new IllegalStateException("판매 완료된 거래글은 삭제할 수 없습니다.");
		}
	}

	private TradeItem getTradeItemEntity(Integer itemId) {
		return this.tradeItemRepository.findById(itemId)
				.orElseThrow(() -> new DataNotFoundException("trade item not found"));
	}

	private String normalize(String value) {
		return value == null ? "" : value.trim();
	}
}
