package com.greenhouse.backend.sales.direct.web.dto;

import com.greenhouse.backend.common.api.PageResponse;
import java.util.List;

public record DirectReturnArrivalState(
    List<PendingReturn> pendingReturns, PageResponse<DirectReturnArrivalResponse> history) {
  public record PendingReturn(
      Long returnChangeId,
      Long itemId,
      String itemName,
      Integer pendingQuantity,
      boolean arrivalAllowed) {}
}
