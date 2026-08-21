package com.greenhouse.backend.farm.application.orchid.mutation;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSource;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;

public record OrchidGroupHistoricalMutationInput(
		OrchidGroupMutationType mutationType,
		OrchidGroupMutationSource source,
		Instant occurredAt,
		LocalDate effectiveBusinessDate,
		String reason,
		List<OrchidGroupHistoricalEvidenceInput> evidence) {

	public OrchidGroupHistoricalMutationInput {
		if (mutationType == null || source == null || occurredAt == null || effectiveBusinessDate == null) {
			throw new IllegalArgumentException("Historical Mutation 입력의 필수 값이 누락되었습니다.");
		}
		if (mutationType == OrchidGroupMutationType.BASELINE_IMPORT) {
			throw new IllegalArgumentException("Historical importer는 baseline Mutation을 만들 수 없습니다.");
		}
		reason = normalize(reason);
		evidence = evidence == null ? List.of() : List.copyOf(evidence);
		if (evidence.isEmpty()) {
			throw new IllegalArgumentException("Historical Mutation에는 evidence가 하나 이상 필요합니다.");
		}
		var groupIds = new HashSet<Long>();
		if (evidence.stream().anyMatch(item -> !groupIds.add(item.orchidGroupId()))) {
			throw new IllegalArgumentException("한 Historical Mutation에 같은 난 묶음을 중복할 수 없습니다.");
		}
	}

	private static String normalize(String value) {
		if (value == null) {
			return null;
		}
		String normalized = value.trim();
		return normalized.isEmpty() ? null : normalized;
	}
}
