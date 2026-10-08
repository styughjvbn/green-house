package com.greenhouse.backend.farm.transformation.integration;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.structure.BedZoneRepository;
import com.greenhouse.backend.work.spi.operation.StructureChangeRecordLockPort;
import java.util.Collection;
import java.util.HashSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class FarmStructureChangeRecordLockAdapter implements StructureChangeRecordLockPort {
  private static final int ID_BATCH_SIZE = 500;
  private final OrchidGroupRepository groups;
  private final BedZoneRepository zones;

  @Override
  public void lock(Collection<Long> sourceOrchidGroupIds, Collection<Long> resultBedZoneIds) {
    var ids = sourceOrchidGroupIds.stream().distinct().sorted().toList();
    var zoneIds = new HashSet<>(resultBedZoneIds);
    for (int start = 0; start < ids.size(); start += ID_BATCH_SIZE) {
      var batch = ids.subList(start, Math.min(start + ID_BATCH_SIZE, ids.size()));
      var locked = groups.findAllForUpdateByIdIn(batch);
      if (locked.size() != batch.size()) {
        throw new IllegalArgumentException("현재 작업할 원본 난 묶음을 모두 찾을 수 없습니다.");
      }
      locked.forEach(group -> zoneIds.add(group.getBedZone().getId()));
    }
    var orderedZoneIds = zoneIds.stream().sorted().toList();
    for (int start = 0; start < orderedZoneIds.size(); start += ID_BATCH_SIZE) {
      var batch =
          orderedZoneIds.subList(start, Math.min(start + ID_BATCH_SIZE, orderedZoneIds.size()));
      if (zones.findAllForUpdateByIdIn(batch).size() != batch.size()) {
        throw new NotFoundException("작업 구역을 모두 찾을 수 없습니다.");
      }
    }
  }
}
