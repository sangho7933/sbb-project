package com.mysite.sbb.home;

import java.util.List;

public record FeaturedVideo(String slotLabel, String title, String summary, String youtubeId) {

	public static List<FeaturedVideo> defaultLineup() {
		return List.of(
				new FeaturedVideo("01", "마도성", "강력한 마법 화력으로 전장을 압박하는 원거리 딜러", "PhOBS3yML2Y"),
				new FeaturedVideo("02", "정령성", "정령 소환과 상태 이상으로 흐름을 흔드는 전략형 클래스", "g0F3dpS0LGI"),
				new FeaturedVideo("03", "치유성", "회복과 보호막으로 파티를 안정시키는 핵심 지원 직업", "k-p6AAACrUI"),
				new FeaturedVideo("04", "호법성", "버프와 보조 전투를 겸하는 하이브리드 서포터", "TmSVxYo7THQ"),
				new FeaturedVideo("05", "궁성", "정확한 타격과 기동력으로 압박하는 원거리 클래스", "wbLjVMlN5Zk"),
				new FeaturedVideo("06", "살성", "은신과 폭딜로 순간 전투를 지배하는 암살형 직업", "172TjGugIpw"),
				new FeaturedVideo("07", "수호성", "강한 방어력으로 전선을 지키는 탱커 포지션", "FuRqVQKtAnY"),
				new FeaturedVideo("08", "검성", "연속 콤보와 묵직한 타격을 겸비한 근거리 딜러", "KbkINT4w6uw"));
	}
}
