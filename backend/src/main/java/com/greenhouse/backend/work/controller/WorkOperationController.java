package com.greenhouse.backend.work.controller;

import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.common.api.ErrorResponse;
import com.greenhouse.backend.common.api.PageResponse;
import com.greenhouse.backend.work.api.correction.WorkCorrectionCommand;
import com.greenhouse.backend.work.api.effect.InboundPottingCommand;
import com.greenhouse.backend.work.api.effect.StructureChangeCommand;
import com.greenhouse.backend.work.application.correction.WorkOperationCorrectionService;
import com.greenhouse.backend.work.application.operation.InboundPottingOperationService;
import com.greenhouse.backend.work.application.operation.InboundPottingPlanService;
import com.greenhouse.backend.work.application.operation.StructureChangeExecutionService;
import com.greenhouse.backend.work.application.operation.StructureChangeRecordService;
import com.greenhouse.backend.work.application.operation.WorkOperationDetailService;
import com.greenhouse.backend.work.application.operation.WorkOperationGraphQueryService;
import com.greenhouse.backend.work.application.operation.WorkOperationPlanService;
import com.greenhouse.backend.work.application.operation.WorkOperationProgressService;
import com.greenhouse.backend.work.application.operation.WorkOperationQueryService;
import com.greenhouse.backend.work.application.operation.WorkOperationRelationQueryService;
import com.greenhouse.backend.work.application.operation.WorkOperationView;
import com.greenhouse.backend.work.domain.operation.WorkOperationSearchView;
import com.greenhouse.backend.work.domain.operation.WorkOperationStatus;
import com.greenhouse.backend.work.domain.operation.WorkSourceScopeType;
import com.greenhouse.backend.work.dto.correction.WorkOperationCorrectionsResponse;
import com.greenhouse.backend.work.dto.effect.DiscardRecordCreateRequest;
import com.greenhouse.backend.work.dto.effect.InboundPottingCandidateResponse;
import com.greenhouse.backend.work.dto.effect.InboundPottingPlanBatchCreateRequest;
import com.greenhouse.backend.work.dto.effect.InboundPottingPlanCreateRequest;
import com.greenhouse.backend.work.dto.effect.InboundPottingRecordCreateRequest;
import com.greenhouse.backend.work.dto.effect.StructureChangeRecordBatchCreateRequest;
import com.greenhouse.backend.work.dto.effect.StructureChangeRecordCreateRequest;
import com.greenhouse.backend.work.dto.operation.OrchidGroupWorkHistoryResponse;
import com.greenhouse.backend.work.dto.operation.WorkHistoryScopeType;
import com.greenhouse.backend.work.dto.operation.WorkOperationBatchCreateRequest;
import com.greenhouse.backend.work.dto.operation.WorkOperationCompleteRequest;
import com.greenhouse.backend.work.dto.operation.WorkOperationCreateRequest;
import com.greenhouse.backend.work.dto.operation.WorkOperationDetailResponse;
import com.greenhouse.backend.work.dto.operation.WorkOperationGraphDetail;
import com.greenhouse.backend.work.dto.operation.WorkOperationGraphResponse;
import com.greenhouse.backend.work.dto.operation.WorkOperationRelationKind;
import com.greenhouse.backend.work.dto.operation.WorkOperationSummaryResponse;
import com.greenhouse.backend.work.dto.operation.WorkOperationTitleUpdateRequest;
import com.greenhouse.backend.work.dto.target.WorkTargetExecutionRequest;
import com.greenhouse.backend.work.dto.target.WorkTargetPreviewRequest;
import com.greenhouse.backend.work.dto.target.WorkTargetPreviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class WorkOperationController {

  private final WorkOperationPlanService planService;

  private final WorkOperationProgressService progressService;

  private final WorkOperationQueryService queryService;

  private final StructureChangeExecutionService structureChangeExecutionService;

  private final StructureChangeRecordService structureChangeRecordService;

  private final InboundPottingPlanService inboundPottingPlanService;

  private final InboundPottingOperationService inboundPottingOperationService;

  private final WorkOperationCorrectionService workOperationCorrectionService;

  private final WorkOperationDetailService workOperationDetailService;

  private final WorkOperationGraphQueryService workOperationGraphQueryService;

  private final WorkOperationRelationQueryService workOperationRelationQueryService;

  @PostMapping("/work-operations/target-preview")
  public ApiResponse<WorkTargetPreviewResponse> preview(
      @Valid @RequestBody WorkTargetPreviewRequest request) {
    return ApiResponse.ok(planService.preview(request));
  }

  /**
   * @deprecated Use {@code POST /api/work-operations/batch}, which also supports a single work
   *     operation.
   */
  @Deprecated(since = "2026-08", forRemoval = false)
  @PostMapping("/work-operations")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<WorkOperationView> create(
      @Valid @RequestBody WorkOperationCreateRequest request,
      @Parameter(
              description = "일반 단건 계획 생성의 재전송 키. 같은 키·입력은 최초 응답을 반환한다.",
              schema = @Schema(minLength = 1, maxLength = 100))
          @RequestHeader(name = "Idempotency-Key", required = false)
          String key) {
    return ApiResponse.ok(planService.create(request, key));
  }

  @PostMapping("/work-operations/batch")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<List<WorkOperationView>> createBatch(
      @Valid @RequestBody WorkOperationBatchCreateRequest request,
      @Parameter(
              description = "일반 일괄 계획 생성의 재전송 키. 품종별 전체 결과의 최초 응답을 반환한다.",
              schema = @Schema(minLength = 1, maxLength = 100))
          @RequestHeader(name = "Idempotency-Key", required = false)
          String key) {
    return ApiResponse.ok(planService.createBatch(request, key));
  }

  @PostMapping("/work-operations/record")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<WorkOperationView> createCompletedRecord(
      @Valid @RequestBody WorkOperationCreateRequest request,
      @Parameter(
              description = "일반 완료 기록 생성의 재전송 키. 같은 키·입력은 최초 응답을 반환한다.",
              schema = @Schema(minLength = 1, maxLength = 100))
          @RequestHeader(name = "Idempotency-Key", required = false)
          String key) {
    return ApiResponse.ok(planService.createCompletedRecord(request, key));
  }

  /**
   * @deprecated Use {@code POST /api/work-operations/structure-change-records/batch}.
   */
  @Deprecated(since = "2026-08", forRemoval = false)
  @PostMapping("/work-operations/structure-change-records")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<WorkOperationView> createStructureChangeRecord(
      @Valid @RequestBody StructureChangeRecordCreateRequest request) {
    return ApiResponse.ok(structureChangeRecordService.createStructureChangeRecord(request));
  }

  @PostMapping("/work-operations/structure-change-records/batch")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<List<WorkOperationView>> createStructureChangeRecords(
      @Valid @RequestBody StructureChangeRecordBatchCreateRequest request) {
    return ApiResponse.ok(structureChangeRecordService.createStructureChangeRecords(request));
  }

  @PostMapping("/work-operations/discard-records")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<List<WorkOperationView>> createDiscardRecord(
      @Valid @RequestBody DiscardRecordCreateRequest request) {
    return ApiResponse.ok(structureChangeRecordService.createDiscardRecord(request));
  }

  @PostMapping("/work-operations/inbound-potting-records")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<List<WorkOperationView>> createInboundPottingRecord(
      @Valid @RequestBody InboundPottingRecordCreateRequest request) {
    return ApiResponse.ok(structureChangeRecordService.createInboundPottingRecord(request));
  }

  @GetMapping("/work-operations/inbound-potting-candidates")
  public ApiResponse<List<InboundPottingCandidateResponse>> getInboundPottingCandidates() {
    return ApiResponse.ok(inboundPottingPlanService.getCandidates());
  }

  /**
   * @deprecated Use {@code POST /api/work-operations/inbound-potting-plans/batch}.
   */
  @Deprecated(since = "2026-08", forRemoval = false)
  @PostMapping("/work-operations/inbound-potting-plans")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<WorkOperationView> createInboundPottingPlan(
      @Valid @RequestBody InboundPottingPlanCreateRequest request) {
    return ApiResponse.ok(inboundPottingPlanService.create(request));
  }

  @PostMapping("/work-operations/inbound-potting-plans/batch")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<List<WorkOperationView>> createInboundPottingPlans(
      @Valid @RequestBody InboundPottingPlanBatchCreateRequest request) {
    return ApiResponse.ok(inboundPottingPlanService.createBatch(request));
  }

  @PostMapping("/work-operations/inbound-potting-executions")
  @Deprecated(since = "2026-08", forRemoval = false)
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<WorkOperationView> executeInboundPotting(
      @Valid @RequestBody InboundPottingCommand request) {
    return ApiResponse.ok(inboundPottingOperationService.executeNow(request));
  }

  @GetMapping("/work-operations")
  public ApiResponse<PageResponse<WorkOperationSummaryResponse>> search(
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(required = false) WorkOperationStatus status,
      @RequestParam(defaultValue = "ALL") WorkOperationSearchView view,
      @RequestParam(required = false) WorkSourceScopeType sourceScopeType,
      @RequestParam(required = false) Long sourceScopeId,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) Boolean hasCorrections,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.ok(
        queryService.search(
            from,
            to,
            status,
            view,
            sourceScopeType,
            sourceScopeId,
            keyword,
            hasCorrections,
            page,
            size));
  }

  @GetMapping("/work-operations/calendar")
  @Operation(
      description =
          "양 끝 날짜를 포함해 최대 366일, 최대 1,000개 작업을 완전한 목록으로 반환한다. 기간 초과는 400, 결과 건수 초과는 422 QUERY_LIMIT_EXCEEDED. 초과 시 기간·필터를 좁히거나 작업 페이지 조회를 사용한다.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "200",
      description = "조회 범위의 전체 작업 요약",
      useReturnTypeSchema = true)
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "400",
      description = "기간 또는 조회 조건 오류 (VALIDATION_ERROR)",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "422",
      description = "조회 결과 상한 초과 (QUERY_LIMIT_EXCEEDED)",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  public ApiResponse<List<WorkOperationSummaryResponse>> getCalendar(
      @RequestParam LocalDate from,
      @RequestParam LocalDate to,
      @RequestParam(required = false) WorkOperationStatus status,
      @RequestParam(defaultValue = "ALL") WorkOperationSearchView view,
      @RequestParam(required = false) Boolean hasCorrections) {
    return ApiResponse.ok(queryService.getCalendar(from, to, status, view, hasCorrections));
  }

  @GetMapping("/work-operations/{workOperationId}")
  public ApiResponse<WorkOperationView> get(@PathVariable Long workOperationId) {
    return ApiResponse.ok(queryService.get(workOperationId));
  }

  @PatchMapping("/work-operations/{workOperationId}/title")
  public ApiResponse<WorkOperationView> updateTitle(
      @PathVariable Long workOperationId,
      @Valid @RequestBody WorkOperationTitleUpdateRequest request) {
    return ApiResponse.ok(progressService.updateTitle(workOperationId, request.title()));
  }

  @GetMapping("/work-operations/{workOperationId}/details")
  public ApiResponse<WorkOperationDetailResponse> getDetails(@PathVariable Long workOperationId) {
    return ApiResponse.ok(workOperationDetailService.get(workOperationId));
  }

  @GetMapping("/work-operations/{workOperationId}/graph")
  @Operation(
      description =
          "노드 수 외에도 작업 대상·효과·정정 참조는 조회별 최대 1,000건, Mutation 관계는 최대 maxNodes × 4건으로 제한한다. 일부 노드·참조·관계가 생략되면 truncated=true이며 전체 이력으로 취급하지 않는다.")
  public ApiResponse<WorkOperationGraphResponse> getGraph(
      @PathVariable Long workOperationId,
      @RequestParam(defaultValue = "WORK") WorkOperationGraphDetail detail,
      @RequestParam(defaultValue = "1") int depth,
      @RequestParam(defaultValue = "120") int maxNodes) {
    return ApiResponse.ok(
        workOperationGraphQueryService.get(workOperationId, detail, depth, maxNodes));
  }

  @GetMapping("/work-operations/{workOperationId}/relations")
  public ApiResponse<List<WorkOperationSummaryResponse>> getRelations(
      @PathVariable Long workOperationId, @RequestParam WorkOperationRelationKind kind) {
    return ApiResponse.ok(workOperationRelationQueryService.get(workOperationId, kind));
  }

  @PostMapping("/work-operations/{workOperationId}/complete")
  public ApiResponse<WorkOperationView> complete(
      @PathVariable Long workOperationId,
      @Valid @RequestBody(required = false) WorkOperationCompleteRequest request) {
    return ApiResponse.ok(
        progressService.complete(
            workOperationId, request == null ? null : request.completedDate()));
  }

  @PostMapping("/work-operations/{workOperationId}/start")
  public ApiResponse<WorkOperationView> start(@PathVariable Long workOperationId) {
    return ApiResponse.ok(progressService.start(workOperationId));
  }

  @PostMapping("/work-operations/{workOperationId}/pause")
  public ApiResponse<WorkOperationView> pause(@PathVariable Long workOperationId) {
    return ApiResponse.ok(progressService.pause(workOperationId));
  }

  @PostMapping("/work-operations/{workOperationId}/resume")
  public ApiResponse<WorkOperationView> resume(@PathVariable Long workOperationId) {
    return ApiResponse.ok(progressService.resume(workOperationId));
  }

  @PostMapping("/work-operations/{workOperationId}/end-remaining")
  public ApiResponse<WorkOperationView> endRemaining(@PathVariable Long workOperationId) {
    return ApiResponse.ok(progressService.endRemaining(workOperationId));
  }

  @PostMapping("/work-operations/{workOperationId}/targets/{targetId}/start")
  public ApiResponse<WorkOperationView> startTarget(
      @PathVariable Long workOperationId,
      @PathVariable Long targetId,
      @Valid @RequestBody(required = false) WorkTargetExecutionRequest request) {
    return ApiResponse.ok(
        progressService.startTarget(
            workOperationId,
            targetId,
            request == null ? new WorkTargetExecutionRequest(null, null, null) : request));
  }

  @PostMapping("/work-operations/{workOperationId}/targets/{targetId}/complete")
  public ApiResponse<WorkOperationView> completeTarget(
      @PathVariable Long workOperationId,
      @PathVariable Long targetId,
      @Valid @RequestBody(required = false) WorkTargetExecutionRequest request) {
    return ApiResponse.ok(
        progressService.completeTarget(
            workOperationId,
            targetId,
            request == null ? new WorkTargetExecutionRequest(null, null, null) : request));
  }

  @PostMapping("/work-operations/{workOperationId}/targets/{targetId}/skip")
  public ApiResponse<WorkOperationView> skipTarget(
      @PathVariable Long workOperationId,
      @PathVariable Long targetId,
      @Valid @RequestBody(required = false) WorkTargetExecutionRequest request) {
    return ApiResponse.ok(
        progressService.skipTarget(
            workOperationId,
            targetId,
            request == null ? new WorkTargetExecutionRequest(null, null, null) : request));
  }

  /**
   * @deprecated Use {@code POST
   *     /api/work-operations/{workOperationId}/structure-change-executions}.
   */
  @Deprecated(since = "2026-08", forRemoval = false)
  @PostMapping("/work-operations/{workOperationId}/merge/complete")
  public ApiResponse<WorkOperationView> completeMerge(
      @PathVariable Long workOperationId, @Valid @RequestBody WorkTargetExecutionRequest request) {
    return ApiResponse.ok(structureChangeExecutionService.completeMerge(workOperationId, request));
  }

  @PostMapping("/work-operations/{workOperationId}/structure-change-executions")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<WorkOperationView> executeStructureChange(
      @PathVariable Long workOperationId, @Valid @RequestBody StructureChangeCommand request) {
    return ApiResponse.ok(structureChangeExecutionService.execute(workOperationId, request));
  }

  /**
   * @deprecated Use {@code GET /api/work-history} with {@code historyScopeType=ORCHID_GROUP}.
   */
  @Deprecated(since = "2026-08", forRemoval = false)
  @GetMapping("/orchid-groups/{orchidGroupId}/work-history")
  @Operation(
      description =
          "호환 경로: 작업일·ID 내림차순 최신 500개 작업을 반환한다. 전체 이력은 GET /api/work-history의 페이지 조회를 사용한다.")
  public ApiResponse<List<OrchidGroupWorkHistoryResponse>> getOrchidGroupHistory(
      @PathVariable Long orchidGroupId) {
    return ApiResponse.ok(queryService.getOrchidGroupHistory(orchidGroupId));
  }

  @GetMapping("/work-history")
  public ApiResponse<PageResponse<OrchidGroupWorkHistoryResponse>> getWorkHistory(
      @RequestParam WorkHistoryScopeType historyScopeType,
      @RequestParam Long historyScopeId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.ok(
        queryService.getWorkHistory(historyScopeType, historyScopeId, page, size));
  }

  @PostMapping("/work-operations/{workOperationId}/corrections")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<WorkOperationCorrectionsResponse> createCorrection(
      @PathVariable Long workOperationId, @Valid @RequestBody WorkCorrectionCommand request) {
    return ApiResponse.ok(workOperationCorrectionService.create(workOperationId, request));
  }

  @GetMapping("/work-operations/{workOperationId}/corrections")
  public ApiResponse<WorkOperationCorrectionsResponse> getCorrections(
      @PathVariable Long workOperationId) {
    return ApiResponse.ok(workOperationCorrectionService.get(workOperationId));
  }
}
