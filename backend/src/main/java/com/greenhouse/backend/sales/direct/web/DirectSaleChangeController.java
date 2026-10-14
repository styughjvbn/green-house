package com.greenhouse.backend.sales.direct.web;

import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.sales.direct.application.DirectSaleChangeService;
import com.greenhouse.backend.sales.direct.domain.DirectSaleChangeType;
import com.greenhouse.backend.sales.direct.web.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "sales-controller")
@RequestMapping("/api/sales-slips/{id}/direct-changes")
public class DirectSaleChangeController {
  private final DirectSaleChangeService service;

  @GetMapping
  public ApiResponse<DirectSaleChangeResponse> getDirectSaleChanges(
      @PathVariable Long id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size) {
    return ApiResponse.ok(service.get(id, page, size));
  }

  @Operation(
      description =
          "원가격 구간의 일부 수량을 새 단가로 조정합니다. 원품목·재고·수납은 보존하며 현재 거래금액을 갱신합니다. 같은 키·입력은 최초 변경 ID를 반환합니다.")
  @PostMapping("/price-adjustments")
  public ApiResponse<DirectSaleChangeResult> adjustDirectSalePrices(
      @PathVariable Long id,
      @Valid @RequestBody DirectSaleChangeRequest request,
      @RequestHeader("Idempotency-Key") String key) {
    return ApiResponse.ok(service.change(id, DirectSaleChangeType.PRICE_ADJUSTMENT, request, key));
  }

  @Operation(description = "선택한 가격 구간의 수량·금액을 반품 이력으로 기록합니다. 실제 농장 도착·재고 반영·현금 환불을 실행하지 않습니다.")
  @PostMapping("/returns")
  public ApiResponse<DirectSaleChangeResult> recordDirectSaleReturn(
      @PathVariable Long id,
      @Valid @RequestBody DirectSaleChangeRequest request,
      @RequestHeader("Idempotency-Key") String key) {
    return ApiResponse.ok(service.change(id, DirectSaleChangeType.RETURN, request, key));
  }

  @Operation(description = "후속 사용이 없는 가격 조정 또는 반품을 취소 이력으로 되돌립니다. 원기록은 삭제하지 않습니다.")
  @PostMapping("/{changeId}/cancel")
  public ApiResponse<DirectSaleChangeResult> cancelDirectSaleChange(
      @PathVariable Long id,
      @PathVariable Long changeId,
      @Valid @RequestBody CancelDirectSaleChangeRequest request,
      @RequestHeader("Idempotency-Key") String key) {
    return ApiResponse.ok(service.cancel(id, changeId, request, key));
  }
}
