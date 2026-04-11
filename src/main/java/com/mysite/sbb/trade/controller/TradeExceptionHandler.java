package com.mysite.sbb.trade.controller;

import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.mysite.sbb.DataNotFoundException;

@ControllerAdvice(assignableTypes = TradeItemController.class)
public class TradeExceptionHandler {

	@ExceptionHandler(DataNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public String handleTradeNotFound(DataNotFoundException exception, Model model) {
		model.addAttribute("pageTitle", "거래 상품을 찾을 수 없습니다 - A2C");
		model.addAttribute("activeNav", "trade");
		model.addAttribute("message", exception.getMessage());
		return "trade/not_found";
	}
}
