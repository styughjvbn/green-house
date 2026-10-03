package com.greenhouse.backend.work.application.correction;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.work.application.operation.WorkRequestFingerprint;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkCorrectionRequestFingerprintTest {

	@Test
	void absentQuantityDeclarationKeepsLegacyReceiptFingerprint() {
		var date = LocalDate.of(2026, 7, 15);
		var adjustments = List.of(new OrchidGroupCorrectionInput(4L, 25, "정상"));
		var legacy = new LegacyRequest("key", date, null, null, "사유", adjustments, false);
		var absent = new WorkCorrectionCommand("key", date, null, null, "사유", adjustments, false, null);
		var empty = new WorkCorrectionCommand("key", date, null, null, "사유", adjustments, false, List.of());
		var fingerprint = new WorkRequestFingerprint();
		assertThat(fingerprint.calculate(absent)).isEqualTo(fingerprint.calculate(legacy));
		assertThat(fingerprint.calculate(empty)).isEqualTo(fingerprint.calculate(legacy));
	}

	private record LegacyRequest(String idempotencyKey, LocalDate workDate, String worker, String memo, String reason,
			List<OrchidGroupCorrectionInput> orchidGroupAdjustments, Boolean cancelResultCreation) {
	}

}
