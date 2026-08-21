package com.greenhouse.backend.farm.application.orchid.mutation;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupHistoricalEvidenceKind;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupHistoricalEvidenceQuality;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntryRole;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record OrchidGroupHistoricalEvidenceInput(
		Long orchidGroupId,
		OrchidGroupMutationEntryRole role,
		OrchidGroupHistoricalEvidenceKind evidenceKind,
		OrchidGroupHistoricalEvidenceQuality evidenceQuality,
		List<String> knownFields,
		Map<String, Object> beforeFragment,
		Map<String, Object> afterFragment,
		Map<String, Object> changeSet,
		Map<String, Object> sourcePayload) {

	public OrchidGroupHistoricalEvidenceInput {
		if (orchidGroupId == null || role == null || evidenceKind == null || evidenceQuality == null) {
			throw new IllegalArgumentException("Historical evidence 입력의 필수 값이 누락되었습니다.");
		}
		if ((evidenceKind == OrchidGroupHistoricalEvidenceKind.GAP)
				!= (evidenceQuality == OrchidGroupHistoricalEvidenceQuality.GAP)) {
			throw new IllegalArgumentException("GAP kind와 quality는 함께 사용해야 합니다.");
		}
		if (beforeFragment == null && afterFragment == null && changeSet == null) {
			throw new IllegalArgumentException("Historical evidence 입력에는 알려진 상태 조각이 필요합니다.");
		}
		knownFields = knownFields == null ? List.of() : knownFields.stream()
				.map(value -> value == null ? "" : value.trim())
				.filter(value -> !value.isEmpty())
				.distinct()
				.sorted()
				.toList();
		beforeFragment = immutableMap(beforeFragment);
		afterFragment = immutableMap(afterFragment);
		changeSet = immutableMap(changeSet);
		sourcePayload = immutableMap(sourcePayload);
		if (sourcePayload == null || sourcePayload.isEmpty()) {
			throw new IllegalArgumentException("Historical evidence source payload가 필요합니다.");
		}
	}

	private static Map<String, Object> immutableMap(Map<String, Object> value) {
		return value == null
				? null
				: Collections.unmodifiableMap(new LinkedHashMap<>(value));
	}
}
