package com.mysite.sbb.trade.controller;

import java.security.Principal;

import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.mysite.sbb.trade.entity.TradeItem;
import com.mysite.sbb.trade.service.TradeItemService;
import com.mysite.sbb.trade.service.TradeItemSoldOutException;
import com.mysite.sbb.user.SiteUser;
import com.mysite.sbb.user.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Controller
@RequestMapping("/trade/items")
public class TradeItemController {

	private final TradeItemService tradeItemService;
	private final UserService userService;

	@GetMapping
	public String list(Model model, @RequestParam(value = "page", defaultValue = "0") int page,
			@RequestParam(value = "category", defaultValue = "") String category,
			@RequestParam(value = "kw", defaultValue = "") String kw,
			@RequestParam(value = "status", defaultValue = "") String status,
			@RequestParam(value = "sort", defaultValue = "new") String sort,
			@RequestParam(value = "mine", defaultValue = "false") boolean mine, Principal principal) {

		if (requiresLogin(mine, principal)) {
			return redirectToLogin();
		}

		SiteUser currentUser = getCurrentUser(principal);
		Page<TradeItem> paging = this.tradeItemService.getList(category, kw, status, sort, mine, page, currentUser);

		populateList(model, currentUser, paging, category, kw, status, sort, mine);
		return "trade/list";
	}

	@PreAuthorize("isAuthenticated()")
	@GetMapping("/new")
	public String createForm(@ModelAttribute("tradeItemForm") TradeItemForm tradeItemForm, Principal principal,
			Model model) {
		populateForm(model, requireCurrentUser(principal), "상품 등록 - A2C");
		return "trade/form";
	}

	@PreAuthorize("isAuthenticated()")
	@PostMapping("/new")
	public String create(@Valid @ModelAttribute("tradeItemForm") TradeItemForm tradeItemForm,
			BindingResult bindingResult, Principal principal, Model model, RedirectAttributes redirectAttributes) {
		SiteUser currentUser = requireCurrentUser(principal);
		if (bindingResult.hasErrors()) {
			populateForm(model, currentUser, "상품 등록 - A2C");
			return "trade/form";
		}

		TradeItem tradeItem = this.tradeItemService.create(tradeItemForm, currentUser);
		redirectAttributes.addFlashAttribute("successMessage", "상품이 등록되었습니다.");
		return tradeItemDetailRedirect(tradeItem.getId());
	}

	@GetMapping("/{id}")
	public String detail(Model model, @PathVariable("id") Integer id, Principal principal) {
		SiteUser currentUser = getCurrentUser(principal);
		TradeItem tradeItem = this.tradeItemService.getTradeItem(id);
		populateDetail(model, tradeItem, currentUser);
		return "trade/detail";
	}

	@PreAuthorize("isAuthenticated()")
	@PostMapping("/{id}/purchase")
	public String purchase(@PathVariable("id") Integer id, Principal principal, RedirectAttributes redirectAttributes) {
		SiteUser buyer = requireCurrentUser(principal);
		try {
			this.tradeItemService.purchase(id, buyer);
			redirectAttributes.addFlashAttribute("successMessage", "구매가 완료되었습니다.");
		} catch (TradeItemSoldOutException | IllegalStateException exception) {
			redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
		}
		return tradeItemDetailRedirect(id);
	}

	@GetMapping("/purchases")
	public String purchases(Model model, Principal principal) {
		if (isAnonymous(principal)) {
			return redirectToLogin();
		}

		SiteUser currentUser = requireCurrentUser(principal);
		populatePurchases(model, currentUser);
		return "trade/purchases";
	}

	private void populateForm(Model model, SiteUser currentUser, String pageTitle) {
		populateTradePage(model, pageTitle, currentUser);
		model.addAttribute("categories", this.tradeItemService.getCatalogCategories());
		model.addAttribute("catalogItems", this.tradeItemService.getCatalogItems());
	}

	private void populateList(Model model, SiteUser currentUser, Page<TradeItem> paging, String category, String kw,
			String status, String sort, boolean mine) {
		populateTradePage(model, "거래게시판 - A2C", currentUser);
		model.addAttribute("paging", paging);
		model.addAttribute("categories", this.tradeItemService.getCatalogCategories());
		model.addAttribute("selectedCategory", category);
		model.addAttribute("kw", kw);
		model.addAttribute("status", status);
		model.addAttribute("sort", sort);
		model.addAttribute("mine", mine);
		model.addAttribute("tradeEnabled", currentUser != null);
	}

	private void populateDetail(Model model, TradeItem tradeItem, SiteUser currentUser) {
		boolean owner = isOwner(tradeItem, currentUser);
		populateTradePage(model, tradeItem.getTitle() + " - 거래 게시판 - A2C", currentUser);
		model.addAttribute("tradeItem", tradeItem);
		model.addAttribute("isOwner", owner);
		model.addAttribute("canPurchase", canPurchase(tradeItem, currentUser, owner));
	}

	private void populatePurchases(Model model, SiteUser currentUser) {
		populateTradePage(model, "구매 내역 - A2C", currentUser);
		model.addAttribute("transactions", this.tradeItemService.getMyPurchases(currentUser));
	}

	private void populateTradePage(Model model, String pageTitle, SiteUser currentUser) {
		model.addAttribute("pageTitle", pageTitle);
		model.addAttribute("activeNav", "trade");
		model.addAttribute("currentUser", currentUser);
	}

	private boolean requiresLogin(boolean mine, Principal principal) {
		return mine && isAnonymous(principal);
	}

	private boolean isAnonymous(Principal principal) {
		return principal == null;
	}

	private SiteUser requireCurrentUser(Principal principal) {
		SiteUser currentUser = getCurrentUser(principal);
		if (currentUser == null) {
			throw new IllegalStateException("authenticated user required");
		}
		return currentUser;
	}

	private SiteUser getCurrentUser(Principal principal) {
		if (principal == null) {
			return null;
		}
		return this.userService.getUser(principal.getName());
	}

	private boolean isOwner(TradeItem tradeItem, SiteUser currentUser) {
		return currentUser != null
				&& tradeItem.getSeller() != null
				&& tradeItem.getSeller().getUserId().equals(currentUser.getId());
	}

	private boolean canPurchase(TradeItem tradeItem, SiteUser currentUser, boolean owner) {
		return currentUser != null && !owner && !tradeItem.isSoldOut();
	}

	private String tradeItemDetailRedirect(Integer id) {
		return "redirect:/trade/items/" + id;
	}

	private String redirectToLogin() {
		return "redirect:/user/login";
	}
}
