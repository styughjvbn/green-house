package com.greenhouse.backend.farm.application.structure;

import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.structure.BedZone;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.orchid.OrchidPlacementRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrchidPlacementPolicy {

  private static final BigDecimal MIN_SPAN = BigDecimal.ONE.setScale(2);

  private final OrchidGroupRepository orchidGroupRepository;

  public PlacementRange resolveRange(
      BedZone bedZone, BigDecimal requestedStartPosition, BigDecimal requestedEndPosition) {
    if (requestedStartPosition == null && requestedEndPosition == null) {
      return findFirstAvailableSingleSlot(bedZone);
    }
    BigDecimal startPosition = normalizeNumber(requestedStartPosition);
    BigDecimal endPosition = normalizeNumber(requestedEndPosition);
    validatePlacement(bedZone, startPosition, endPosition, null);
    return new PlacementRange(startPosition, endPosition);
  }

  public BigDecimal normalizeNumber(BigDecimal value) {
    if (value == null) {
      return null;
    }
    return value.setScale(2, RoundingMode.HALF_UP);
  }

  public void validatePlacement(
      BedZone bedZone,
      BigDecimal startPosition,
      BigDecimal endPosition,
      Long excludeOrchidGroupId) {
    validatePlacementExcluding(
        bedZone,
        startPosition,
        endPosition,
        excludeOrchidGroupId == null ? Set.of() : Set.of(excludeOrchidGroupId));
  }

  public void validatePlacementExcluding(
      BedZone bedZone,
      BigDecimal startPosition,
      BigDecimal endPosition,
      Set<Long> excludeOrchidGroupIds) {
    validateRange(bedZone, startPosition, endPosition);
    validateNoOverlap(bedZone, startPosition, endPosition, excludeOrchidGroupIds);
  }

  public void validateRestoredPlacements(
      List<RestoredPlacement> placements, Set<Long> excludedIds) {
    if (placements.isEmpty()) {
      return;
    }
    var batch =
        prepareBatch(
            placements.stream().map(RestoredPlacement::bedZone).distinct().toList(),
            excludedIds,
            false);
    for (RestoredPlacement placement : placements) {
      validateRange(placement.bedZone(), placement.startPosition(), placement.endPosition());
      var zoneId = placement.bedZone().getId();
      if (batch
          .intervals
          .get(zoneId)
          .overlaps(placement.startPosition(), placement.endPosition())) {
        throw new IllegalArgumentException("복구할 난 묶음의 배치가 다른 난 묶음 배치와 겹칩니다.");
      }
      if (placement.sortOrder() != null
          && !batch.sortOrders.get(zoneId).add(placement.sortOrder())) {
        throw new IllegalArgumentException("복구할 난 묶음의 구역 내 표시 순서가 다른 난 묶음과 중복됩니다.");
      }
      batch.intervals.get(zoneId).add(placement.startPosition(), placement.endPosition());
    }
  }

  public BatchPlacements prepareBatch(Collection<BedZone> zones, Set<Long> excludedIds) {
    return prepareBatch(zones, excludedIds, true);
  }

  private BatchPlacements prepareBatch(
      Collection<BedZone> zones, Set<Long> excludedIds, boolean normalize) {
    var ids = zones.stream().map(BedZone::getId).distinct().toList();
    Map<Long, List<OrchidPlacementRow>> rows = new HashMap<>();
    for (int start = 0; start < ids.size(); start += 500) {
      for (var row :
          orchidGroupRepository.findActivePlacements(
              ids.subList(start, Math.min(start + 500, ids.size())))) {
        if (!excludedIds.contains(row.orchidGroupId())) {
          rows.computeIfAbsent(row.bedZoneId(), id -> new ArrayList<>()).add(row);
        }
      }
    }
    var batch = new BatchPlacements();
    for (var zone : zones) {
      var ranges = new ArrayList<PlacementRange>();
      var orders = new HashSet<Integer>();
      for (var row : rows.getOrDefault(zone.getId(), List.of())) {
        if (row.startPosition() != null && row.endPosition() != null) {
          ranges.add(
              new PlacementRange(
                  normalize ? normalizeNumber(row.startPosition()) : row.startPosition(),
                  normalize ? normalizeNumber(row.endPosition()) : row.endPosition()));
        }
        if (row.sortOrder() != null) orders.add(row.sortOrder());
      }
      batch.intervals.put(
          zone.getId(),
          new PlacementIntervalIndex(ranges, zone.getPhysicalBed().getPositionUnitCount()));
      batch.sortOrders.put(zone.getId(), orders);
    }
    return batch;
  }

  public void validateBatchMoves(List<RestoredPlacement> placements, Set<Long> excludedIds) {
    var batch =
        prepareBatch(
            placements.stream().map(RestoredPlacement::bedZone).distinct().toList(), excludedIds);
    for (var placement : placements) {
      batch.validate(placement.bedZone(), placement.startPosition(), placement.endPosition());
    }
    var results = new HashMap<Long, PlacementIntervalIndex>();
    for (var placement : placements) {
      var index =
          results.computeIfAbsent(
              placement.bedZone().getId(), id -> new PlacementIntervalIndex(List.of(), null));
      if (index.overlaps(placement.startPosition(), placement.endPosition())) {
        throw new IllegalArgumentException("이동 결과 난 묶음의 배치가 서로 겹칩니다.");
      }
      index.add(placement.startPosition(), placement.endPosition());
    }
  }

  /** Request-local state, created after zone locks; new results participate before DB flush. */
  public final class BatchPlacements {
    private final Map<Long, PlacementIntervalIndex> intervals = new HashMap<>();
    private final Map<Long, Set<Integer>> sortOrders = new HashMap<>();

    private BatchPlacements() {}

    public void validate(BedZone zone, BigDecimal start, BigDecimal end) {
      validateRange(zone, start, end);
      if (intervals.get(zone.getId()).overlaps(start, end)) {
        throw new IllegalArgumentException("선택한 위치가 기존 난 묶음 배치와 겹칩니다.");
      }
    }

    public void reserve(BedZone zone, BigDecimal start, BigDecimal end) {
      validate(zone, start, end);
      intervals.get(zone.getId()).add(normalizeNumber(start), normalizeNumber(end));
    }

    public PlacementRange reserveFirstSingleSlot(BedZone zone) {
      if (zone.getPhysicalBed().getPositionUnitCount() == null) {
        throw new IllegalArgumentException("배드 칸 수 정보가 없어 자동 배치할 수 없습니다.");
      }
      var start = intervals.get(zone.getId()).firstSingleSlot();
      if (start == null) throw new IllegalArgumentException("선택한 구역에 1칸 이상 비어 있는 공간이 없습니다.");
      var range = new PlacementRange(start, start.add(MIN_SPAN));
      reserve(zone, range.startPosition(), range.endPosition());
      return range;
    }
  }

  private void validateRange(BedZone bedZone, BigDecimal startPosition, BigDecimal endPosition) {
    if (startPosition == null || endPosition == null) {
      throw new IllegalArgumentException("시작 위치와 종료 위치를 모두 입력해야 합니다.");
    }
    if (endPosition.compareTo(startPosition) <= 0) {
      throw new IllegalArgumentException("종료 위치는 시작 위치보다 커야 합니다.");
    }
    if (endPosition.subtract(startPosition).compareTo(MIN_SPAN) < 0) {
      throw new IllegalArgumentException("난 묶음은 최소 1칸 이상을 차지해야 합니다.");
    }
    BigDecimal maxPosition = bedZone.getPhysicalBed().getPositionUnitCount();
    if (maxPosition != null && endPosition.compareTo(maxPosition) > 0) {
      throw new IllegalArgumentException("종료 위치는 배드 최대 칸 수를 넘을 수 없습니다.");
    }
  }

  public PlacementRange findFirstAvailableSingleSlot(BedZone bedZone) {
    BigDecimal maxPosition = bedZone.getPhysicalBed().getPositionUnitCount();
    if (maxPosition == null) {
      throw new IllegalArgumentException("배드 칸 수 정보가 없어 자동 배치할 수 없습니다.");
    }

    BigDecimal cursor = BigDecimal.ZERO.setScale(2);
    List<OrchidGroup> positionedGroups =
        orchidGroupRepository
            .findByBedZoneIdAndQuantityGreaterThanOrderBySortOrderAsc(bedZone.getId(), 0)
            .stream()
            .filter(group -> group.getStartPosition() != null && group.getEndPosition() != null)
            .sorted(
                Comparator.comparing(OrchidGroup::getStartPosition)
                    .thenComparing(OrchidGroup::getSortOrder))
            .toList();

    for (OrchidGroup group : positionedGroups) {
      BigDecimal start = normalizeNumber(group.getStartPosition());
      BigDecimal end = normalizeNumber(group.getEndPosition());
      if (start.subtract(cursor).compareTo(MIN_SPAN) >= 0) {
        return new PlacementRange(cursor, cursor.add(MIN_SPAN));
      }
      if (end.compareTo(cursor) > 0) {
        cursor = end;
      }
    }

    if (maxPosition.subtract(cursor).compareTo(MIN_SPAN) >= 0) {
      return new PlacementRange(cursor, cursor.add(MIN_SPAN));
    }

    throw new IllegalArgumentException("선택한 구역에 1칸 이상 비어 있는 공간이 없습니다.");
  }

  private void validateNoOverlap(
      BedZone bedZone,
      BigDecimal startPosition,
      BigDecimal endPosition,
      Set<Long> excludeOrchidGroupIds) {
    for (OrchidGroup group :
        orchidGroupRepository.findByBedZoneIdAndQuantityGreaterThanOrderBySortOrderAsc(
            bedZone.getId(), 0)) {
      if (excludeOrchidGroupIds.contains(group.getId())) {
        continue;
      }
      if (group.getStartPosition() == null || group.getEndPosition() == null) {
        continue;
      }
      if (isOverlapping(
          startPosition,
          endPosition,
          normalizeNumber(group.getStartPosition()),
          normalizeNumber(group.getEndPosition()))) {
        throw new IllegalArgumentException("선택한 위치가 기존 난 묶음 배치와 겹칩니다.");
      }
    }
  }

  private boolean isOverlapping(
      BigDecimal candidateStart,
      BigDecimal candidateEnd,
      BigDecimal existingStart,
      BigDecimal existingEnd) {
    return candidateStart.compareTo(existingEnd) < 0 && candidateEnd.compareTo(existingStart) > 0;
  }

  public record PlacementRange(BigDecimal startPosition, BigDecimal endPosition) {}

  public record RestoredPlacement(
      BedZone bedZone, BigDecimal startPosition, BigDecimal endPosition, Integer sortOrder) {}
}
