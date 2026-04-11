package com.mysite.sbb.skilltree;

import java.util.Arrays;

public enum SkillTreeJob {
	GLADIATOR("gladiator", "검성", "⚔", "묵직한 연계와 근접 압박에 특화된 검성 스킬트리", "job-gladiator"),
	TEMPLAR("templar", "수호성", "🛡", "생존기와 파티 보호 루트를 연구하는 수호성 스킬트리", "job-templar"),
	RANGER("ranger", "궁성", "🏹", "거리 조절과 폭딜 타이밍을 설계하는 궁성 스킬트리", "job-ranger"),
	ASSASSIN("assassin", "살성", "🗡", "은신과 폭딜 연계를 극대화하는 살성 스킬트리", "job-assassin"),
	SORCERER("sorcerer", "마도성", "🔥", "광역 딜과 제어기를 조합하는 마도성 스킬트리", "job-sorcerer"),
	SPIRITMASTER("spiritmaster", "정령성", "🌪", "정령 운용과 디버프 루트를 정리하는 정령성 스킬트리", "job-spiritmaster"),
	CLERIC("cleric", "치유성", "💚", "힐 사이클과 보조 운용을 다루는 치유성 스킬트리", "job-cleric"),
	CHANTER("chanter", "호법성", "✨", "버프, 보호, 근접 지원 루트를 설계하는 호법성 스킬트리", "job-chanter");

	private final String slug;
	private final String label;
	private final String icon;
	private final String description;
	private final String cssClass;

	SkillTreeJob(String slug, String label, String icon, String description, String cssClass) {
		this.slug = slug;
		this.label = label;
		this.icon = icon;
		this.description = description;
		this.cssClass = cssClass;
	}

	public String getSlug() {
		return this.slug;
	}

	public String getLabel() {
		return this.label;
	}

	public String getIcon() {
		return this.icon;
	}

	public String getDescription() {
		return this.description;
	}

	public String getCssClass() {
		return this.cssClass;
	}

	public static SkillTreeJob fromSlug(String slug) {
		return Arrays.stream(values())
				.filter(job -> job.slug.equalsIgnoreCase(slug))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("unknown skilltree job slug: " + slug));
	}

	public static SkillTreeJob fromLabel(String label) {
		return Arrays.stream(values())
				.filter(job -> job.label.equals(label))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("unknown skilltree job: " + label));
	}

	public static SkillTreeJob fromPathValue(String value) {
		return Arrays.stream(values())
				.filter(job -> job.slug.equalsIgnoreCase(value) || job.label.equals(value))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("unknown skilltree job: " + value));
	}
}
