package com.greenhouse.backend.work.application.operation;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.farm.dto.transformation.MultiCreateOrchidGroupRowRequest;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkRequestFingerprintTest {

	private final WorkRequestFingerprint fingerprint = new WorkRequestFingerprint();

	@Test
	void objectKeyOrderAndNumericRepresentationDoNotChangeTheRequest() {
		var first = new LinkedHashMap<String, Object>();
		first.put("amount", 12L);
		first.put("nested", Map.of("position", new BigDecimal("6.00")));
		var second = new LinkedHashMap<String, Object>();
		second.put("nested", Map.of("position", 6));
		second.put("amount", 12);
		assertThat(fingerprint.calculate(first)).isEqualTo(fingerprint.calculate(second));
	}

	@Test
	void meaningfulArrayOrderAndFreeFormTextArePreserved() {
		assertThat(fingerprint.calculate(List.of(1, 2))).isNotEqualTo(fingerprint.calculate(List.of(2, 1)));
		assertThat(fingerprint.calculate(Map.of("memo", " a ")))
			.isNotEqualTo(fingerprint.calculate(Map.of("memo", "a")));
		assertThat(fingerprint.calculate(Map.of("details", Map.of("idempotencyKey", "a"))))
			.isNotEqualTo(fingerprint.calculate(Map.of("details", Map.of("idempotencyKey", "b"))));
	}

	@Test
	void collectionMembershipOrderIsNotPartOfTheRequest() {
		var first = new MultiCreateOrchidGroupRowRequest(null, new LinkedHashSet<>(List.of(9L, 8L)));
		var second = new MultiCreateOrchidGroupRowRequest(null, new LinkedHashSet<>(List.of(8L, 9L)));
		assertThat(fingerprint.calculate(first)).isEqualTo(fingerprint.calculate(second));
		assertThat(fingerprint.calculate(new MultiCreateOrchidGroupRowRequest(null, null)))
			.isEqualTo(fingerprint.calculate(new MultiCreateOrchidGroupRowRequest(null, java.util.Set.of())));
	}

}
