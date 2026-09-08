package com.greenhouse.backend.farm.application.orchid.mutation;

public record CreateOrchidGroupMutationItem(Long bedZoneId, OrchidGroupMutationDetails details) {

	public CreateOrchidGroupMutationItem {
		if (bedZoneId == null || details == null) {
			throw new IllegalArgumentException("다중 생성할 논리 구역과 상세 상태가 필요합니다.");
		}
	}
}
