package com.greenhouse.backend.work.operation.web;

import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.work.api.operation.WorkOperationView;
import com.greenhouse.backend.work.operation.application.WorkOperationVoidService;
import com.greenhouse.backend.work.operation.web.dto.WorkOperationBatchCancellationRequest;
import com.greenhouse.backend.work.operation.web.dto.WorkOperationBatchCancellationResponse;
import com.greenhouse.backend.work.operation.web.dto.WorkOperationCancellationEligibilityResponse;
import com.greenhouse.backend.work.operation.web.dto.WorkOperationCancellationRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/work-operations")
@RequiredArgsConstructor
public class WorkOperationVoidController {

  private final WorkOperationVoidService service;

  @PostMapping("/cancel-batch")
  public ApiResponse<WorkOperationBatchCancellationResponse> cancelBatch(
      @Valid @RequestBody WorkOperationBatchCancellationRequest request) {
    return ApiResponse.ok(service.cancelBatch(request));
  }

  @GetMapping("/{workOperationId}/void-eligibility")
  public ApiResponse<WorkOperationCancellationEligibilityResponse> eligibility(
      @PathVariable Long workOperationId) {
    return ApiResponse.ok(service.eligibility(workOperationId));
  }

  @GetMapping("/{workOperationId}/cancel-eligibility")
  public ApiResponse<WorkOperationCancellationEligibilityResponse> cancelEligibility(
      @PathVariable Long workOperationId) {
    return ApiResponse.ok(service.eligibility(workOperationId));
  }

  @PostMapping("/{workOperationId}/void")
  public ApiResponse<WorkOperationView> voidOperation(
      @PathVariable Long workOperationId,
      @Valid @RequestBody WorkOperationCancellationRequest request) {
    return ApiResponse.ok(service.voidOperation(workOperationId, request));
  }

  @PostMapping("/{workOperationId}/cancel")
  public ApiResponse<WorkOperationView> cancelOperation(
      @PathVariable Long workOperationId,
      @Valid @RequestBody WorkOperationCancellationRequest request) {
    return ApiResponse.ok(service.cancelOperation(workOperationId, request));
  }
}
