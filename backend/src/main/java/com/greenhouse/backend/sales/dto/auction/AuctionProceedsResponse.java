package com.greenhouse.backend.sales.dto.auction;

import com.greenhouse.backend.sales.application.auction.AuctionDataReader;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/** Provider amounts remain nullable; result references are original identifiers. */
public record AuctionProceedsResponse(
    Long id,
    Long auctionHouseId,
    String auctionHouseName,
    @Schema(nullable = true) String sourceReference,
    @Schema(nullable = true) Long reportedGrossAmount,
    @Schema(nullable = true) Long receivableAmount,
    boolean matchingConfirmed,
    BigDecimal paidAmount,
    @Schema(nullable = true) BigDecimal remainingAmount,
    boolean reviewRequired,
    boolean paymentAllowed,
    List<Long> resultIds,
    List<AuctionDataReader.Result> resultDetails) {}
