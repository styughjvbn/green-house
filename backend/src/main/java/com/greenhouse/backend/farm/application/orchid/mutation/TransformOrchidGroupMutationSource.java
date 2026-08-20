package com.greenhouse.backend.farm.application.orchid.mutation;

import static com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationCommandNormalizer.normalizeNumber;

import java.math.BigDecimal;

public record TransformOrchidGroupMutationSource(
		Long orchidGroupId,
		Integer transformedQuantity,
		BigDecimal releasedStartPosition,
		BigDecimal releasedEndPosition) {

	public TransformOrchidGroupMutationSource {
		if (orchidGroupId == null) {
			throw new IllegalArgumentException("구조 변경 원본 난 묶음이 필요합니다.");
		}
		if (transformedQuantity == null || transformedQuantity < 1) {
			throw new IllegalArgumentException("구조 변경 수량은 1 이상이어야 합니다.");
		}
		releasedStartPosition = normalizeNumber(releasedStartPosition);
		releasedEndPosition = normalizeNumber(releasedEndPosition);
		if ((releasedStartPosition == null) != (releasedEndPosition == null)) {
			throw new IllegalArgumentException("원본에서 비울 시작·끝 위치를 모두 입력해야 합니다.");
		}
		if (releasedStartPosition != null
				&& releasedEndPosition.compareTo(releasedStartPosition) <= 0) {
			throw new IllegalArgumentException("원본에서 비울 종료 위치는 시작 위치보다 커야 합니다.");
		}
	}
}
