/*
 * 로그인과 회원가입 화면 진입 및 기본 검증 흐름을 담당한다.
 */
package com.mysite.sbb.user;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Controller
@RequestMapping("/user")
/**
 * 인증 화면에서 필요한 공통 모델과 가입 요청을 조합한다.
 */
public class AuthController {

	private final UserService userService;

	@GetMapping("/signup")
	public String signup(UserCreateForm userCreateForm, Model model) {
		populateSignupModel(model);
		return "user_signup";
	}

	@PostMapping("/signup")
	public String signup(@Valid UserCreateForm userCreateForm, BindingResult bindingResult, Model model) {
		populateSignupModel(model);
		if (bindingResult.hasErrors()) {
			return "user_signup";
		}

		if (!userCreateForm.getPassword1().equals(userCreateForm.getPassword2())) {
			bindingResult.rejectValue("password2", "passwordInCorrect", "비밀번호가 서로 일치하지 않습니다.");
			return "user_signup";
		}

		try {
			this.userService.create(
					userCreateForm.getUsername(),
					userCreateForm.getEmail(),
					userCreateForm.getPassword1(),
					userCreateForm.getRace());
		} catch (DataIntegrityViolationException e) {
			e.printStackTrace();
			bindingResult.reject("signupFailed", "이미 등록된 사용자입니다.");
			return "user_signup";
		} catch (Exception e) {
			e.printStackTrace();
			bindingResult.reject("signupFailed", e.getMessage());
			return "user_signup";
		}

		return "redirect:/";
	}

	@GetMapping("/login")
	public String login(@RequestParam(value = "message", defaultValue = "") String loginErrorMessage, Model model) {
		model.addAttribute("pageTitle", "로그인 - A2C");
		model.addAttribute("activeNav", "auth");
		model.addAttribute("loginErrorMessage", loginErrorMessage == null ? "" : loginErrorMessage.trim());
		return "user_login";
	}

	// 회원가입 GET/POST가 같은 화면 메타를 쓰도록 공통 모델을 채운다.
	private void populateSignupModel(Model model) {
		model.addAttribute("pageTitle", "회원가입 - A2C");
		model.addAttribute("activeNav", "auth");
		model.addAttribute("races", UserRace.values());
	}
}
