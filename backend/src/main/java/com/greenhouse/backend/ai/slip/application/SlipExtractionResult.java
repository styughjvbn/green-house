package com.greenhouse.backend.ai.slip.application;

import com.greenhouse.backend.sales.api.document.SalesType;
import java.util.List;

public record SlipExtractionResult(
    String requestId,
    SalesType salesType,
    String saleDate,
    String partnerName,
    Long partnerId,
    String rawPartnerName,
    String memo,
    List<Item> items,
    List<String> warnings) {
  @io.swagger.v3.oas.annotations.media.Schema(name = "AiSlipExtractionItem")
  public record Item(
      String itemName,
      String genus,
      Long varietyId,
      String rawItemName,
      String rawGenus,
      String spec,
      String unit,
      Integer boxes,
      Integer quantity,
      Integer unitPrice,
      Long amount,
      Long calculatedAmount,
      String memo) {}
}
