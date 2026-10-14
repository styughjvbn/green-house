package com.greenhouse.backend.sales.direct.domain;

import com.greenhouse.backend.common.exception.ConflictException;
import java.time.LocalDate;

/** Commercial return capacity is consumed only by valid physical arrival facts. */
public final class DirectReturnArrivalPolicy {
  private DirectReturnArrivalPolicy() {}

  public static int pendingQuantity(DirectSaleChange returned, int arrived) {
    if (returned.getType() != DirectSaleChangeType.RETURN
        || arrived < 0
        || arrived > returned.getQuantity())
      throw new ConflictException("DIRECT_ARRIVAL_LEDGER_INVALID", "반품 도착 수량을 확인해야 합니다.");
    return returned.getQuantity() - arrived;
  }

  public static void requireArrival(
      DirectSaleChange returned, boolean canceled, int arrived, int quantity, LocalDate date) {
    if (canceled) throw new ConflictException("DIRECT_RETURN_CANCELED", "취소한 반품에는 도착을 등록할 수 없습니다.");
    if (date == null || date.isBefore(returned.getChangeDate()))
      throw new IllegalArgumentException("도착일은 반품일 이후여야 합니다.");
    if (quantity < 1 || quantity > pendingQuantity(returned, arrived))
      throw new ConflictException("DIRECT_ARRIVAL_QUANTITY_EXCEEDED", "도착 대기 수량을 초과할 수 없습니다.");
  }
}
