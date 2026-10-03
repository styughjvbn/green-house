package com.greenhouse.backend.farm.application.orchid.mutation;

import static com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationCommandNormalizer.requireText;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSource;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ReconcileOrchidGroupMutationCommand(
    OrchidGroupMutationSource source,
    Long orchidGroupId,
    Integer actualQuantity,
    String actualStatus,
    Long actualBedZoneId,
    BigDecimal actualStartPosition,
    BigDecimal actualEndPosition,
    LocalDate effectiveBusinessDate,
    String reason)
    implements OrchidGroupMutationCommand {

  public ReconcileOrchidGroupMutationCommand {
    if (source == null
        || orchidGroupId == null
        || actualBedZoneId == null
        || effectiveBusinessDate == null) {
      throw new IllegalArgumentException("현장 동기화 대상, 위치, 업무일이 필요합니다.");
    }
    if (actualQuantity == null || actualQuantity < 0) {
      throw new IllegalArgumentException("현장 확인 수량은 0 이상이어야 합니다.");
    }
    actualStatus = requireText(actualStatus, "현장 확인 상태");
    reason = requireText(reason, "현장 동기화 사유");
  }
}
