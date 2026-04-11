package com.mysite.sbb.trade.service;

public class TradeItemSoldOutException extends RuntimeException {

	public TradeItemSoldOutException(String message) {
		super(message);
	}
}
