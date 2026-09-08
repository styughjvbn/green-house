package com.greenhouse.backend.farm.application.orchid.mutation;

public record TransformOrchidGroupMutationResult(Long bedZoneId, OrchidGroupMutationDetails details) {

	public TransformOrchidGroupMutationResult {
		if (bedZoneId == null || details == null) {
			throw new IllegalArgumentException("구조 변경 결과의 논리 구역과 상세 상태가 필요합니다.");
		}
	}
}
