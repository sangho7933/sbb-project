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
import jakarta.persistence.Lob;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "USER_STATS", uniqueConstraints = {
		@UniqueConstraint(name = "UK_USER_STATS_USER", columnNames = { "USER_ID" })
})
public class UserStat {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "ID")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "USER_ID", nullable = false)
	private SiteUser user;

	@Column(name = "TOTAL_ATTACK_MIN")
	private Double totalAttackMin;

	@Column(name = "TOTAL_ATTACK_MAX")
	private Double totalAttackMax;

	@Column(name = "TOTAL_DEFENSE")
	private Double totalDefense;

	@Column(name = "TOTAL_ACCURACY")
	private Double totalAccuracy;

	@Column(name = "TOTAL_CRITICAL")
	private Double totalCritical;

	@Column(name = "TOTAL_HEALTH")
	private Double totalHealth;

	@Column(name = "TOTAL_MAGIC_BOOST")
	private Double totalMagicBoost;

	@Column(name = "TOTAL_MAGIC_ACCURACY")
	private Double totalMagicAccuracy;

	@Column(name = "TOTAL_PVE_ATTACK")
	private Double totalPveAttack;

	@Column(name = "TOTAL_HEALING_BOOST")
	private Double totalHealingBoost;

	@Column(name = "POWER_SCORE")
	private Double powerScore;

	@Lob
	@Column(name = "SUMMARY_JSON")
	private String summaryJson;

	@Column(name = "UPDATED_AT", nullable = false)
	private LocalDateTime updatedAt;
}
