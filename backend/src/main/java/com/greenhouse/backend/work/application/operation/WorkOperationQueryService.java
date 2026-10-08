package com.greenhouse.backend.work.application.operation;

import com.greenhouse.backend.common.api.PageRequests;
import com.greenhouse.backend.common.api.PageResponse;
import com.greenhouse.backend.common.api.QueryLimits;
import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.work.api.operation.WorkOperationStatus;
import com.greenhouse.backend.work.api.operation.WorkSourceScopeType;
import com.greenhouse.backend.work.api.target.WorkTargetSelection;
import com.greenhouse.backend.work.domain.operation.WorkOperationSearchView;
import com.greenhouse.backend.work.dto.operation.OrchidGroupWorkHistoryResponse;
import com.greenhouse.backend.work.dto.operation.WorkHistoryScopeType;
import com.greenhouse.backend.work.dto.operation.WorkOperationSummaryResponse;
import com.greenhouse.backend.work.repository.WorkEffectOrchidGroupRepository;
import com.greenhouse.backend.work.repository.WorkOperationRepository;
import com.greenhouse.backend.work.repository.WorkOperationTargetRepository;
import com.greenhouse.backend.work.spi.target.WorkTargetResolver;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class WorkOperationQueryService {

  private final WorkOperationRepository operationRepository;

  private final WorkOperationTargetRepository targetRepository;

  private final WorkEffectOrchidGroupRepository effectOrchidGroupRepository;

  private final WorkTargetResolver workTargetResolver;

  private final WorkOperationResponseAssembler responseAssembler;

  private final WorkOperationSummaryAssembler summaryAssembler;

  private final Clock clock;

  public WorkOperationView get(Long operationId) {
    return responseAssembler.assemble(
        operationRepository
            .findWithWorkTypeById(operationId)
            .orElseThrow(() -> new NotFoundException("작업을 찾을 수 없습니다.")));
  }

  public List<WorkOperationView> getAll(Collection<Long> operationIds) {
    if (operationIds.isEmpty()) {
      return List.of();
    }
    var operationsById =
        responseAssembler
            .assembleAll(operationRepository.findWithWorkTypeByIdIn(operationIds))
            .stream()
            .collect(Collectors.toMap(WorkOperationView::id, Function.identity()));
    return operationIds.stream()
        .map(
            operationId -> {
              var response = operationsById.get(operationId);
              if (response == null) {
                throw new NotFoundException("작업을 찾을 수 없습니다.");
              }
              return response;
            })
        .toList();
  }

  public PageResponse<WorkOperationSummaryResponse> search(
      LocalDate fromDate,
      LocalDate toDate,
      WorkOperationStatus status,
      WorkOperationSearchView view,
      WorkSourceScopeType sourceScopeType,
      Long sourceScopeId,
      String keyword,
      int page,
      int size) {

    return search(
        fromDate, toDate, status, view, sourceScopeType, sourceScopeId, keyword, null, page, size);
  }

  public PageResponse<WorkOperationSummaryResponse> search(
      LocalDate fromDate,
      LocalDate toDate,
      WorkOperationStatus status,
      WorkOperationSearchView view,
      WorkSourceScopeType sourceScopeType,
      Long sourceScopeId,
      String keyword,
      Boolean hasCorrections,
      int page,
      int size) {
    validateDates(fromDate, toDate);
    PageRequests.validate(page, size);
    if (sourceScopeId != null && sourceScopeType == null) {
      throw new IllegalArgumentException("대상 범위 ID를 조회하려면 대상 범위 유형이 필요합니다.");
    }
    LocalDate farmToday = TimeConfig.farmToday(clock);
    var operationPage =
        operationRepository.search(
            fromDate,
            toDate,
            status,
            view,
            TimeConfig.farmDayStartUtc(farmToday),
            sourceScopeType,
            sourceScopeId,
            keyword,
            hasCorrections,
            PageRequest.of(page, size));
    return new PageResponse<>(
        summaryAssembler.assembleAll(operationPage.getContent()),
        operationPage.getNumber(),
        operationPage.getSize(),
        operationPage.getTotalElements(),
        operationPage.getTotalPages());
  }

  public List<WorkOperationSummaryResponse> getCalendar(
      LocalDate fromDate,
      LocalDate toDate,
      WorkOperationStatus status,
      WorkOperationSearchView view) {
    return getCalendar(fromDate, toDate, status, view, null);
  }

  public List<WorkOperationSummaryResponse> getCalendar(
      LocalDate fromDate,
      LocalDate toDate,
      WorkOperationStatus status,
      WorkOperationSearchView view,
      Boolean hasCorrections) {
    validateDates(fromDate, toDate);
    LocalDate farmToday = TimeConfig.farmToday(clock);
    if (fromDate == null || toDate == null || ChronoUnit.DAYS.between(fromDate, toDate) > 365) {
      throw new IllegalArgumentException("캘린더 조회는 시작일과 종료일을 지정하고 366일 이내로 요청해주세요.");
    }
    var operations =
        operationRepository.searchAll(
            fromDate, toDate, status, view, TimeConfig.farmDayStartUtc(farmToday), hasCorrections);
    QueryLimits.requireWithin(operations.size(), QueryLimits.CALENDAR_ROWS);
    return summaryAssembler.assembleAll(operations);
  }

  public List<OrchidGroupWorkHistoryResponse> getOrchidGroupHistory(Long orchidGroupId) {
    return historyPage(WorkHistoryScopeType.ORCHID_GROUP, orchidGroupId, 0, QueryLimits.LEGACY_ROWS)
        .content();
  }

  public PageResponse<OrchidGroupWorkHistoryResponse> getWorkHistory(
      WorkHistoryScopeType historyScopeType, Long historyScopeId, int page, int size) {
    PageRequests.validate(page, size);
    return historyPage(historyScopeType, historyScopeId, page, size);
  }

  private PageResponse<OrchidGroupWorkHistoryResponse> historyPage(
      WorkHistoryScopeType historyScopeType, Long historyScopeId, int page, int size) {
    ResolvedHistoryScope scope = resolveHistoryScope(historyScopeType, historyScopeId);
    if (scope.orchidGroupIds().isEmpty()) {
      return new PageResponse<>(List.of(), page, size, 0, 0);
    }
    var pageable =
        PageRequest.of(
            page,
            size,
            Sort.by(Sort.Direction.DESC, "plannedStartDate")
                .and(Sort.by(Sort.Direction.DESC, "id")));
    var operationPage = operationRepository.findHistoryPage(scope.orchidGroupIds(), pageable);
    if (operationPage.isEmpty()) {
      return new PageResponse<>(
          List.of(),
          operationPage.getNumber(),
          operationPage.getSize(),
          operationPage.getTotalElements(),
          operationPage.getTotalPages());
    }
    List<Long> operationIds =
        operationPage.getContent().stream().map(operation -> operation.getId()).toList();
    Map<Long, OrchidGroupWorkHistoryResponse> historyByOperationId =
        assembleHistoryPage(operationIds, scope.orchidGroupIds(), scope.currentLocations());
    return PageResponse.from(
        operationPage.map(operation -> historyByOperationId.get(operation.getId())));
  }

  private ResolvedHistoryScope resolveHistoryScope(
      WorkHistoryScopeType historyScopeType, Long historyScopeId) {
    validateHistoryScope(historyScopeType, historyScopeId);
    WorkSourceScopeType sourceScopeType = historyScopeType.toSourceScopeType();
    var resolvedTargets =
        workTargetResolver.resolve(
            WorkTargetSelection.identifiedScope(sourceScopeType, historyScopeId));
    Set<Long> orchidGroupIds =
        resolvedTargets.stream().map(target -> target.orchidGroupId()).collect(Collectors.toSet());
    Map<Long, Map<String, Object>> currentLocations =
        resolvedTargets.stream()
            .collect(
                Collectors.toMap(
                    target -> target.orchidGroupId(),
                    target -> target.location(),
                    (left, right) -> left));
    return new ResolvedHistoryScope(orchidGroupIds, currentLocations);
  }

  private Map<Long, OrchidGroupWorkHistoryResponse> assembleHistoryPage(
      List<Long> operationIds,
      Set<Long> orchidGroupIds,
      Map<Long, Map<String, Object>> currentLocations) {
    var historyByOperationId = new LinkedHashMap<Long, OrchidGroupWorkHistoryResponse>();
    targetRepository
        .findByWorkOperationIdInAndOrchidGroupIdInAndExcludedAtIsNullOrderByWorkOperationIdAscIdAsc(
            operationIds, orchidGroupIds)
        .forEach(
            target ->
                historyByOperationId.putIfAbsent(
                    target.getWorkOperation().getId(),
                    OrchidGroupWorkHistoryResponse.from(
                        target,
                        currentLocations.getOrDefault(
                            target.getOrchidGroupId(), target.getLocationSnapshot()))));
    effectOrchidGroupRepository
        .findByWorkAppliedEffectWorkOperationIdInAndOrchidGroupIdInOrderByWorkAppliedEffectWorkOperationIdAscIdAsc(
            operationIds, orchidGroupIds)
        .forEach(
            effectGroup ->
                historyByOperationId.putIfAbsent(
                    effectGroup.getWorkAppliedEffect().getWorkOperation().getId(),
                    OrchidGroupWorkHistoryResponse.fromEffect(
                        effectGroup, currentLocations.get(effectGroup.getOrchidGroupId()))));
    return historyByOperationId;
  }

  private void validateHistoryScope(WorkHistoryScopeType historyScopeType, Long historyScopeId) {
    if (historyScopeType == null || historyScopeId == null) {
      throw new IllegalArgumentException("작업 이력 조회 범위 유형과 ID가 필요합니다.");
    }
  }

  private record ResolvedHistoryScope(
      Set<Long> orchidGroupIds, Map<Long, Map<String, Object>> currentLocations) {}

  private void validateDates(LocalDate startDate, LocalDate endDate) {
    if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
      throw new IllegalArgumentException("예정 종료일은 예정 시작일보다 빠를 수 없습니다.");
    }
  }
}
