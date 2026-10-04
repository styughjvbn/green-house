package com.greenhouse.backend.farm.application.transformation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.structure.BedZone;
import com.greenhouse.backend.farm.domain.structure.BedZoneSide;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.structure.BedZoneRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class FarmStructureChangeRecordLockAdapterTest {
  private final OrchidGroupRepository groups = mock(OrchidGroupRepository.class);
  private final BedZoneRepository zones = mock(BedZoneRepository.class);
  private final FarmStructureChangeRecordLockAdapter adapter =
      new FarmStructureChangeRecordLockAdapter(groups, zones);

  @Test
  void sourceAndCurrentResultZoneUnionsRemainGloballyOrderedAcrossChunks() {
    var fixtures = new HashMap<Long, OrchidGroup>();
    var zoneFixtures = new HashMap<Long, BedZone>();
    var input = new ArrayList<Long>();
    for (long id = 1001; id > 0; id--) {
      var zone = new BedZone("구역", BedZoneSide.LEFT, 1);
      ReflectionTestUtils.setField(zone, "id", 2002 - id);
      var group =
          new OrchidGroup(zone, "난", "난", 10, "3치", 1, "정상", 1, BigDecimal.ZERO, BigDecimal.ONE);
      ReflectionTestUtils.setField(group, "id", id);
      fixtures.put(id, group);
      zoneFixtures.put(zone.getId(), zone);
      input.add(id);
    }
    input.add(1001L);
    var extraZone = new BedZone("추가 구역", BedZoneSide.LEFT, 1);
    ReflectionTestUtils.setField(extraZone, "id", 1L);
    zoneFixtures.put(1L, extraZone);
    when(groups.findAllForUpdateByIdIn(anyCollection()))
        .thenAnswer(
            invocation -> {
              Collection<Long> ids = invocation.getArgument(0);
              return ids.stream().map(fixtures::get).toList();
            });
    when(zones.findAllForUpdateByIdIn(anyCollection()))
        .thenAnswer(
            invocation -> {
              Collection<Long> ids = invocation.getArgument(0);
              return ids.contains(3001L) ? List.of() : ids.stream().map(zoneFixtures::get).toList();
            });
    assertThatThrownBy(() -> adapter.lock(input, List.of(3001L, 1001L, 1L, 3001L)))
        .isInstanceOf(NotFoundException.class);
    var orderedGroups = LongStream.rangeClosed(1, 1001).boxed().toList();
    var orderedZones =
        LongStream.concat(
                LongStream.of(1),
                LongStream.concat(LongStream.rangeClosed(1001, 2001), LongStream.of(3001)))
            .boxed()
            .toList();
    var order = inOrder(groups, zones);
    for (int start = 0; start < orderedGroups.size(); start += 500)
      order
          .verify(groups)
          .findAllForUpdateByIdIn(
              orderedGroups.subList(start, Math.min(start + 500, orderedGroups.size())));
    for (int start = 0; start < orderedZones.size(); start += 500)
      order
          .verify(zones)
          .findAllForUpdateByIdIn(
              orderedZones.subList(start, Math.min(start + 500, orderedZones.size())));
    verifyNoMoreInteractions(groups, zones);
  }

  @Test
  void aMissingSourceStopsBeforeLockingAnyZoneOrLaterSourceBatch() {
    when(groups.findAllForUpdateByIdIn(anyCollection())).thenReturn(List.of());
    assertThatThrownBy(
            () -> adapter.lock(LongStream.rangeClosed(1, 501).boxed().toList(), List.of(1L)))
        .isInstanceOf(IllegalArgumentException.class);
    verify(groups).findAllForUpdateByIdIn(LongStream.rangeClosed(1, 500).boxed().toList());
    verifyNoMoreInteractions(groups);
    verifyNoInteractions(zones);
  }
}
