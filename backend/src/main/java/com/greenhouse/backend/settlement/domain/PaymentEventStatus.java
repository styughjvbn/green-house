package com.greenhouse.backend.settlement.domain;

public enum PaymentEventStatus {
	UNAPPLIED,
	PARTIALLY_APPLIED,
	FULLY_APPLIED,
	CANDIDATE,
	CONFIRMED,
	REJECTED,
	CANCELLED
}
