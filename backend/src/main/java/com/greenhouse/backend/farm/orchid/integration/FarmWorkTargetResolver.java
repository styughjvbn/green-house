package com.greenhouse.backend.farm.orchid.integration;

import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.application.orchid.DerivedOrchidGroupService;
import com.greenhouse.backend.farm.domain.collection.OrchidGroupCollectionStatus;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroupStatusPolicy;
import com.greenhouse.backend.farm.repository.collection.OrchidGroupCollectionMemberRepository;
import com.greenhouse.backend.farm.repository.collection.OrchidGroupCollectionRepository;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.farm.structure.repository.BedZoneRepository;
import com.greenhouse.backend.farm.structure.repository.HouseRepository;
import com.greenhouse.backend.farm.structure.repository.PhysicalBedRepository;
import com.greenhouse.backend.work.api.target.WorkTargetSelection;
import com.greenhouse.backend.work.spi.target.ResolvedWorkTarget;
import com.greenhouse.backend.work.spi.target.WorkTargetResolver;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class FarmWorkTargetResolver implements WorkTargetResolver {
  private static final int ID_BATCH_SIZE = 500;

  private final HouseRepository houseRepository;

  private final PhysicalBedRepository physicalBedRepository;

  private final BedZoneRepository bedZoneRepository;

  private final OrchidGroupRepository orchidGroupRepository;

  private final OrchidGroupCollectionRepository collectionRepository;

  private final OrchidGroupCollectionMemberRepository collectionMemberRepository;

  private final DerivedOrchidGroupService derivedOrchidGroupService;

  private final Clock clock;

  @Override
  public List<ResolvedWorkTarget> resolve(WorkTargetSelection selection) {
    return switch (selection.sourceScopeType()) {
      case FARM -> resolveLocation(null, null);
      case HOUSE -> resolveHouse(selection.sourceScopeId());
      case PHYSICAL_BED -> resolvePhysicalBed(selection.sourceScopeId());
      case BED_ZONE -> resolveBedZone(selection.sourceScopeId());
      case ORCHID_GROUP -> resolveManual(selection.sourceOrchidGroupIds());
      case DERIVED_GROUP ->
          resolveActiveIds(
              derivedOrchidGroupService
                  .getMembers(selection.sourceDerivedGroupKey(), null, null, null)
                  .stream()
                  .map(member -> member.id())
                  .collect(Collectors.toSet()));
      case USER_COLLECTION -> resolveCollection(selection.sourceScopeId());
      case MANUAL_SELECTION -> resolveManual(selection.sourceOrchidGroupIds());
      default -> throw new IllegalArgumentException("아직 지원하지 않는 작업 대상 유형입니다.");
    };
  }

  @Override
  public ResolvedWorkTarget getCurrent(Long orchidGroupId) {
    return orchidGroupRepository
        .findDetailById(orchidGroupId)
        .map(this::toResolvedTarget)
        .orElseThrow(() -> new NotFoundException("난 묶음을 찾을 수 없습니다."));
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void lockAndValidateActive(List<Long> orchidGroupIds) {
    var ids = orchidGroupIds.stream().distinct().sorted().toList();
    try {
      for (int start = 0; start < ids.size(); start += ID_BATCH_SIZE) {
        var batch = ids.subList(start, Math.min(start + ID_BATCH_SIZE, ids.size()));
        if (orchidGroupRepository.findAllForUpdateByIdIn(batch).size() != batch.size()) {
          throw targetChanged();
        }
      }
      for (int start = 0; start < ids.size(); start += ID_BATCH_SIZE) {
        var batch = ids.subList(start, Math.min(start + ID_BATCH_SIZE, ids.size()));
        if (orchidGroupRepository
                .findActiveWorkTargetsByIds(batch, OrchidGroupStatusPolicy.inactiveStatuses())
                .size()
            != batch.size()) {
          throw targetChanged();
        }
      }
    } catch (ObjectOptimisticLockingFailureException exception) {
      throw targetChanged();
    }
  }

  private ConflictException targetChanged() {
    return new ConflictException(
        "WORK_TARGET_CHANGED", "작업 대상이 변경되었습니다. 현재 작업 가능한 난 묶음을 다시 선택해주세요.");
  }

  private ResolvedWorkTarget toResolvedTarget(OrchidGroup group) {
    return new ResolvedWorkTarget(
        group.getId(),
        group.getVariety() == null ? null : group.getVariety().getId(),
        group.getVarietyName(),
        group.getQuantity(),
        currentAgeYear(group),
        group.getPotSize(),
        group.getPotSizeCode().name(),
        location(group));
  }

  private List<ResolvedWorkTarget> resolveHouse(Long houseId) {
    if (houseId == null || !houseRepository.existsById(houseId)) {
      throw new NotFoundException("동을 찾을 수 없습니다.");
    }
    return orchidGroupRepository
        .findActiveWorkTargetsByHouseId(houseId, OrchidGroupStatusPolicy.inactiveStatuses())
        .stream()
        .map(this::toResolvedTarget)
        .toList();
  }

  private List<ResolvedWorkTarget> resolvePhysicalBed(Long physicalBedId) {
    if (physicalBedId == null || !physicalBedRepository.existsById(physicalBedId)) {
      throw new NotFoundException("다이를 찾을 수 없습니다.");
    }
    return resolveLocation(physicalBedId, null);
  }

  private List<ResolvedWorkTarget> resolveBedZone(Long bedZoneId) {
    if (bedZoneId == null || !bedZoneRepository.existsById(bedZoneId)) {
      throw new NotFoundException("논리 구역을 찾을 수 없습니다.");
    }
    return resolveLocation(null, bedZoneId);
  }

  private List<ResolvedWorkTarget> resolveLocation(Long physicalBedId, Long bedZoneId) {
    return orchidGroupRepository
        .findActiveWorkTargets(physicalBedId, bedZoneId, OrchidGroupStatusPolicy.inactiveStatuses())
        .stream()
        .map(this::toResolvedTarget)
        .toList();
  }

  private List<ResolvedWorkTarget> resolveCollection(Long collectionId) {
    var collection =
        collectionId == null ? null : collectionRepository.findById(collectionId).orElse(null);
    if (collection == null) {
      throw new NotFoundException("사용자 그룹을 찾을 수 없습니다.");
    }
    if (collection.getStatus() != OrchidGroupCollectionStatus.ACTIVE) {
      throw new IllegalArgumentException("보관된 사용자 그룹으로 새 작업을 만들 수 없습니다.");
    }
    Set<Long> ids =
        collectionMemberRepository
            .findByCollectionIdAndRemovedAtIsNullOrderByJoinedAtAsc(collectionId)
            .stream()
            .map(member -> member.getOrchidGroupId())
            .collect(Collectors.toSet());
    return resolveActiveIds(ids);
  }

  private List<ResolvedWorkTarget> resolveManual(List<Long> orchidGroupIds) {
    Set<Long> ids = Set.copyOf(orchidGroupIds);
    List<ResolvedWorkTarget> resolved = resolveActiveIds(ids);
    Set<Long> resolvedIds =
        resolved.stream().map(ResolvedWorkTarget::orchidGroupId).collect(Collectors.toSet());
    if (!resolvedIds.containsAll(ids)) {
      throw new IllegalArgumentException("직접 선택 대상에는 현재 작업 가능한 난 묶음만 포함할 수 있습니다.");
    }
    return resolved;
  }

  private List<ResolvedWorkTarget> resolveActiveIds(Set<Long> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    return orchidGroupRepository
        .findActiveWorkTargetsByIds(ids, OrchidGroupStatusPolicy.inactiveStatuses())
        .stream()
        .map(this::toResolvedTarget)
        .toList();
  }

  private Integer currentAgeYear(OrchidGroup group) {
    if (group.getAgeYear() == null) {
      return null;
    }
    LocalDate referenceDate =
        group.getInboundRecord() != null
            ? group.getInboundRecord().getInboundDate()
            : group.getCreatedAt() == null
                ? null
                : TimeConfig.toFarmTime(group.getCreatedAt()).toLocalDate();
    if (referenceDate == null) {
      return group.getAgeYear();
    }
    long elapsedYears = ChronoUnit.YEARS.between(referenceDate, TimeConfig.farmToday(clock));
    return group.getAgeYear() + Math.max(0, Math.toIntExact(elapsedYears));
  }

  private Map<String, Object> location(OrchidGroup group) {
    var zone = group.getBedZone();
    var bed = zone.getPhysicalBed();
    var house = bed.getHouse();
    Map<String, Object> location = new LinkedHashMap<>();
    location.put("houseId", house.getId());
    location.put("houseNumber", house.getNumber());
    location.put("physicalBedId", bed.getId());
    location.put("physicalBedNumber", bed.getNumber());
    location.put("bedZoneId", zone.getId());
    location.put("bedZoneName", zone.getName());
    return location;
  }
}
