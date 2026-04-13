/*
 * 사용자 생성, 기본값 보정, 관리자 목록 조회를 담당하는 서비스이다.
 */
package com.mysite.sbb.user;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mysite.sbb.DataNotFoundException;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
/**
 * 사용자 저장 규칙과 관리자용 조회 보정 규칙을 한 곳에서 관리한다.
 */
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	@Transactional
	public SiteUser create(String username, String email, String password) {
		return create(username, email, password, UserRace.defaultRace().getCode());
	}

	@Transactional
	public SiteUser create(String username, String email, String password, String race) {
		UserRace userRace = resolveRace(race);
		SiteUser user = new SiteUser();
		user.setUsername(username);
		user.setEmail(email);
		user.setPassword(this.passwordEncoder.encode(password));
		user.setGold(1000);
		user.setRace(userRace.getLabel());
		this.userRepository.save(user);
		return user;
	}

	@Transactional
	public SiteUser ensureUser(String username, String email, String password) {
		return ensureUser(username, email, password, UserRace.defaultRace().getCode());
	}

	@Transactional
	public SiteUser ensureUser(String username, String email, String password, String race) {
		UserRace userRace = resolveRace(race);
		SiteUser user = this.userRepository.findByUsername(username).orElseGet(SiteUser::new);
		user.setUsername(username);
		user.setEmail(email);
		if (user.getPassword() == null || !this.passwordEncoder.matches(password, user.getPassword())) {
			user.setPassword(this.passwordEncoder.encode(password));
		}
		applyMissingGold(user);
		user.setRace(userRace.getLabel());
		return this.userRepository.save(user);
	}

	public SiteUser getUser(String username) {
		Optional<SiteUser> siteUser = this.userRepository.findByUsername(username);
		if (siteUser.isPresent()) {
			return siteUser.get();
		}
		throw new DataNotFoundException("siteuser not found");
	}

	public SiteUser getUser(Long id) {
		return this.userRepository.findById(id)
				.orElseThrow(() -> new DataNotFoundException("siteuser not found"));
	}

	public Page<SiteUser> getAdminList(int page, int size) {
		return getAdminList(page, size, "");
	}

	public Page<SiteUser> getAdminList(int page, int size, String kw) {
		PageRequest pageable = createAdminPageRequest(page, size);
		String keyword = sanitizeKeyword(kw);
		if (keyword.isBlank()) {
			return this.userRepository.findAll(pageable);
		}
		return this.userRepository.findByUsernameContainingIgnoreCase(keyword, pageable);
	}

	@Transactional
	public SiteUser save(SiteUser siteUser) {
		applyMissingRace(siteUser);
		return this.userRepository.save(siteUser);
	}

	@Transactional
	public int backfillDefaultRace() {
		return this.userRepository.updateBlankRace(UserRace.defaultRace().getLabel());
	}

	@Transactional
	public SiteUser suspendUser(Long userId, int days) {
		validateSuspensionDays(days);
		SiteUser siteUser = getUser(userId);
		validateSuspendable(siteUser);
		siteUser.setSuspensionDays(days);
		siteUser.setSuspendedUntil(LocalDateTime.now().plusDays(days));
		return this.userRepository.save(siteUser);
	}

	public List<SiteUser> getSuspendedUsers() {
		return this.userRepository.findBySuspendedUntilAfterOrderBySuspendedUntilAsc(LocalDateTime.now());
	}

	@Transactional
	public SiteUser releaseSuspension(Long userId) {
		SiteUser siteUser = getUser(userId);
		siteUser.setSuspendedUntil(null);
		siteUser.setSuspensionDays(null);
		return this.userRepository.save(siteUser);
	}

	// 입력 종족 값은 생성/보정 흐름 모두 같은 해석 규칙을 사용한다.
	private UserRace resolveRace(String race) {
		return UserRace.from(race);
	}

	// 관리자 검색어는 공백만 정리하고 빈 값은 그대로 둔다.
	private String sanitizeKeyword(String keyword) {
		return keyword == null ? "" : keyword.trim();
	}

	// 관리자 목록 페이지는 음수 요청을 허용하지 않는다.
	private int safePage(int page) {
		return Math.max(page, 0);
	}

	// 페이지 크기는 1 미만으로 내려가지 않게 맞춘다.
	private int safeSize(int size) {
		return Math.max(size, 1);
	}

	// 관리자 목록 페이징은 정렬과 보정 규칙을 같은 방식으로 재사용한다.
	private PageRequest createAdminPageRequest(int page, int size) {
		return PageRequest.of(safePage(page), safeSize(size), Sort.by(Sort.Order.desc("id")));
	}

	// 기존 사용자 보정 시 기본 골드가 비어 있는 경우만 채운다.
	private void applyMissingGold(SiteUser siteUser) {
		if (siteUser.getGold() == null) {
			siteUser.setGold(1000);
		}
	}

	// 저장 전 종족이 비어 있으면 기존 기본 종족 규칙을 적용한다.
	private void applyMissingRace(SiteUser siteUser) {
		if (siteUser.getRace() == null || siteUser.getRace().isBlank()) {
			siteUser.setRace(UserRace.defaultRace().getLabel());
		}
	}

	private void validateSuspensionDays(int days) {
		if (days != 1 && days != 7) {
			throw new IllegalStateException("지원하지 않는 정지 기간입니다.");
		}
	}

	private void validateSuspendable(SiteUser siteUser) {
		if ("admin".equalsIgnoreCase(siteUser.getUsername())) {
			throw new IllegalStateException("관리자 계정은 정지할 수 없습니다.");
		}
	}
}
