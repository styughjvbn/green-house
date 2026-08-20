package com.greenhouse.backend.farm.application.orchid.mutation;

import static com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationCommandNormalizer.normalizeNumber;
import static com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationCommandNormalizer.normalizeText;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSource;
import java.math.BigDecimal;
import java.time.LocalDate;

public record MoveOrchidGroupMutationCommand(
		OrchidGroupMutationSource source,
		Long orchidGroupId,
		Long toBedZoneId,
		BigDecimal startPosition,
		BigDecimal endPosition,
		LocalDate effectiveBusinessDate,
		String reason) {

	public MoveOrchidGroupMutationCommand {
		if (source == null || orchidGroupId == null || toBedZoneId == null || effectiveBusinessDate == null) {
			throw new IllegalArgumentException("이동 Mutation의 source, 난 묶음, 목적 구역과 업무일이 필요합니다.");
		}
		startPosition = normalizeNumber(startPosition);
		endPosition = normalizeNumber(endPosition);
		reason = normalizeText(reason);
	}
}
