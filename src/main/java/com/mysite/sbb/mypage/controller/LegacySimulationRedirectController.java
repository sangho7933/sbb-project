package com.mysite.sbb.mypage.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LegacySimulationRedirectController {

	@GetMapping("/equipment-simulation")
	public String legacyEquipmentSimulation() {
		return "redirect:/simulator";
	}
}
