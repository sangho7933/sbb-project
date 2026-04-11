package com.mysite.sbb.trade.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "TRADE_TRANSACTIONS")
public class TradeTransaction {

	public static final String STATUS_COMPLETED = "구매완료";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "ID", nullable = false)
	private Integer id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "ITEM_ID", nullable = false)
	private TradeItem item;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "BUYER_ID", nullable = false, referencedColumnName = "ID")
	private TradeUser buyer;
	
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "SELLER_ID", nullable = false, referencedColumnName = "ID")
	private TradeUser seller;

	@Column(name = "PRICE", nullable = false)
	private Integer price;

	@Column(name = "STATUS", nullable = false, length = 20)
	private String status;

	@Column(name = "CREATED_AT", nullable = false)
	private LocalDateTime createdAt;

	@PrePersist
	public void onCreate() {
		if (this.status == null || this.status.isBlank()) {
			this.status = STATUS_COMPLETED;
		}
		if (this.createdAt == null) {
			this.createdAt = LocalDateTime.now();
		}
	}
}
