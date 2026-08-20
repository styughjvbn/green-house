package com.greenhouse.backend.farm.application.orchid.mutation;

import static com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationCommandNormalizer.requireText;

public record CorrectOrchidGroupMutationItem(
		Long orchidGroupId,
		Integer correctedQuantity,
		String correctedStatus) {

	public CorrectOrchidGroupMutationItem {
		if (orchidGroupId == null) {
			throw new IllegalArgumentException("보정 대상 난 묶음이 필요합니다.");
		}
		if (correctedQuantity == null || correctedQuantity < 0) {
			throw new IllegalArgumentException("보정 수량은 0 이상이어야 합니다.");
		}
		correctedStatus = requireText(correctedStatus, "보정 상태");
	}
}
