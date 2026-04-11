package com.mysite.sbb.mypage.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SimulatorController {

	@PreAuthorize("isAuthenticated()")
	@GetMapping("/simulator")
	public String simulator(Model model) {
		model.addAttribute("pageTitle", "장비 시뮬레이터 - A2C");
		model.addAttribute("activeNav", "simulator");
		return "simulator/index";
	}
}
