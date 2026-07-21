package com.greenhouse.backend.work.controller;

import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.work.application.InboundPottingOperationService;
import com.greenhouse.backend.work.application.InboundPottingPlanService;
import com.greenhouse.backend.work.application.StructureChangeExecutionService;
import com.greenhouse.backend.work.application.WorkOperationCorrectionService;
import com.greenhouse.backend.work.application.WorkOperationPlanService;
import com.greenhouse.backend.work.application.WorkOperationProgressService;
import com.greenhouse.backend.work.application.WorkOperationQueryService;
import com.greenhouse.backend.work.dto.OrchidGroupWorkHistoryResponse;
import com.greenhouse.backend.work.dto.InboundPottingPlanBatchCreateRequest;
import com.greenhouse.backend.work.dto.InboundPottingCandidateResponse;
import com.greenhouse.backend.work.dto.InboundPottingPlanCreateRequest;
import com.greenhouse.backend.work.dto.InboundPottingExecutionRequest;
import com.greenhouse.backend.work.dto.WorkOperationCreateRequest;
import com.greenhouse.backend.work.dto.WorkOperationBatchCreateRequest;
import com.greenhouse.backend.work.dto.WorkOperationCompleteRequest;
import com.greenhouse.backend.work.dto.WorkOperationCorrectionCreateRequest;
import com.greenhouse.backend.work.dto.WorkOperationCorrectionsResponse;
import com.greenhouse.backend.work.dto.WorkOperationResponse;
import com.greenhouse.backend.work.dto.WorkTargetPreviewRequest;
import com.greenhouse.backend.work.dto.WorkTargetPreviewResponse;
import com.greenhouse.backend.work.dto.WorkTargetExecutionRequest;
import com.greenhouse.backend.work.dto.StructureChangeExecutionRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.features", name = "work-operation-v2-enabled", havingValue = "true")
public class WorkOperationController {

	private final WorkOperationPlanService planService;
	private final WorkOperationProgressService progressService;
	private final WorkOperationQueryService queryService;
	private final StructureChangeExecutionService structureChangeExecutionService;
	private final InboundPottingPlanService inboundPottingPlanService;
	private final InboundPottingOperationService inboundPottingOperationService;
	private final WorkOperationCorrectionService workOperationCorrectionService;

	@PostMapping("/work-operations/target-preview")
	public ApiResponse<WorkTargetPreviewResponse> preview(@Valid @RequestBody WorkTargetPreviewRequest request) {
		return ApiResponse.ok(planService.preview(request));
	}

	@PostMapping("/work-operations")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<WorkOperationResponse> create(@Valid @RequestBody WorkOperationCreateRequest request) {
		return ApiResponse.ok(planService.create(request));
	}

	@PostMapping("/work-operations/batch")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<List<WorkOperationResponse>> createBatch(
			@Valid @RequestBody WorkOperationBatchCreateRequest request) {
		return ApiResponse.ok(planService.createBatch(request));
	}

	@PostMapping("/work-operations/record")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<WorkOperationResponse> createCompletedRecord(
			@Valid @RequestBody WorkOperationCreateRequest request) {
		return ApiResponse.ok(planService.createCompletedRecord(request));
	}

	@GetMapping("/work-operations/inbound-potting-candidates")
	public ApiResponse<List<InboundPottingCandidateResponse>> getInboundPottingCandidates() {
		return ApiResponse.ok(inboundPottingPlanService.getCandidates());
	}

	@PostMapping("/work-operations/inbound-potting-plans")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<WorkOperationResponse> createInboundPottingPlan(
			@Valid @RequestBody InboundPottingPlanCreateRequest request) {
		return ApiResponse.ok(inboundPottingPlanService.create(request));
	}

	@PostMapping("/work-operations/inbound-potting-plans/batch")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<List<WorkOperationResponse>> createInboundPottingPlans(
			@Valid @RequestBody InboundPottingPlanBatchCreateRequest request) {
		return ApiResponse.ok(inboundPottingPlanService.createBatch(request));
	}

	@PostMapping("/work-operations/inbound-potting-executions")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<WorkOperationResponse> executeInboundPotting(
			@Valid @RequestBody InboundPottingExecutionRequest request) {
		return ApiResponse.ok(inboundPottingOperationService.executeNow(request));
	}

	@GetMapping("/work-operations")
	public ApiResponse<List<WorkOperationResponse>> search(
			@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to,
			@RequestParam(required = false) com.greenhouse.backend.work.domain.WorkOperationStatus status,
			@RequestParam(defaultValue = "ALL") com.greenhouse.backend.work.domain.WorkOperationSearchView view,
			@RequestParam(required = false) com.greenhouse.backend.work.domain.WorkSourceScopeType scopeType,
			@RequestParam(required = false) Long scopeId) {
		return ApiResponse.ok(queryService.search(from, to, status, view, scopeType, scopeId));
	}

