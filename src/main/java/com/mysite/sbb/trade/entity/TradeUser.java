package com.mysite.sbb.trade.entity;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "TRADE_USERS")
public class TradeUser {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "ID", nullable = false)
	private Long id;

	@Column(name = "USER_ID", nullable = false, unique = true)
	private Long userId;

	@Column(name = "USERNAME", length = 50)
	private String username;

	@Column(name = "EMAIL", length = 100)
	private String email;

	@OneToMany(mappedBy = "seller")
	private List<TradeItem> itemsForSale = new ArrayList<>();

	@OneToMany(mappedBy = "buyer")
	private List<TradeTransaction> purchases = new ArrayList<>();
}
