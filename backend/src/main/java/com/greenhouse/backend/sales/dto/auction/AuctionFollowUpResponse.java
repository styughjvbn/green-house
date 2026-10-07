package com.greenhouse.backend.sales.dto.auction;

import com.greenhouse.backend.sales.domain.auction.AuctionFollowUpMethod;
import java.util.List;

public record AuctionFollowUpResponse(
    Long lotId,
    Long decisionId,
    AuctionFollowUpMethod method,
    Integer decidedQuantity,
    Integer pendingQuantity,
    Integer disposedQuantity,
    Integer inferredReturnQuantity,
    boolean decisionChangeAllowed,
    boolean arrivalAllowed,
    List<AuctionFollowUpMethod> availableMethods) {}
