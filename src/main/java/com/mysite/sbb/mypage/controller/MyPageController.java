package com.mysite.sbb.mypage.controller;

import java.security.Principal;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.util.StringUtils;

import com.mysite.sbb.mypage.service.MyPageService;
import com.mysite.sbb.user.SiteUser;
import com.mysite.sbb.user.UserService;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Controller
public class MyPageController {

	private final MyPageService myPageService;
	private final UserService userService;

	@PreAuthorize("isAuthenticated()")
	@GetMapping("/mypage")
	public String myPage(Principal principal, Model model) {
		SiteUser user = this.userService.getUser(principal.getName());
		model.addAttribute("pageTitle", "마이페이지 - A2C");
		model.addAttribute("activeNav", "mypage");
		model.addAttribute("mypage", this.myPageService.getOverview(user));
		return "mypage/index";
	}

	@GetMapping("/trade/items/mypage")
	public String tradeMyPageShortcut(Principal principal) {
		if (principal == null) {
			return "redirect:/user/login?redirect=/mypage";
		}
		return "redirect:/mypage";
	}

	@PreAuthorize("isAuthenticated()")
	@PostMapping("/mypage/equip")
	public String equipOwnedItem(
			@RequestParam("itemId") Long itemId,
			Principal principal,
			RedirectAttributes redirectAttributes) {
		SiteUser user = this.userService.getUser(principal.getName());
		try {
			String message = this.myPageService.equipOwnedItem(user, itemId);
			redirectAttributes.addFlashAttribute("mypageMessage", message);
		} catch (ResponseStatusException exception) {
			redirectAttributes.addFlashAttribute(
					"mypageError",
					StringUtils.hasText(exception.getReason()) ? exception.getReason() : "장비를 장착하지 못했습니다.");
		}
		return "redirect:/mypage#owned-gear";
	}
}
