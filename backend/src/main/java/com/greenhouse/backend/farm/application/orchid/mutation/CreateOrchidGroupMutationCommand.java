package com.greenhouse.backend.farm.application.orchid.mutation;

import static com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationCommandNormalizer.normalizeText;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSource;
import java.time.LocalDate;

public record CreateOrchidGroupMutationCommand(
		OrchidGroupMutationSource source,
		Long bedZoneId,
		OrchidGroupMutationDetails details,
		LocalDate effectiveBusinessDate,
		String reason) implements OrchidGroupMutationCommand {

	public CreateOrchidGroupMutationCommand {
		if (source == null || bedZoneId == null || details == null || effectiveBusinessDate == null) {
			throw new IllegalArgumentException("생성 Mutation의 source, 논리 구역, 상세 상태와 업무일이 필요합니다.");
		}
		reason = normalizeText(reason);
	}
}