	@GetMapping("/work-operations/{workOperationId}")
	public ApiResponse<WorkOperationResponse> get(@PathVariable Long workOperationId) {
		return ApiResponse.ok(queryService.get(workOperationId));
	}

	@PostMapping("/work-operations/{workOperationId}/complete")
	public ApiResponse<WorkOperationResponse> complete(
			@PathVariable Long workOperationId,
			@Valid @RequestBody(required = false) WorkOperationCompleteRequest request) {
		return ApiResponse.ok(progressService.complete(
				workOperationId, request == null ? null : request.completedDate()));
	}

	@PostMapping("/work-operations/{workOperationId}/start")
	public ApiResponse<WorkOperationResponse> start(@PathVariable Long workOperationId) {
		return ApiResponse.ok(progressService.start(workOperationId));
	}

	@PostMapping("/work-operations/{workOperationId}/pause")
	public ApiResponse<WorkOperationResponse> pause(@PathVariable Long workOperationId) {
		return ApiResponse.ok(progressService.pause(workOperationId));
	}

	@PostMapping("/work-operations/{workOperationId}/resume")
	public ApiResponse<WorkOperationResponse> resume(@PathVariable Long workOperationId) {
		return ApiResponse.ok(progressService.resume(workOperationId));
	}

	@PostMapping("/work-operations/{workOperationId}/cancel")
	public ApiResponse<WorkOperationResponse> cancel(@PathVariable Long workOperationId) {
		return ApiResponse.ok(progressService.cancel(workOperationId));
	}

	@PostMapping("/work-operations/{workOperationId}/targets/{targetId}/start")
	public ApiResponse<WorkOperationResponse> startTarget(
			@PathVariable Long workOperationId,
			@PathVariable Long targetId,
			@Valid @RequestBody(required = false) WorkTargetExecutionRequest request) {
		return ApiResponse.ok(progressService.startTarget(
				workOperationId, targetId, request == null ? new WorkTargetExecutionRequest(null, null, null) : request));
	}

	@PostMapping("/work-operations/{workOperationId}/targets/{targetId}/complete")
	public ApiResponse<WorkOperationResponse> completeTarget(
			@PathVariable Long workOperationId,
			@PathVariable Long targetId,
			@Valid @RequestBody(required = false) WorkTargetExecutionRequest request) {
		return ApiResponse.ok(progressService.completeTarget(
				workOperationId, targetId, request == null ? new WorkTargetExecutionRequest(null, null, null) : request));
	}

	@PostMapping("/work-operations/{workOperationId}/targets/{targetId}/skip")
	public ApiResponse<WorkOperationResponse> skipTarget(
			@PathVariable Long workOperationId,
			@PathVariable Long targetId,
			@Valid @RequestBody(required = false) WorkTargetExecutionRequest request) {
		return ApiResponse.ok(progressService.skipTarget(
				workOperationId, targetId, request == null ? new WorkTargetExecutionRequest(null, null, null) : request));
	}

	@PostMapping("/work-operations/{workOperationId}/merge/complete")
	public ApiResponse<WorkOperationResponse> completeMerge(
			@PathVariable Long workOperationId,
			@Valid @RequestBody WorkTargetExecutionRequest request) {
		return ApiResponse.ok(structureChangeExecutionService.completeMerge(workOperationId, request));
	}

	@PostMapping("/work-operations/{workOperationId}/structure-change-executions")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<WorkOperationResponse> executeStructureChange(
			@PathVariable Long workOperationId,
			@Valid @RequestBody StructureChangeExecutionRequest request) {
		return ApiResponse.ok(structureChangeExecutionService.execute(workOperationId, request));
	}

	@GetMapping("/orchid-groups/{orchidGroupId}/work-history")
	public ApiResponse<List<OrchidGroupWorkHistoryResponse>> getOrchidGroupHistory(
			@PathVariable Long orchidGroupId) {
		return ApiResponse.ok(queryService.getOrchidGroupHistory(orchidGroupId));
	}

	@PostMapping("/work-operations/{workOperationId}/corrections")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<WorkOperationCorrectionsResponse> createCorrection(
			@PathVariable Long workOperationId,
			@Valid @RequestBody WorkOperationCorrectionCreateRequest request) {
		return ApiResponse.ok(workOperationCorrectionService.create(workOperationId, request));
	}

	@GetMapping("/work-operations/{workOperationId}/corrections")
	public ApiResponse<WorkOperationCorrectionsResponse> getCorrections(
			@PathVariable Long workOperationId) {
		return ApiResponse.ok(workOperationCorrectionService.get(workOperationId));
	}
}
