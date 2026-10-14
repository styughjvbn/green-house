package com.greenhouse.backend.sales.direct.web.dto;

import com.greenhouse.backend.farm.api.orchid.mutation.OrchidGroupMutationDetails;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record DirectReturnArrivalRequest(
    @NotNull LocalDate arrivalDate,
    @NotNull @Positive Integer quantity,
    @Schema(description = "이미 재고에 반영된 도착이면 true. Farm 재고를 변경하지 않습니다.") boolean recordOnly,
    @Positive Long bedZoneId,
    @Valid OrchidGroupMutationDetails details,
    @Size(max = 100) String worker) {
  public void validateInventoryInput() {
    if (recordOnly) {
      if (bedZoneId != null || details != null)
        throw new IllegalArgumentException("기록만 등록할 때는 농장 생성 정보를 입력하지 않습니다.");
    } else if (bedZoneId == null || details == null || !quantity.equals(details.quantity()))
      throw new IllegalArgumentException("새 묶음 생성에는 배치와 일치하는 수량이 필요합니다.");
  }
}
