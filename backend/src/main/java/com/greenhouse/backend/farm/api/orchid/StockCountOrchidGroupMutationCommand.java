package com.greenhouse.backend.farm.api.orchid;

import java.time.LocalDate;

public record StockCountOrchidGroupMutationCommand(
    OrchidGroupMutationSource source,
    Long orchidGroupId,
    Long expectedRevision,
    Integer actualQuantity,
    LocalDate effectiveBusinessDate,
    String reason)
    implements OrchidGroupMutationCommand {
  public StockCountOrchidGroupMutationCommand {
    if (source == null
        || orchidGroupId == null
        || expectedRevision == null
        || expectedRevision < 0
        || actualQuantity == null
        || actualQuantity < 0
        || effectiveBusinessDate == null
        || reason == null
        || reason.isBlank()) throw new IllegalArgumentException("실사 수량 조정 요청이 올바르지 않습니다.");
    reason = reason.trim();
  }
}
