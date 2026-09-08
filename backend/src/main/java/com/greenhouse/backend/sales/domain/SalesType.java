package com.greenhouse.backend.sales.domain;

public enum SalesType {
	DIRECT("미입금", null),
	AUCTION("정산 대기", "경매 정산");

	private final String defaultPaymentStatus;
	private final String defaultPaymentMethod;

	SalesType(String defaultPaymentStatus, String defaultPaymentMethod) {
		this.defaultPaymentStatus = defaultPaymentStatus;
		this.defaultPaymentMethod = defaultPaymentMethod;
	}

	public String defaultPaymentStatus() { return defaultPaymentStatus; }
	public String defaultPaymentMethod() { return defaultPaymentMethod; }
}
