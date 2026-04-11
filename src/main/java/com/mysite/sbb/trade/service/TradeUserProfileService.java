package com.mysite.sbb.trade.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mysite.sbb.trade.entity.TradeUser;
import com.mysite.sbb.trade.repository.TradeUserRepository;
import com.mysite.sbb.user.SiteUser;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class TradeUserProfileService {

	private final TradeUserRepository tradeUserRepository;

	@Transactional
	public TradeUser sync(SiteUser siteUser) {
		TradeUser tradeUser = this.tradeUserRepository.findByUserId(siteUser.getId()).orElseGet(() -> {
			TradeUser created = new TradeUser();
			created.setUserId(siteUser.getId());
			return created;
		});

		tradeUser.setUsername(siteUser.getUsername());
		tradeUser.setEmail(siteUser.getEmail());
		return this.tradeUserRepository.save(tradeUser);
	}
}
