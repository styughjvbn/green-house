package com.greenhouse.backend.farm.orchid.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationEngine;
import com.greenhouse.backend.farm.orchid.domain.OrchidGroup;
import com.greenhouse.backend.farm.orchid.repository.OrchidGroupRepository;
import com.greenhouse.backend.farm.orchid.web.dto.OrchidGroupBatchUpdateItem;
import com.greenhouse.backend.farm.orchid.web.dto.OrchidGroupBatchUpdateRequest;
import com.greenhouse.backend.farm.orchid.web.dto.OrchidGroupUpdateRequest;
import com.greenhouse.backend.farm.structure.domain.BedZone;
import com.greenhouse.backend.farm.structure.domain.BedZoneSide;
import com.greenhouse.backend.farm.structure.repository.BedZoneRepository;
import com.greenhouse.backend.work.api.target.WorkOrchidGroupUsageApi;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class OrchidGroupCommandLockTest {
  @Test
  void largeBatchLocksAllGroupsThenGloballyOrderedZonesBeforeAnyMutation() {
    var groups = mock(OrchidGroupRepository.class);
    var zones = mock(BedZoneRepository.class);
    var engine = mock(OrchidGroupMutationEngine.class);
    var audit = mock(OrchidGroupAuditSupport.class);
    var service =
        new OrchidGroupCommandService(
            groups,
            zones,
            mock(WorkOrchidGroupUsageApi.class),
            List.of(),
            audit,
            engine,
            Clock.systemUTC());
    var fixtures = new HashMap<Long, OrchidGroup>();
    var zoneFixtures = new HashMap<Long, BedZone>();
    var requests = new ArrayList<OrchidGroupBatchUpdateItem>();
    var detail =
        new OrchidGroupUpdateRequest(
            1L, 10, "3치", 1, "정상", "POT", null, false, BigDecimal.ZERO, BigDecimal.ONE, null);
    for (long id = 1001; id > 0; id--) {
      var zone = new BedZone("구역", BedZoneSide.LEFT, 1);
      long zoneId = 2002 - id;
      ReflectionTestUtils.setField(zone, "id", zoneId);
      var group =
          new OrchidGroup(zone, "난", "난", 10, "3치", 1, "정상", 1, BigDecimal.ZERO, BigDecimal.ONE);
      ReflectionTestUtils.setField(group, "id", id);
      fixtures.put(id, group);
      zoneFixtures.put(zoneId, zone);
      requests.add(new OrchidGroupBatchUpdateItem(id, detail));
    }
    requests.add(new OrchidGroupBatchUpdateItem(1001L, detail));
    when(groups.findAllForUpdateByIdIn(anyCollection()))
        .thenAnswer(invocation -> lookup(fixtures, invocation.getArgument(0)));
    // Fail the last zone, after both ordered sets have crossed the 500-ID boundary.
    when(zones.findAllForUpdateByIdIn(anyCollection()))
        .thenAnswer(
            invocation -> {
              Collection<Long> ids = invocation.getArgument(0);
              return ids.contains(2001L) ? List.of() : lookup(zoneFixtures, ids);
            });
    assertThatThrownBy(() -> service.updateBatch(new OrchidGroupBatchUpdateRequest(requests)))
        .isInstanceOf(NotFoundException.class);
    var order = inOrder(groups, zones);
    for (long[] range :
        List.of(new long[] {1, 500}, new long[] {501, 1000}, new long[] {1001, 1001}))
      order.verify(groups).findAllForUpdateByIdIn(ids(range));
    for (long[] range :
        List.of(new long[] {1001, 1500}, new long[] {1501, 2000}, new long[] {2001, 2001}))
      order.verify(zones).findAllForUpdateByIdIn(ids(range));
    verifyNoMoreInteractions(groups, zones);
    verifyNoInteractions(engine, audit);
  }

  private List<Long> ids(long[] range) {
    return LongStream.rangeClosed(range[0], range[1]).boxed().toList();
  }

  private <T> List<T> lookup(Map<Long, T> fixtures, Collection<Long> ids) {
    return ids.stream().map(fixtures::get).toList();
  }
}
