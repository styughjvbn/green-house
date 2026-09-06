package com.greenhouse.backend.settlement.application;

/** Identifies the received event; the ledger entity remains inside Settlement. */
public record PaymentReceipt(Long eventId) {
}
