package com.greenhouse.backend.auction.repository;

import java.time.LocalDate;

/** Scalar source values for the auction module's result read contract. */
public record AuctionResultReadRow(
    Long id,
    Long lotId,
    Long auctionHouseId,
    LocalDate auctionDate,
    LocalDate shipmentDate,
    String varietyName,
    String shipmentGrade,
    Integer quantity,
    Integer unitPrice,
    Integer amount) {}
