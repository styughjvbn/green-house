package com.greenhouse.backend.farm.application.orchid.mutation;

import static com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationCommandNormalizer.normalizeText;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSource;
import java.time.LocalDate;

public record UpdateOrchidGroupMutationCommand(OrchidGroupMutationSource source, Long orchidGroupId,
		OrchidGroupMutationDetails details, LocalDate effectiveBusinessDate,
		String reason) implements OrchidGroupMutationCommand {

	public UpdateOrchidGroupMutationCommand {
		if (source == null || orchidGroupId == null || details == null || effectiveBusinessDate == null) {
			throw new IllegalArgumentException("수정 Mutation의 source, 난 묶음, 상세 상태와 업무일이 필요합니다.");
		}
		reason = normalizeText(reason);
	}
}
