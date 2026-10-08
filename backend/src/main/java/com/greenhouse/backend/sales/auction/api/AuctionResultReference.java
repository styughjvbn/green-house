package com.greenhouse.backend.sales.auction.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@io.swagger.v3.oas.annotations.media.Schema(name = "AuctionProceedsResultReference")
public record AuctionResultReference(
    @io.swagger.v3.oas.annotations.media.Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Long id,
    @io.swagger.v3.oas.annotations.media.Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Long lotId,
    @io.swagger.v3.oas.annotations.media.Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Long auctionHouseId,
    @io.swagger.v3.oas.annotations.media.Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate auctionDate,
    @io.swagger.v3.oas.annotations.media.Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate shipmentDate,
    @io.swagger.v3.oas.annotations.media.Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String varietyName,
    @io.swagger.v3.oas.annotations.media.Schema(
            nullable = true,
            requiredMode = Schema.RequiredMode.REQUIRED)
        String shipmentGrade,
    @io.swagger.v3.oas.annotations.media.Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Integer quantity,
    @io.swagger.v3.oas.annotations.media.Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Integer unitPrice,
    @io.swagger.v3.oas.annotations.media.Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Long amount) {}
