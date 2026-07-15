package com.greenhouse.backend.work.controller;

import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.work.application.WorkOperationService;
import com.greenhouse.backend.work.dto.OrchidGroupWorkHistoryResponse;
import com.greenhouse.backend.work.dto.WorkOperationCreateRequest;
import com.greenhouse.backend.work.dto.WorkOperationResponse;
import com.greenhouse.backend.work.dto.WorkTargetPreviewRequest;
import com.greenhouse.backend.work.dto.WorkTargetPreviewResponse;
import com.greenhouse.backend.work.dto.WorkTargetExecutionRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.features", name = "work-operation-v2-enabled", havingValue = "true")
public class WorkOperationController {

	private final WorkOperationService workOperationService;

	@PostMapping("/work-operations/target-preview")
	public ApiResponse<WorkTargetPreviewResponse> preview(@Valid @RequestBody WorkTargetPreviewRequest request) {
		return ApiResponse.ok(workOperationService.preview(request));
	}

	@PostMapping("/work-operations")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<WorkOperationResponse> create(@Valid @RequestBody WorkOperationCreateRequest request) {
		return ApiResponse.ok(workOperationService.create(request));
	}

	@GetMapping("/work-operations/{workOperationId}")
	public ApiResponse<WorkOperationResponse> get(@PathVariable Long workOperationId) {
		return ApiResponse.ok(workOperationService.get(workOperationId));
	}

	@PostMapping("/work-operations/{workOperationId}/complete")
	public ApiResponse<WorkOperationResponse> complete(@PathVariable Long workOperationId) {
		return ApiResponse.ok(workOperationService.complete(workOperationId));
	}

	@PostMapping("/work-operations/{workOperationId}/start")
	public ApiResponse<WorkOperationResponse> start(@PathVariable Long workOperationId) {
		return ApiResponse.ok(workOperationService.start(workOperationId));
	}

	@PostMapping("/work-operations/{workOperationId}/pause")
	public ApiResponse<WorkOperationResponse> pause(@PathVariable Long workOperationId) {
		return ApiResponse.ok(workOperationService.pause(workOperationId));
	}

	@PostMapping("/work-operations/{workOperationId}/resume")
	public ApiResponse<WorkOperationResponse> resume(@PathVariable Long workOperationId) {
		return ApiResponse.ok(workOperationService.resume(workOperationId));
	}

	@PostMapping("/work-operations/{workOperationId}/cancel")
	public ApiResponse<WorkOperationResponse> cancel(@PathVariable Long workOperationId) {
		return ApiResponse.ok(workOperationService.cancel(workOperationId));
	}

	@PostMapping("/work-operations/{workOperationId}/targets/{targetId}/start")
	public ApiResponse<WorkOperationResponse> startTarget(
			@PathVariable Long workOperationId,
			@PathVariable Long targetId,
			@Valid @RequestBody(required = false) WorkTargetExecutionRequest request) {
		return ApiResponse.ok(workOperationService.startTarget(
				workOperationId, targetId, request == null ? new WorkTargetExecutionRequest(null, null) : request));
	}

	@PostMapping("/work-operations/{workOperationId}/targets/{targetId}/complete")
	public ApiResponse<WorkOperationResponse> completeTarget(
			@PathVariable Long workOperationId,
			@PathVariable Long targetId,
			@Valid @RequestBody(required = false) WorkTargetExecutionRequest request) {
		return ApiResponse.ok(workOperationService.completeTarget(
				workOperationId, targetId, request == null ? new WorkTargetExecutionRequest(null, null) : request));
	}

	@PostMapping("/work-operations/{workOperationId}/targets/{targetId}/skip")
	public ApiResponse<WorkOperationResponse> skipTarget(
			@PathVariable Long workOperationId,
			@PathVariable Long targetId,
			@Valid @RequestBody(required = false) WorkTargetExecutionRequest request) {
		return ApiResponse.ok(workOperationService.skipTarget(
				workOperationId, targetId, request == null ? new WorkTargetExecutionRequest(null, null) : request));
	}

	@GetMapping("/orchid-groups/{orchidGroupId}/work-history")
	public ApiResponse<List<OrchidGroupWorkHistoryResponse>> getOrchidGroupHistory(
			@PathVariable Long orchidGroupId) {
		return ApiResponse.ok(workOperationService.getOrchidGroupHistory(orchidGroupId));
	}
}
