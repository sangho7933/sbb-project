package com.mysite.sbb.user;

import java.util.Arrays;

public enum UserRace {

	ELYOS("elyos", "천족"),
	ASMODIAN("asmodian", "마족");

	private final String code;
	private final String label;

	UserRace(String code, String label) {
		this.code = code;
		this.label = label;
	}

	public String getCode() {
		return this.code;
	}

	public String getLabel() {
		return this.label;
	}

	public static UserRace defaultRace() {
		return ASMODIAN;
	}

	public static UserRace from(String value) {
		if (value == null || value.isBlank()) {
			return defaultRace();
		}

		String normalized = value.trim();
		return Arrays.stream(values())
				.filter(race -> race.code.equalsIgnoreCase(normalized) || race.label.equals(normalized))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("지원하지 않는 종족입니다."));
	}
}
