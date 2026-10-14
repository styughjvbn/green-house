package com.greenhouse.backend.sales.payment.domain;

public enum PaymentEventStatus {
  UNAPPLIED,
  PARTIALLY_APPLIED,
  FULLY_APPLIED,
  CANDIDATE,
  CONFIRMED,
  REJECTED,
  CANCELLED
}
