package com.mysite.sbb.home;

import java.security.Principal;

import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.mysite.sbb.DataNotFoundException;
import com.mysite.sbb.admin.AdminModeService;
import com.mysite.sbb.board.BoardCategory;
import com.mysite.sbb.skilltree.SkillTreeJob;
import com.mysite.sbb.user.SiteUser;
import com.mysite.sbb.user.UserLoginLogService;
import com.mysite.sbb.user.UserService;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;

@RequiredArgsConstructor
@ControllerAdvice
public class GlobalViewModelAdvice {

	private final UserService userService;
	private final UserLoginLogService userLoginLogService;
	private final AdminModeService adminModeService;

	@ModelAttribute("boardCategories")
	public BoardCategory[] boardCategories() {
		return BoardCategory.values();
	}

	@ModelAttribute("skillTreeJobs")
	public SkillTreeJob[] skillTreeJobs() {
		return SkillTreeJob.values();
	}

	@ModelAttribute("currentSiteUser")
	public SiteUser currentSiteUser(Principal principal) {
		if (principal == null) {
			return null;
		}
		try {
			return this.userService.getUser(principal.getName());
		} catch (DataNotFoundException exception) {
			return null;
		}
	}

	@ModelAttribute("adminUser")
	public boolean adminUser(Authentication authentication) {
		return this.adminModeService.isAdmin(authentication);
	}

	@ModelAttribute("adminMode")
	public boolean adminMode(Authentication authentication, HttpSession session) {
		return this.adminModeService.isAdminMode(authentication, session);
	}

	@ModelAttribute("todayLoginUserCount")
	public long todayLoginUserCount() {
		return this.userLoginLogService.countTodayDistinctUsers();
	}
}
