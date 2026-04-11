package com.mysite.sbb.mypage.controller;

import java.security.Principal;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mysite.sbb.mypage.dto.MyPageDtos;
import com.mysite.sbb.mypage.service.MyPageService;
import com.mysite.sbb.user.SiteUser;
import com.mysite.sbb.user.UserService;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
@RequestMapping({ "/api/mypage", "/api/simulator" })
public class MyPageApiController {

	private final MyPageService myPageService;
	private final UserService userService;

	@GetMapping("/dashboard")
	@PreAuthorize("isAuthenticated()")
	public MyPageDtos.MyPageDashboardResponse dashboard(Principal principal) {
		return this.myPageService.getDashboard(getCurrentUser(principal));
	}

	@GetMapping("/items")
	@PreAuthorize("isAuthenticated()")
	public MyPageDtos.ItemCatalogResponse slotCatalog(
			@RequestParam("slotCode") String slotCode,
			@RequestParam(value = "ownedOnly", defaultValue = "false") boolean ownedOnly,
			@RequestParam(value = "keyword", defaultValue = "") String keyword,
			Principal principal) {
		return this.myPageService.getSlotCatalog(getCurrentUser(principal), slotCode, ownedOnly, keyword);
	}

	@PostMapping("/simulator")
	@PreAuthorize("isAuthenticated()")
	public MyPageDtos.SimulatorResponse simulate(
			@RequestBody(required = false) MyPageDtos.SimulatorStateRequest request,
			Principal principal) {
		return this.myPageService.simulate(getCurrentUser(principal), request);
	}

	@PostMapping("/recommendations")
	@PreAuthorize("isAuthenticated()")
	public MyPageDtos.GrowthRecommendationResponse recommend(
			@RequestBody(required = false) MyPageDtos.SimulatorStateRequest request,
			Principal principal) {
		return this.myPageService.recommendGrowth(getCurrentUser(principal), request);
	}

	@PostMapping("/sync")
	@ResponseStatus(HttpStatus.ACCEPTED)
	@PreAuthorize("hasRole('ADMIN')")
	public MyPageDtos.CatalogSyncResponse syncOfficialItems() {
		return this.myPageService.syncOfficialCatalog();
	}

	private SiteUser getCurrentUser(Principal principal) {
		return this.userService.getUser(principal.getName());
	}
}
