package com.greenhouse.backend.sales.domain.payment;

public enum PaymentEventStatus {
  UNAPPLIED,
  PARTIALLY_APPLIED,
  FULLY_APPLIED,
  CANDIDATE,
  CONFIRMED,
  REJECTED,
  CANCELLED
}
