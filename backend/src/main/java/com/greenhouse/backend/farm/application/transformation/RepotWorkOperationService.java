package com.greenhouse.backend.farm.application.transformation;

import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupResponse;
import com.greenhouse.backend.farm.dto.transformation.RepotWorkOperationRequest;
import com.greenhouse.backend.farm.dto.transformation.RepotWorkOperationResponse;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.work.api.operation.ImmediateWorkExecutionApi;
import com.greenhouse.backend.work.api.operation.WorkOperationQueryApi;
import com.greenhouse.backend.work.domain.operation.WorkTypeDefinition;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RepotWorkOperationService {

  private final Clock clock;

  private final LegacyStructureChangeRequestMapper legacyRequestMapper;

  private final ImmediateWorkExecutionApi immediateWorkExecutionService;

  private final WorkOperationQueryApi queryService;

  private final OrchidGroupRepository orchidGroupRepository;

  public RepotWorkOperationService(
      ImmediateWorkExecutionApi immediateWorkExecutionService,
      WorkOperationQueryApi queryService,
      OrchidGroupRepository orchidGroupRepository,
      Clock clock,
      LegacyStructureChangeRequestMapper legacyRequestMapper) {
    this.immediateWorkExecutionService = immediateWorkExecutionService;
    this.queryService = queryService;
    this.orchidGroupRepository = orchidGroupRepository;
    this.clock = clock;
    this.legacyRequestMapper = legacyRequestMapper;
  }

  public RepotWorkOperationResponse execute(RepotWorkOperationRequest request) {
    int totalResultQuantity = request.results().stream().mapToInt(row -> row.quantity()).sum();
    int lossQuantity = Math.max(0, request.inputQuantity() - totalResultQuantity);
    int increaseQuantity = Math.max(0, totalResultQuantity - request.inputQuantity());
    var sourceGroups =
        orchidGroupRepository.findAllForUpdateByIdIn(List.of(request.sourceOrchidGroupId()));
    if (sourceGroups.isEmpty()) {
      throw new NotFoundException("원본 난 묶음을 찾을 수 없습니다.");
    }
    var sourceGroup = sourceGroups.getFirst();
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("sourceOrchidGroupId", request.sourceOrchidGroupId());
    details.put("inputQuantity", request.inputQuantity());
    details.put("lossQuantity", lossQuantity);
    details.put("increaseQuantity", increaseQuantity);
    details.put("resultCount", request.results().size());
    var operation =
        immediateWorkExecutionService.executeVarietyHistoryForTarget(
            normalizeRequired(request.idempotencyKey()),
            WorkTypeDefinition.REPOT.name(),
            sourceGroup.getVarietyName(),
            request.workDate(),
            normalize(request.worker()),
            normalize(request.memo()),
            request.sourceOrchidGroupId(),
            details,
            legacyRequestMapper.fromRequest(request));
    return response(operation.id());
  }

  @Transactional(readOnly = true)
  public RepotWorkOperationResponse get(Long operationId) {
    return response(operationId);
  }

  private RepotWorkOperationResponse response(Long operationId) {
    var businessDate = TimeConfig.farmToday(clock);
    var operation = queryService.get(operationId);
    var resultIds =
        immediateWorkExecutionService.getStructureChangeResultOrchidGroupIds(
            operationId, WorkTypeDefinition.REPOT.name());
    var source =
        orchidGroupRepository
            .findDetailById(operation.sourceScopeId())
            .orElseThrow(() -> new NotFoundException("원본 난 묶음을 찾을 수 없습니다."));
    var groupsById =
        orchidGroupRepository.findDetailsByIds(resultIds).stream()
            .collect(Collectors.toMap(group -> group.getId(), group -> group));
    var results =
        resultIds.stream()
            .filter(groupsById::containsKey)
            .map(id -> OrchidGroupResponse.from(groupsById.get(id), businessDate))
            .toList();
    return new RepotWorkOperationResponse(
        operation,
        OrchidGroupResponse.from(source, businessDate),
        results,
        integerDetail(operation.details(), "inputQuantity"),
        integerDetail(operation.details(), "lossQuantity"),
        integerDetail(operation.details(), "increaseQuantity"));
  }

  private Integer integerDetail(Map<String, Object> details, String key) {
    Object value = details == null ? null : details.get(key);
    return value instanceof Number number ? number.intValue() : null;
  }

  private String normalize(String value) {
    if (value == null) return null;
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }

  private String normalizeRequired(String value) {
    String normalized = normalize(value);
    if (normalized == null) throw new IllegalArgumentException("필수 문자열 값은 비워둘 수 없습니다.");
    return normalized;
  }
}
