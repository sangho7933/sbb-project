package com.mysite.sbb.trade.entity;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "TRADE_ITEMS")
public class TradeItem {

	public static final String STATUS_ON_SALE = "판매중";
	public static final String STATUS_SOLD_OUT = "판매완료";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "ID", nullable = false)
	private Integer id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "USER_ID", nullable = false, referencedColumnName = "ID")
	private TradeUser seller;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "CATALOG_ITEM_ID")
	private TradeCatalogItem catalogItem;

	@Column(name = "TITLE", nullable = false, length = 100)
	private String title;

	@Column(name = "IMAGE_URL", length = 255)
	private String imageUrl;

	@Column(name = "CATEGORY", length = 50)
	private String category;

	@Column(name = "SUB_CATEGORY", length = 50)
	private String subCategory;

	@Column(name = "OPTIONS", length = 500)
	private String options;

	@Column(name = "PRICE")
	private Integer price;

	@Column(name = "STATUS", length = 20)
	private String status;

	@Column(name = "HIDDEN", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private boolean hidden;

	@OneToMany(mappedBy = "item", cascade = CascadeType.ALL)
	private List<TradeTransaction> transactions = new ArrayList<>();

	@PrePersist
	public void onCreate() {
		if (this.status == null || this.status.isBlank()) {
			this.status = STATUS_ON_SALE;
		}
	}

	public String getStatus() {
		return (this.status == null || this.status.isBlank()) ? STATUS_ON_SALE : this.status;
	}

	public boolean isSoldOut() {
		return STATUS_SOLD_OUT.equals(getStatus());
	}

	public void markSoldOut() {
		this.status = STATUS_SOLD_OUT;
	}

	public void markHidden() {
		this.hidden = true;
	}
}
