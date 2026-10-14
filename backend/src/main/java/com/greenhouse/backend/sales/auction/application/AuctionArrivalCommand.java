package com.greenhouse.backend.sales.auction.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.greenhouse.backend.farm.api.orchid.mutation.OrchidGroupMutationDetails;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record AuctionArrivalCommand(
    @NotNull LocalDate arrivalDate,
    @Positive Long bedZoneId,
    @Valid OrchidGroupMutationDetails details,
    @NotBlank @Size(max = 100) String idempotencyKey,
    @Size(max = 100) String worker,
    @JsonInclude(JsonInclude.Include.NON_NULL) @Positive Integer quantity,
    @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "이미 재고에 반영된 도착이면 true. Farm 재고를 변경하지 않습니다.")
        Boolean recordOnly) {
  public AuctionArrivalCommand(
      LocalDate arrivalDate,
      Long bedZoneId,
      OrchidGroupMutationDetails details,
      String idempotencyKey,
      String worker) {
    this(arrivalDate, bedZoneId, details, idempotencyKey, worker, null, null);
  }

  public int arrivalQuantity() {
    if (Boolean.TRUE.equals(recordOnly)) {
      if (quantity == null || quantity < 1 || bedZoneId != null || details != null)
        throw new IllegalArgumentException("기록만 등록할 때는 도착 수량만 입력해야 합니다.");
      return quantity;
    }
    if (bedZoneId == null
        || details == null
        || (quantity != null && !quantity.equals(details.quantity())))
      throw new IllegalArgumentException("새 묶음 생성에는 배치와 일치하는 수량이 필요합니다.");
    return details.quantity();
  }
}
