package com.greenhouse.backend.sales.domain;

/**
 * Compatibility groups for stored free-form labels, not payment eligibility or ledger
 * state.
 */
public enum SalesPaymentCategory {

	PAID, PARTIAL, UNPAID;

	public static SalesPaymentCategory fromStoredStatus(String status) {
		// Keep the existing report classification until stored labels have an explicit
		// migration policy.
		if (status.contains("부분")) {
			return PARTIAL;
		}
		if (status.contains("완료") || status.equals("PAID")) {
			return PAID;
		}
		return UNPAID;
	}

}
