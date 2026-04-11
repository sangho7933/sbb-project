package com.mysite.sbb.mypage.entity;

import java.time.LocalDateTime;

import com.mysite.sbb.user.SiteUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "USER_EQUIPMENT", uniqueConstraints = {
		@UniqueConstraint(name = "UK_USER_EQUIPMENT_SLOT", columnNames = { "USER_ID", "SLOT_CODE" })
})
public class UserEquipment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "ID")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "USER_ID", nullable = false)
	private SiteUser user;

	@Column(name = "SLOT_CODE", nullable = false, length = 30)
	private String slotCode;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "ITEM_ID")
	private Item item;

	@Column(name = "UPDATED_AT", nullable = false)
	private LocalDateTime updatedAt;
}
