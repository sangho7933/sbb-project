package com.mysite.sbb.user;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class SiteUser {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(unique = true)
	private String username;

	private String password;

	@Column(unique = true)
	private String email;

	@Column(nullable = false, columnDefinition = "INTEGER DEFAULT 1000")
	private Integer gold = 1000;

	@Column(length = 20, columnDefinition = "VARCHAR(20) DEFAULT '마족'")
	private String race = UserRace.defaultRace().getLabel();

	@Column(name = "suspended_until")
	private LocalDateTime suspendedUntil;

	@Column(name = "suspension_days")
	private Integer suspensionDays;

	public Integer getGold() {
		return this.gold == null ? 1000 : this.gold;
	}

	public String getRace() {
		return this.race == null || this.race.isBlank()
				? UserRace.defaultRace().getLabel()
				: this.race.trim();
	}

	public boolean isSuspended() {
		return this.suspendedUntil != null && this.suspendedUntil.isAfter(LocalDateTime.now());
	}

	public String getSuspensionLabel() {
		if (!isSuspended()) {
			return "정상";
		}
		if (this.suspensionDays != null && this.suspensionDays > 0) {
			return this.suspensionDays + "일 정지";
		}
		return "정지";
	}

	public String getSuspensionMessage() {
		if (!isSuspended()) {
			return "정상 계정입니다.";
		}
		if (this.suspensionDays != null && this.suspensionDays > 0) {
			return this.suspensionDays + "일 정지된 계정입니다.";
		}
		return "정지된 계정입니다.";
	}
}
