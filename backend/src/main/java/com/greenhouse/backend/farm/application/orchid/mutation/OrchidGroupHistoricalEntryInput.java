package com.greenhouse.backend.farm.application.orchid.mutation;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntryRole;

public record OrchidGroupHistoricalEntryInput(
		Long orchidGroupId,
		OrchidGroupMutationEntryRole role,
		Integer creationQuantity,
		Integer quantityDelta) {

	public OrchidGroupHistoricalEntryInput {
		if (orchidGroupId == null || role == null) {
			throw new IllegalArgumentException("Historical Entry 입력의 필수 값이 누락되었습니다.");
		}
		if (creationQuantity != null && creationQuantity < 0) {
			throw new IllegalArgumentException("Historical Entry 생성 수량은 0 이상이어야 합니다.");
		}
	}
}
