package com.greenhouse.backend.sales.dto.auction;

import java.math.BigDecimal;
import java.util.List;

/** Provider amounts remain nullable; result references are original identifiers. */
public record AuctionProceedsResponse(
    Long id,
    Long auctionHouseId,
    String auctionHouseName,
    String sourceReference,
    Long reportedGrossAmount,
    Long receivableAmount,
    boolean matchingConfirmed,
    BigDecimal paidAmount,
    BigDecimal remainingAmount,
    boolean reviewRequired,
    boolean paymentAllowed,
    List<Long> resultIds) {}
