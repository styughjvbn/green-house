package com.greenhouse.backend.sales.dto.auction;

import com.greenhouse.backend.sales.domain.auction.AuctionReturnArrival;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AuctionArrivalResponse(
    Long id,
    Long lotId,
    Long decisionId,
    Integer quantity,
    LocalDate arrivalDate,
    String worker,
    Long orchidGroupId,
    Long creationMutationId,
    LocalDateTime createdAt,
    @Schema(nullable = true) LocalDateTime canceledAt,
    @Schema(nullable = true) Long cancellationMutationId,
    @Schema(nullable = true) String cancellationReason,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean cancellationAllowed) {
  public static AuctionArrivalResponse from(AuctionReturnArrival arrival) {
    return new AuctionArrivalResponse(
        arrival.getId(),
        arrival.getLotId(),
        arrival.getDecisionId(),
        arrival.getQuantity(),
        arrival.getArrivalDate(),
        arrival.getWorker(),
        arrival.getOrchidGroupId(),
        arrival.getCreationMutationId(),
        arrival.getCreatedAt(),
        arrival.getCanceledAt(),
        arrival.getCancellationMutationId(),
        arrival.getCancellationReason(),
        arrival.isCancellationAllowed());
  }
}
