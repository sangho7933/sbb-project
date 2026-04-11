package com.mysite.sbb.mypage.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "MY_PAGE_ITEMS")
public class Item {

	@Id
	@Column(name = "ID")
	private Long id;

	@Column(name = "NAME", nullable = false, length = 200)
	private String name;

	@Column(name = "ICON", length = 500)
	private String icon;

	@Column(name = "GRADE", length = 50)
	private String grade;

	@Column(name = "GRADE_NAME", length = 100)
	private String gradeName;

	@Column(name = "CATEGORY_NAME", length = 200)
	private String categoryName;

	@Column(name = "EQUIP_CATEGORY", length = 200)
	private String equipCategory;

	@Lob
	@Column(name = "DESCRIPTION")
	private String description;

	@Lob
	@Column(name = "CLASS_NAMES_JSON")
	private String classNamesJson;

	@Column(name = "RACE_NAME", length = 100)
	private String raceName;

	@Column(name = "EQUIP_LEVEL")
	private Integer equipLevel;

	@Lob
	@Column(name = "MAIN_STATS_JSON")
	private String mainStatsJson;

	@Lob
	@Column(name = "SUB_STATS_JSON")
	private String subStatsJson;

	@Lob
	@Column(name = "SUB_SKILLS_JSON")
	private String subSkillsJson;

	@Column(name = "ENCHANT_LEVEL")
	private Integer enchantLevel;

	@Column(name = "MAX_ENCHANT_LEVEL")
	private Integer maxEnchantLevel;

	@Column(name = "SAFE_ENCHANT_LEVEL")
	private Integer safeEnchantLevel;

	@Column(name = "ENCHANTABLE")
	private Boolean enchantable;

	@Column(name = "MAGIC_STONE_SLOT_COUNT")
	private Integer magicStoneSlotCount;

	@Column(name = "GOD_STONE_SLOT_COUNT")
	private Integer godStoneSlotCount;

	@Lob
	@Column(name = "MAGIC_STONE_STAT_JSON")
	private String magicStoneStatJson;

	@Lob
	@Column(name = "GOD_STONE_STAT_JSON")
	private String godStoneStatJson;

	@Lob
	@Column(name = "SET_JSON")
	private String setJson;

	@Lob
	@Column(name = "SET_ITEM_JSON")
	private String setItemJson;

	@Column(name = "TRADABLE")
	private Boolean tradable;

	@Column(name = "TYPE", length = 50)
	private String type;

	@Column(name = "SLOT_CODE", length = 30)
	private String slotCode;

	@Column(name = "POWER_SCORE")
	private Double powerScore;

	@Column(name = "SYNCED_AT")
	private LocalDateTime syncedAt;
}
