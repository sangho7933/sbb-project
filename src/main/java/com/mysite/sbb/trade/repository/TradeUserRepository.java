package com.mysite.sbb.trade.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mysite.sbb.trade.entity.TradeUser;

public interface TradeUserRepository extends JpaRepository<TradeUser, Long> {

	Optional<TradeUser> findByUserId(Long userId);
}
