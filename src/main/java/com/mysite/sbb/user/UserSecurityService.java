/*
 * 로그인 시 사용자 정보와 권한 목록을 스프링 시큐리티 형식으로 변환한다.
 */
package com.mysite.sbb.user;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
/**
 * 저장된 사용자 정보를 인증용 UserDetails로 매핑한다.
 */
public class UserSecurityService implements UserDetailsService {

	private final UserRepository userRepository;

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		Optional<SiteUser> optionalSiteUser = this.userRepository.findByUsername(username);
		if (optionalSiteUser.isEmpty()) {
			throw new UsernameNotFoundException("사용자를 찾을수 없습니다.");
		}
		SiteUser siteUser = optionalSiteUser.get();
		return new User(siteUser.getUsername(), siteUser.getPassword(), buildAuthorities(siteUser));
	}

	// 기본 USER 권한에 관리자 사용자명 규칙을 추가로 조합한다.
	private List<GrantedAuthority> buildAuthorities(SiteUser siteUser) {
		List<GrantedAuthority> authorities = new ArrayList<>();
		authorities.add(new SimpleGrantedAuthority(UserRole.USER.getValue()));
		if (isAdminUser(siteUser)) {
			authorities.add(new SimpleGrantedAuthority(UserRole.ADMIN.getValue()));
		}
		return authorities;
	}

	// 관리자 권한 부여는 기존 admin 사용자명 규칙만 따른다.
	private boolean isAdminUser(SiteUser siteUser) {
		return "admin".equalsIgnoreCase(siteUser.getUsername());
	}
}
