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
    List<String> warnings,
    @io.swagger.v3.oas.annotations.media.Schema(description = "AI 자체 평가 신뢰도(0~100). 실제 정답률이 아닙니다.")
        Integer confidence,
    Integer imageLongEdge,
    Integer attempts,
    List<SlipFieldReview> reviewFields) {
  @io.swagger.v3.oas.annotations.media.Schema(name = "AiSlipExtractionItem")
  public record Item(
      String itemName,
      String genus,
      Long varietyId,
      String rawItemName,
      String rawGenus,
      String spec,
      Integer unit,
      Integer boxes,
      Integer quantity,
      Integer unitPrice,
      Long amount,
      Long calculatedAmount,
      String memo) {}
}
