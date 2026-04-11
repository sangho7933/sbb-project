package com.mysite.sbb.user;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class UserLoginLogService {

	private final UserLoginLogRepository userLoginLogRepository;
	private final UserRepository userRepository;

	@Transactional
	public void recordLoginSuccess(String username) {
		if (username == null || username.isBlank()) {
			return;
		}

		this.userRepository.findByUsername(username)
				.ifPresent(user -> saveLog(user, LocalDateTime.now()));
	}

	public long countTodayDistinctUsers() {
		LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
		return this.userLoginLogRepository.countDistinctUsersBetween(startOfToday, startOfToday.plusDays(1));
	}

	@Transactional
	public UserLoginLog saveLog(SiteUser user, LocalDateTime loggedAt) {
		UserLoginLog loginLog = new UserLoginLog();
		loginLog.setUser(user);
		loginLog.setLoggedAt(loggedAt);
		return this.userLoginLogRepository.save(loginLog);
	}
}
