package com.mysite.sbb.user;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Component
public class UserLoginSuccessListener {

	private final UserLoginLogService userLoginLogService;

	@EventListener
	public void handleAuthenticationSuccess(AuthenticationSuccessEvent event) {
		Authentication authentication = event.getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()) {
			return;
		}

		String username = authentication.getName();
		if (username == null || username.isBlank() || "anonymousUser".equalsIgnoreCase(username)) {
			return;
		}

		this.userLoginLogService.recordLoginSuccess(username);
	}
}
