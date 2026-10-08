package com.greenhouse.backend.farm.application.orchid;

import com.greenhouse.backend.audit.domain.AuditAction;
import com.greenhouse.backend.audit.domain.AuditSource;
import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.api.orchid.CancelOrchidGroupCreationMutationCommand;
import com.greenhouse.backend.farm.api.orchid.CreateOrchidGroupMutationCommand;
import com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationDetails;
import com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationSources;
import com.greenhouse.backend.farm.api.orchid.UpdateOrchidGroupMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationEngine;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupBatchUpdateRequest;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupCreateRequest;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupResponse;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupUpdateRequest;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.farm.spi.orchid.OrchidGroupUsageInspector;
import com.greenhouse.backend.farm.structure.repository.BedZoneRepository;
import com.greenhouse.backend.work.api.target.WorkOrchidGroupUsageApi;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class OrchidGroupCommandService {

  private static final int ID_BATCH_SIZE = 500;

  private final OrchidGroupRepository orchidGroupRepository;

  private final BedZoneRepository bedZoneRepository;

  private final WorkOrchidGroupUsageApi workUsageInspector;

  private final List<OrchidGroupUsageInspector> usageInspectors;

  private final OrchidGroupAuditSupport auditSupport;

  private final OrchidGroupMutationEngine mutationEngine;

  private final Clock clock;

  public OrchidGroupResponse create(OrchidGroupCreateRequest request) {
    var businessDate = TimeConfig.farmToday(clock);
    var command =
        new CreateOrchidGroupMutationCommand(
            OrchidGroupMutationSources.farmRequest("ORCHID_GROUP_COMMAND", "DIRECT", "CREATE"),
            request.bedZoneId(),
            mutationDetails(request),
            businessDate,
            "난 묶음 등록");
    OrchidGroup created = createWithEngine(command);
    auditSupport.record(
        created.getId(),
        AuditAction.CREATED,
        AuditSource.ORCHID_GROUP_MANAGEMENT,
        null,
        auditSupport.snapshot(created),
        Map.of("creationMode", "SINGLE"));
    return OrchidGroupResponse.from(created, businessDate);
  }

  public OrchidGroupResponse update(Long orchidGroupId, OrchidGroupUpdateRequest request) {
    return update(lockForUpdate(List.of(orchidGroupId)).get(orchidGroupId), request, "SINGLE");
  }

  public List<OrchidGroupResponse> updateBatch(OrchidGroupBatchUpdateRequest request) {
    var groups =
        lockForUpdate(request.orchidGroups().stream().map(item -> item.orchidGroupId()).toList());
    return request.orchidGroups().stream()
        .map(item -> update(groups.get(item.orchidGroupId()), item.update(), "BATCH"))
        .toList();
  }

  private Map<Long, OrchidGroup> lockForUpdate(Collection<Long> orchidGroupIds) {
    var ids = orchidGroupIds.stream().distinct().sorted().toList();
    var groups = new HashMap<Long, OrchidGroup>();
    // Complete the group lock set before taking any zone lock or loading audit/response details.
    for (int start = 0; start < ids.size(); start += ID_BATCH_SIZE) {
      var batch = ids.subList(start, Math.min(start + ID_BATCH_SIZE, ids.size()));
      var locked = orchidGroupRepository.findAllForUpdateByIdIn(batch);
      if (locked.size() != batch.size()) {
        throw new NotFoundException("난 묶음을 찾을 수 없습니다.");
      }
      locked.forEach(group -> groups.put(group.getId(), group));
    }
    var zoneIds =
        groups.values().stream()
            .map(group -> group.getBedZone().getId())
            .distinct()
            .sorted()
            .toList();
    for (int start = 0; start < zoneIds.size(); start += ID_BATCH_SIZE) {
      var batch = zoneIds.subList(start, Math.min(start + ID_BATCH_SIZE, zoneIds.size()));
      if (bedZoneRepository.findAllForUpdateByIdIn(batch).size() != batch.size()) {
        throw new NotFoundException("구역을 찾을 수 없습니다.");
      }
    }
    for (int start = 0; start < ids.size(); start += ID_BATCH_SIZE) {
      orchidGroupRepository.findDetailsByIds(
          ids.subList(start, Math.min(start + ID_BATCH_SIZE, ids.size())));
    }
    return groups;
  }

  private OrchidGroupResponse update(
      OrchidGroup orchidGroup, OrchidGroupUpdateRequest request, String correctionMode) {
    Long orchidGroupId = orchidGroup.getId();
    var businessDate = TimeConfig.farmToday(clock);
    OrchidGroupAuditSnapshot before = auditSupport.snapshot(orchidGroup);
    OrchidGroupMutationDetails details = mutationDetails(request);
    if (!hasSameDetails(orchidGroup, details)) {
      mutationEngine.updateDetails(updateCommand(orchidGroupId, details));
    }
    OrchidGroupAuditSnapshot after = auditSupport.snapshot(orchidGroup);
    auditSupport.record(
        orchidGroupId,
        auditSupport.actionForCorrection(before, after),
        AuditSource.ORCHID_GROUP_CORRECTION,
        before,
        after,
        Map.of("correctionMode", correctionMode));
    return OrchidGroupResponse.from(orchidGroup, businessDate);
  }

  public void delete(Long orchidGroupId) {
    OrchidGroup orchidGroup =
        orchidGroupRepository.findAllForUpdateByIdIn(Set.of(orchidGroupId)).stream()
            .findFirst()
            .orElseThrow(() -> new NotFoundException("난 묶음을 찾을 수 없습니다."));
    OrchidGroupAuditSnapshot before = auditSupport.snapshot(orchidGroup);
    if (workUsageInspector.hasUncanceledReference(orchidGroupId)) {
      throw new ConflictException("취소되지 않은 작업과 연결된 난 묶음은 생성 취소할 수 없습니다. 연결 작업을 먼저 취소해주세요.");
    }
    usageInspectors.stream()
        .flatMap(inspector -> inspector.inspect(Set.of(orchidGroupId), null).stream())
        .findFirst()
        .ifPresent(
            usage -> {
              throw new ConflictException(usage.message());
            });
    var command =
        new CancelOrchidGroupCreationMutationCommand(
            OrchidGroupMutationSources.farmRequest(
                "ORCHID_GROUP_COMMAND", orchidGroupId.toString(), "CANCEL_CREATION"),
            orchidGroupId,
            TimeConfig.farmToday(clock),
            "난 묶음 삭제 요청에 따른 생성 취소");
    mutationEngine.cancelCreation(command);
    auditSupport.record(
        orchidGroupId,
        AuditAction.DEACTIVATED,
        AuditSource.ORCHID_GROUP_MANAGEMENT,
        before,
        auditSupport.snapshot(orchidGroup),
        Map.of("deleteMode", "CANCEL_CREATION"));
  }

  private OrchidGroup createWithEngine(CreateOrchidGroupMutationCommand command) {
    var result = mutationEngine.create(command);
    Long orchidGroupId = result.entries().getFirst().orchidGroupId();
    return orchidGroupRepository
        .findById(orchidGroupId)
        .orElseThrow(() -> new NotFoundException("생성된 난 묶음을 찾을 수 없습니다."));
  }

  private UpdateOrchidGroupMutationCommand updateCommand(
      Long orchidGroupId, OrchidGroupMutationDetails details) {
    return new UpdateOrchidGroupMutationCommand(
        OrchidGroupMutationSources.farmRequest(
            "ORCHID_GROUP_COMMAND", orchidGroupId.toString(), "UPDATE"),
        orchidGroupId,
        details,
        TimeConfig.farmToday(clock),
        "난 묶음 상세 수정");
  }

  private OrchidGroupMutationDetails mutationDetails(OrchidGroupCreateRequest request) {
    return new OrchidGroupMutationDetails(
        request.varietyId(),
        request.quantity(),
        request.potSize(),
        request.ageYear(),
        request.status(),
        request.placementType(),
        request.trayCount(),
        request.splitPlacementAllowed(),
        request.startPosition(),
        request.endPosition(),
        request.memo());
  }

  private OrchidGroupMutationDetails mutationDetails(OrchidGroupUpdateRequest request) {
    return new OrchidGroupMutationDetails(
        request.varietyId(),
        request.quantity(),
        request.potSize(),
        request.ageYear(),
        request.status(),
        request.placementType(),
        request.trayCount(),
        request.splitPlacementAllowed(),
        request.startPosition(),
        request.endPosition(),
        request.memo());
  }

  private boolean hasSameDetails(OrchidGroup group, OrchidGroupMutationDetails details) {
    return group.getVariety() != null
        && group.getVariety().getId().equals(details.varietyId())
        && Objects.equals(group.getQuantity(), details.quantity())
        && Objects.equals(group.getPotSize(), details.potSize())
        && Objects.equals(group.getAgeYear(), details.ageYear())
        && Objects.equals(group.getStatus(), details.status())
        && Objects.equals(group.getPlacementType(), details.placementType())
        && Objects.equals(group.getTrayCount(), details.trayCount())
        && Objects.equals(group.getSplitPlacementAllowed(), details.splitPlacementAllowed())
        && equalPosition(group.getStartPosition(), details.startPosition())
        && equalPosition(group.getEndPosition(), details.endPosition())
        && Objects.equals(group.getMemo(), details.memo());
  }

  private boolean equalPosition(BigDecimal currentValue, BigDecimal requestValue) {
    if (currentValue == null && requestValue == null) {
      return true;
    }
    if (currentValue == null || requestValue == null) {
      return false;
    }
    return currentValue.compareTo(requestValue) == 0;
  }
}
