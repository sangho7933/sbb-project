package com.mysite.sbb.trade.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "TRADE_CATALOG_ITEMS")
public class TradeCatalogItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 50)
	private String category;

	@Column(nullable = false, length = 100)
	private String itemName;

	@Column(length = 255)
	private String imageUrl;

	@Column(nullable = false)
	private Integer displayOrder;
}