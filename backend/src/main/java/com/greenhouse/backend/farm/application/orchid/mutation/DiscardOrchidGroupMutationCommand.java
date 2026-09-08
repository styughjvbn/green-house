package com.greenhouse.backend.farm.application.orchid.mutation;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSource;
import java.time.LocalDate;

public record DiscardOrchidGroupMutationCommand(OrchidGroupMutationSource source, Long orchidGroupId, Integer quantity,
		LocalDate effectiveBusinessDate, String reason) implements OrchidGroupMutationCommand {

	public DiscardOrchidGroupMutationCommand {
		if (source == null || orchidGroupId == null || effectiveBusinessDate == null) {
			throw new IllegalArgumentException("폐기 Mutation의 source, 난 묶음과 업무일이 필요합니다.");
		}
		if (quantity == null || quantity < 1) {
			throw new IllegalArgumentException("폐기 수량은 1 이상이어야 합니다.");
		}
		reason = normalize(reason);
	}

	private static String normalize(String value) {
		if (value == null) {
			return null;
		}
		String normalized = value.trim();
		return normalized.isEmpty() ? null : normalized;
	}
}
