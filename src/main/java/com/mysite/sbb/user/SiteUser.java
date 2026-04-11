package com.mysite.sbb.user;

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

	public Integer getGold() {
		return this.gold == null ? 1000 : this.gold;
	}

	public String getRace() {
		return this.race == null || this.race.isBlank()
				? UserRace.defaultRace().getLabel()
				: this.race.trim();
	}
}
