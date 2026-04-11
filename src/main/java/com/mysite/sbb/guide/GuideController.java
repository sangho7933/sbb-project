package com.mysite.sbb.guide;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/guide")
public class GuideController {

	private static final int MIN_STEP = 1;
	private static final int MAX_STEP = 8;

	private static final List<GuideStepMeta> GUIDE_STEPS = List.of(
			new GuideStepMeta(1, "설치", "게임 설치와 계정 준비"),
			new GuideStepMeta(2, "종족 (천족/마족)", "시작 종족 선택"),
			new GuideStepMeta(3, "직업 선택", "플레이 스타일에 맞는 직업 찾기"),
			new GuideStepMeta(4, "레벨업 가이드", "초반 성장 동선 정리"),
			new GuideStepMeta(5, "던전", "경험치와 장비 수급 핵심"),
			new GuideStepMeta(6, "거래소 활용", "장비와 재화를 효율적으로 거래"),
			new GuideStepMeta(7, "골드 수급", "초반 골드 관리와 수급 루트"),
			new GuideStepMeta(8, "45레벨 이후", "장비와 스펙 중심의 후반 운영"));

	@GetMapping
	public String index(
			@RequestParam(value = "step", defaultValue = "1") int step,
			Model model) {
		int selectedStep = normalizeStep(step);
		model.addAttribute("pageTitle", "뉴비 가이드 - A2C");
		model.addAttribute("activeNav", "guide");
		model.addAttribute("guideSteps", GUIDE_STEPS);
		model.addAttribute("selectedStep", selectedStep);
		return "guide/index";
	}

	@GetMapping("/steps/{step}")
	public String stepContent(@PathVariable("step") int step) {
		validateStep(step);
		return "guide/step" + step + " :: guideStepContent";
	}

	private int normalizeStep(int step) {
		if (step < MIN_STEP || step > MAX_STEP) {
			return MIN_STEP;
		}
		return step;
	}

	private void validateStep(int step) {
		if (step < MIN_STEP || step > MAX_STEP) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "가이드 단계를 찾을 수 없습니다.");
		}
	}

	public record GuideStepMeta(int number, String title, String subtitle) {
	}
}
