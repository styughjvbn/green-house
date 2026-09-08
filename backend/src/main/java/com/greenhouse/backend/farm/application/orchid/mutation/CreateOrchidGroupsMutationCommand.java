package com.greenhouse.backend.farm.application.orchid.mutation;

import static com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationCommandNormalizer.normalizeText;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSource;
import java.time.LocalDate;
import java.util.List;

public record CreateOrchidGroupsMutationCommand(OrchidGroupMutationSource source,
		List<CreateOrchidGroupMutationItem> groups, LocalDate effectiveBusinessDate,
		String reason) implements OrchidGroupMutationCommand {

	public CreateOrchidGroupsMutationCommand {
		if (source == null || effectiveBusinessDate == null) {
			throw new IllegalArgumentException("다중 생성 Mutation의 source와 업무일이 필요합니다.");
		}
		if (groups == null || groups.isEmpty() || groups.stream().anyMatch(item -> item == null)) {
			throw new IllegalArgumentException("다중 생성할 난 묶음이 필요합니다.");
		}
		groups = List.copyOf(groups);
		reason = normalizeText(reason);
	}
}
