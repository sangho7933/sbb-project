package com.mysite.sbb.board;

import java.util.Arrays;

public enum BoardCategory {
	FREE_BOARD("free", "자유게시판", "자유롭게 대화하고 정보를 나누는 기본 게시판", false),
	GUILD_RECRUITMENT("guild", "길드원모집", "함께 플레이할 길드와 파티원을 찾는 공간", false),
	BOSS_GUIDE("boss", "보스공략", "보스 패턴과 공략 루트를 정리하는 전략 게시판", true),
	HIGHLIGHT("highlight", "하이라이트", "멋진 장면과 인상적인 플레이를 공유하는 게시판", true);

	private final String code;
	private final String label;
	private final String description;
	private final boolean mediaUploadSupported;

	BoardCategory(String code, String label, String description, boolean mediaUploadSupported) {
		this.code = code;
		this.label = label;
		this.description = description;
		this.mediaUploadSupported = mediaUploadSupported;
	}

	public String getCode() {
		return this.code;
	}

	public String getLabel() {
		return this.label;
	}

	public String getDescription() {
		return this.description;
	}

	public boolean isMediaUploadSupported() {
		return this.mediaUploadSupported;
	}

	public static BoardCategory fromCode(String code) {
		return Arrays.stream(values())
				.filter(category -> category.code.equalsIgnoreCase(code))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("unknown board category: " + code));
	}
}
