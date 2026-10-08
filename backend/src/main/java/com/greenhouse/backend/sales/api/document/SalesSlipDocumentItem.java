package com.greenhouse.backend.sales.api.document;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "SalesSlipItemResponse")
public record SalesSlipDocumentItem(
    Long id,
    Long auctionShipmentLotId,
    String itemName,
    String genus,
    String spec,
    Integer quantity,
    Integer unitPrice,
    Integer amount,
    String memo,
    List<SalesSlipDocumentAllocation> allocations) {}
