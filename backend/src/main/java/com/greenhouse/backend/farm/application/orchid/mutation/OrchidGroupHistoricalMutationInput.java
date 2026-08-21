package com.greenhouse.backend.farm.application.orchid.mutation;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSource;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record OrchidGroupHistoricalMutationInput(
		OrchidGroupMutationType mutationType,
		OrchidGroupMutationSource source,
		Instant occurredAt,
		LocalDate effectiveBusinessDate,
		String reason,
		List<OrchidGroupHistoricalEntryInput> entries,
		Map<String, Object> sourcePayload) {

	public OrchidGroupHistoricalMutationInput {
		if (mutationType == null || source == null || occurredAt == null || effectiveBusinessDate == null) {
			throw new IllegalArgumentException("Historical Mutation 입력의 필수 값이 누락되었습니다.");
		}
		if (mutationType == OrchidGroupMutationType.BASELINE_IMPORT) {
			throw new IllegalArgumentException("Historical importer는 baseline Mutation을 만들 수 없습니다.");
		}
		reason = normalize(reason);
		entries = entries == null ? List.of() : List.copyOf(entries);
		if (entries.isEmpty()) {
			throw new IllegalArgumentException("Historical Mutation에는 Entry가 하나 이상 필요합니다.");
		}
		var groupIds = new HashSet<Long>();
		if (entries.stream().anyMatch(item -> !groupIds.add(item.orchidGroupId()))) {
			throw new IllegalArgumentException("한 Historical Mutation에 같은 난 묶음을 중복할 수 없습니다.");
		}
		if (sourcePayload == null || sourcePayload.isEmpty()) {
			throw new IllegalArgumentException("Historical Mutation source payload가 필요합니다.");
		}
		sourcePayload = Collections.unmodifiableMap(new LinkedHashMap<>(sourcePayload));
	}

	private static String normalize(String value) {
		if (value == null) {
			return null;
		}
		String normalized = value.trim();
		return normalized.isEmpty() ? null : normalized;
	}
}
