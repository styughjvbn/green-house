package com.greenhouse.backend.farm.api.orchid;

import static com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationCommandNormalizer.normalizeNumber;

import java.math.BigDecimal;

public record MoveOrchidGroupMutationItem(
    Long orchidGroupId, Long toBedZoneId, BigDecimal startPosition, BigDecimal endPosition) {

  public MoveOrchidGroupMutationItem {
    if (orchidGroupId == null || toBedZoneId == null) {
      throw new IllegalArgumentException("이동할 난 묶음과 목적 구역이 필요합니다.");
    }
    startPosition = normalizeNumber(startPosition);
    endPosition = normalizeNumber(endPosition);
  }
}
