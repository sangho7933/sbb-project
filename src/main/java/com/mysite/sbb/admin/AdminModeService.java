package com.mysite.sbb.admin;

import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

@Service
public class AdminModeService {

	public static final String SESSION_KEY = "adminMode";

	public boolean isAdmin(Authentication authentication) {
		if (authentication == null) {
			return false;
		}
		for (GrantedAuthority authority : authentication.getAuthorities()) {
			if ("ROLE_ADMIN".equals(authority.getAuthority())) {
				return true;
			}
		}
		return false;
	}

	public boolean isAdminMode(Authentication authentication, HttpSession session) {
		return isAdmin(authentication) && session != null && Boolean.TRUE.equals(session.getAttribute(SESSION_KEY));
	}

	public void enable(HttpSession session) {
		if (session != null) {
			session.setAttribute(SESSION_KEY, Boolean.TRUE);
		}
	}

	public void disable(HttpSession session) {
		if (session != null) {
			session.removeAttribute(SESSION_KEY);
		}
	}
}
