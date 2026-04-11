package com.mysite.sbb.mypage.entity;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum EquipmentSlot {

	WEAPON("weapon", "무기", 1, 1.45d, "무기", List.of(
			"weapon", "무기", "검", "대검", "장검", "단검", "활", "bow", "오브", "보주", "법서", "창", "메이스", "곤봉")),
	GUARDER("guarder", "가더", 2, 1.28d, "가더", List.of(
			"guarder", "가더")),
	RING("ring", "반지", 3, 1.12d, "장신구", List.of(
			"ring", "반지")),
	EARRING("earring", "귀걸이", 4, 1.1d, "장신구", List.of(
			"earring", "귀걸이", "ear")),
	NECKLACE("necklace", "목걸이", 5, 1.09d, "장신구", List.of(
			"necklace", "목걸이", "pendant")),
	HELMET("helmet", "투구", 6, 0.82d, "방어구", List.of(
			"helmet", "투구", "모자", "머리", "head")),
	SHOULDER("shoulder", "견갑", 7, 0.88d, "방어구", List.of(
			"shoulder", "견갑", "shoulderpad", "어깨")),
	ARMOR("armor", "상의", 8, 0.96d, "방어구", List.of(
			"armor", "상의", "갑옷", "로브", "흉갑", "상의갑", "chest", "robe", "tunic")),
	GLOVES("gloves", "장갑", 9, 0.79d, "방어구", List.of(
			"gloves", "장갑", "건틀릿", "손", "gauntlet")),
	PANTS("pants", "하의", 10, 0.91d, "방어구", List.of(
			"pants", "하의", "바지", "각반", "다리", "legs", "greaves")),
	BOOTS("boots", "신발", 11, 0.77d, "방어구", List.of(
			"boots", "신발", "장화", "부츠", "shoes", "foot"));

	private final String code;
	private final String label;
	private final int displayOrder;
	private final double recommendationWeight;
	private final String priorityGroup;
	private final List<String> keywords;

	EquipmentSlot(
			String code,
			String label,
			int displayOrder,
			double recommendationWeight,
			String priorityGroup,
			List<String> keywords) {
		this.code = code;
		this.label = label;
		this.displayOrder = displayOrder;
		this.recommendationWeight = recommendationWeight;
		this.priorityGroup = priorityGroup;
		this.keywords = keywords;
	}

	public String getCode() {
		return this.code;
	}

	public String getLabel() {
		return this.label;
	}

	public int getDisplayOrder() {
		return this.displayOrder;
	}

	public double getRecommendationWeight() {
		return this.recommendationWeight;
	}

	public String getPriorityGroup() {
		return this.priorityGroup;
	}

	public static List<EquipmentSlot> orderedValues() {
		return Arrays.stream(values())
				.sorted((left, right) -> Integer.compare(left.displayOrder, right.displayOrder))
				.toList();
	}

	public static List<EquipmentSlot> slotsInGroup(String groupLabel) {
		String normalizedGroup = normalize(groupLabel);
		if (normalizedGroup.isBlank()) {
			return List.of();
		}
		return orderedValues().stream()
				.filter(slot -> normalize(slot.priorityGroup).equals(normalizedGroup))
				.toList();
	}

	public static Optional<EquipmentSlot> fromCode(String code) {
		if (code == null || code.isBlank()) {
			return Optional.empty();
		}

		String normalized = normalize(code);
		return Arrays.stream(values())
				.filter(slot -> normalize(slot.code).equals(normalized)
						|| aliasFor(slot).contains(normalized))
				.findFirst();
	}

	public static Optional<EquipmentSlot> infer(String categoryName, String equipCategory) {
		String normalizedCategory = normalize(categoryName);
		String normalizedEquipCategory = normalize(equipCategory);

		return Arrays.stream(values())
				.filter(slot -> slot.matches(normalizedCategory) || slot.matches(normalizedEquipCategory))
				.findFirst();
	}

	private boolean matches(String normalizedValue) {
		if (normalizedValue.isBlank()) {
			return false;
		}
		return this.keywords.stream()
				.map(EquipmentSlot::normalize)
				.anyMatch(normalizedValue::contains);
	}

	private static List<String> aliasFor(EquipmentSlot slot) {
		return switch (slot) {
		case ARMOR -> List.of("top", "상의");
		case BOOTS -> List.of("shoes", "신발");
		case EARRING -> List.of("earring", "귀걸이");
		case GUARDER -> List.of("guarder", "가더");
		case SHOULDER -> List.of("shoulder", "견갑");
		default -> List.of(slot.code, slot.label);
		};
	}

	private static String normalize(String value) {
		if (value == null) {
			return "";
		}
		return value.toLowerCase(Locale.ROOT)
				.replace(" ", "")
				.replace("-", "")
				.replace("_", "")
				.replace("(", "")
				.replace(")", "")
				.trim();
	}
}
