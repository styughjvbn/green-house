package com.greenhouse.backend.farm.application.orchid.mutation;

import static com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationCommandNormalizer.normalizeNumber;
import static com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationCommandNormalizer.normalizeText;
import static com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationCommandNormalizer.requireText;

import com.greenhouse.backend.farm.domain.orchid.PotSizeCode;
import java.math.BigDecimal;

public record OrchidGroupMutationDetails(
		Long varietyId,
		Integer quantity,
		String potSize,
		Integer ageYear,
		String status,
		String placementType,
		Integer trayCount,
		Boolean splitPlacementAllowed,
		BigDecimal startPosition,
		BigDecimal endPosition,
		String memo) {

	public OrchidGroupMutationDetails {
		if (varietyId == null) {
			throw new IllegalArgumentException("난 묶음 품종이 필요합니다.");
		}
		if (quantity == null || quantity < 1) {
			throw new IllegalArgumentException("난 묶음 수량은 1 이상이어야 합니다.");
		}
		if (ageYear != null && ageYear < 0) {
			throw new IllegalArgumentException("난 묶음 연차는 0 이상이어야 합니다.");
		}
		if (trayCount != null && trayCount < 0) {
			throw new IllegalArgumentException("트레이 수는 0 이상이어야 합니다.");
		}
		potSize = PotSizeCode.fromInput(potSize).getDisplayValue();
		status = requireText(status, "난 묶음 상태");
		placementType = normalizeText(placementType);
		splitPlacementAllowed = Boolean.TRUE.equals(splitPlacementAllowed);
		startPosition = normalizeNumber(startPosition);
		endPosition = normalizeNumber(endPosition);
		memo = normalizeText(memo);
	}
}
