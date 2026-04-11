package com.mysite.sbb.mypage.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mysite.sbb.mypage.entity.UserStat;
import com.mysite.sbb.user.SiteUser;

public interface UserStatRepository extends JpaRepository<UserStat, Long> {

	Optional<UserStat> findByUser(SiteUser user);
}
