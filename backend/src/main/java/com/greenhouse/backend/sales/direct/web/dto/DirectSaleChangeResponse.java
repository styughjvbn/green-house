package com.greenhouse.backend.sales.direct.web.dto;

import com.greenhouse.backend.common.api.PageResponse;
import com.greenhouse.backend.sales.direct.domain.DirectSaleChangeType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record DirectSaleChangeResponse(
    Long documentId,
    long originalAmount,
    int currentAmount,
    @io.swagger.v3.oas.annotations.media.Schema(
            nullable = true,
            description = "현재 수납 배분이 거래금액을 초과한 환불 필요액. 검토 대상은 미상이며 실제 환불 기록이 아닙니다.")
        Long refundDueAmount,
    List<DirectSaleChangeType> availableTypes,
    List<Portion> portions,
    @io.swagger.v3.oas.annotations.media.Schema(
            description = "취소되지 않은 반품의 가격 구간별 수량·단가·비음수 금액. 이력 페이지와 무관한 전체 구성이며 현재 거래금액에서 제외됩니다.")
        List<Portion> returnedPortions,
    PageResponse<History> history) {
  public record Portion(
      Long itemId,
      String itemName,
      String spec,
      @io.swagger.v3.oas.annotations.media.Schema(
              nullable = true,
              description = "원가격 구간이면 null, 조정가격 구간이면 해당 가격 조정 ID")
          Long sourceChangeId,
      int quantity,
      int unitPrice,
      long amount) {}

  public record History(
      Long id,
      Long itemId,
      String itemName,
      DirectSaleChangeType type,
      Long sourceChangeId,
      Long canceledChangeId,
      int quantity,
      int previousUnitPrice,
      int nextUnitPrice,
      long amountDelta,
      LocalDate changeDate,
      String reason,
      LocalDateTime createdAt,
      boolean canceled,
      boolean cancelAllowed) {}
}
