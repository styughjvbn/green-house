package com.greenhouse.backend.sales.direct.web;

import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.sales.direct.application.DirectReturnArrivalService;
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
public class DirectReturnArrivalController {
  private final DirectReturnArrivalService service;

  @GetMapping("/return-arrivals")
  public ApiResponse<DirectReturnArrivalState> getDirectReturnArrivals(
      @PathVariable Long id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size) {
    return ApiResponse.ok(service.get(id, page, size));
  }

  @Operation(
      description = "반품 도착을 기록합니다. 새 묶음 생성 또는 이미 재고에 반영된 도착의 기록만 등록을 선택합니다. 원판매·금액·수납은 변경하지 않습니다.")
  @PostMapping("/{returnId}/arrivals")
  public ApiResponse<DirectReturnArrivalResult> recordDirectReturnArrival(
      @PathVariable Long id,
      @PathVariable Long returnId,
      @Valid @RequestBody DirectReturnArrivalRequest request,
      @RequestHeader("Idempotency-Key") String key) {
    return ApiResponse.ok(service.arrive(id, returnId, request, key));
  }

  @Operation(
      description = "도착 이력을 보존해 취소합니다. 새 묶음을 생성한 경우에만 Farm 생성 Mutation을 보상하며 후속 사용이 있으면 거절합니다.")
  @PostMapping("/return-arrivals/{arrivalId}/cancel")
  public ApiResponse<DirectReturnArrivalResult> cancelDirectReturnArrival(
      @PathVariable Long id,
      @PathVariable Long arrivalId,
      @Valid @RequestBody CancelDirectReturnArrivalRequest request,
      @RequestHeader("Idempotency-Key") String key) {
    return ApiResponse.ok(service.cancel(id, arrivalId, request, key));
  }
}
