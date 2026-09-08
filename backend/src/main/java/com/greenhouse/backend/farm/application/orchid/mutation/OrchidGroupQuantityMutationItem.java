package com.greenhouse.backend.farm.application.orchid.mutation;

public record OrchidGroupQuantityMutationItem(Long orchidGroupId, Integer quantity) {

	public OrchidGroupQuantityMutationItem {
		if (orchidGroupId == null) {
			throw new IllegalArgumentException("수량 Mutation 대상 난 묶음이 필요합니다.");
		}
		if (quantity == null || quantity < 1) {
			throw new IllegalArgumentException("수량 Mutation 수량은 1 이상이어야 합니다.");
		}
	}
}
