package com.mysite.sbb.trade.controller;

import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Validated
public class TradeItemForm {

	@NotBlank(message = "카테고리를 선택해 주세요.")
	private String category;

	@NotNull(message = "아이템을 선택해 주세요.")
	private Long catalogItemId;

	@Size(max = 500, message = "옵션은 500자 이하로 입력해 주세요.")
	private String options;

	@NotNull(message = "가격을 입력해 주세요.")
	@PositiveOrZero(message = "가격은 0골드 이상이어야 합니다.")
	private Integer price;
}