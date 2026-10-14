package com.greenhouse.backend.sales.auction.web.dto;

import com.greenhouse.backend.sales.auction.domain.AuctionFollowUpMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record AuctionFollowUpResponse(
    Long lotId,
    @Schema(nullable = true) Long decisionId,
    @Schema(nullable = true) AuctionFollowUpMethod method,
    @Schema(nullable = true) Integer decidedQuantity,
    Integer pendingQuantity,
    Integer disposedQuantity,
    Integer inferredReturnQuantity,
    boolean decisionChangeAllowed,
    boolean arrivalAllowed,
    List<AuctionFollowUpMethod> availableMethods) {}
