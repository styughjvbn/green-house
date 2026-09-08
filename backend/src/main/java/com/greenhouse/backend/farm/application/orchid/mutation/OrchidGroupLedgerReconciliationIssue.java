package com.greenhouse.backend.farm.application.orchid.mutation;

public record OrchidGroupLedgerReconciliationIssue(String code, String domain, String referenceId, String message) {

	public OrchidGroupLedgerReconciliationIssue {
		code = requireText(code, "대사 오류 코드");
		domain = requireText(domain, "대사 오류 도메인");
		referenceId = requireText(referenceId, "대사 오류 참조 ID");
		message = requireText(message, "대사 오류 설명");
	}

	public static OrchidGroupLedgerReconciliationIssue group(String code, Long orchidGroupId, String message) {
		return new OrchidGroupLedgerReconciliationIssue(code, "FARM", String.valueOf(orchidGroupId), message);
	}

	private static String requireText(String value, String label) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(label + "가 필요합니다.");
		}
		return value.trim();
	}
}
