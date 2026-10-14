package com.greenhouse.backend.farm.orchid.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.farm.orchid.domain.OrchidGroup;
import com.greenhouse.backend.farm.orchid.domain.OrchidGroupStatusPolicy;
import com.greenhouse.backend.farm.orchid.integration.FarmWorkTargetResolver;
import com.greenhouse.backend.farm.orchid.repository.OrchidGroupRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class FarmWorkTargetResolverLockTest {
  private final OrchidGroupRepository groups = mock(OrchidGroupRepository.class);
  private final FarmWorkTargetResolver resolver =
      new FarmWorkTargetResolver(null, null, null, groups, null, null, null, null);

  @Test
  void wholeSelectionIsSortedBeforeChunkingAndAllRootsAreLockedBeforeActiveChecks() {
    var input =
        new ArrayList<>(LongStream.iterate(1001, id -> id > 0, id -> id - 1).boxed().toList());
    input.add(1001L);
    stubLocks();
    when(groups.findActiveWorkTargetsByIds(
            anyCollection(), eq(OrchidGroupStatusPolicy.inactiveStatuses())))
        .thenAnswer(
            invocation ->
                Collections.nCopies(
                    ((Collection<?>) invocation.getArgument(0)).size(), mock(OrchidGroup.class)));
    resolver.lockAndValidateActive(input);
    var ids = LongStream.rangeClosed(1, 1001).boxed().toList();
    var order = inOrder(groups);
    for (int start = 0; start < ids.size(); start += 500) {
      order
          .verify(groups)
          .findAllForUpdateByIdIn(ids.subList(start, Math.min(start + 500, ids.size())));
    }
    for (int start = 0; start < ids.size(); start += 500) {
      order
          .verify(groups)
          .findActiveWorkTargetsByIds(
              ids.subList(start, Math.min(start + 500, ids.size())),
              OrchidGroupStatusPolicy.inactiveStatuses());
    }
    verifyNoMoreInteractions(groups);
  }

  @Test
  void missingRootStopsBeforeLaterChunksOrActiveChecks() {
    when(groups.findAllForUpdateByIdIn(anyCollection())).thenReturn(List.of());
    assertThatThrownBy(
            () -> resolver.lockAndValidateActive(LongStream.rangeClosed(1, 501).boxed().toList()))
        .isInstanceOf(ConflictException.class)
        .extracting("code")
        .isEqualTo("WORK_TARGET_CHANGED");
    verify(groups).findAllForUpdateByIdIn(LongStream.rangeClosed(1, 500).boxed().toList());
    verifyNoMoreInteractions(groups);
  }

  @Test
  void inactiveTargetAfterLockingRejectsTheWholeSelection() {
    stubLocks();
    when(groups.findActiveWorkTargetsByIds(
            anyCollection(), eq(OrchidGroupStatusPolicy.inactiveStatuses())))
        .thenReturn(List.of());
    var ids = LongStream.rangeClosed(1, 501).boxed().toList();
    assertThatThrownBy(() -> resolver.lockAndValidateActive(ids))
        .isInstanceOf(ConflictException.class)
        .extracting("code")
        .isEqualTo("WORK_TARGET_CHANGED");
    var order = inOrder(groups);
    order.verify(groups).findAllForUpdateByIdIn(ids.subList(0, 500));
    order.verify(groups).findAllForUpdateByIdIn(ids.subList(500, 501));
    order
        .verify(groups)
        .findActiveWorkTargetsByIds(
            ids.subList(0, 500), OrchidGroupStatusPolicy.inactiveStatuses());
    verifyNoMoreInteractions(groups);
  }

  @Test
  void anOptimisticVersionConflictKeepsTheExistingDomainErrorCode() {
    when(groups.findAllForUpdateByIdIn(List.of(1L)))
        .thenThrow(new ObjectOptimisticLockingFailureException(OrchidGroup.class, 1L));
    assertThatThrownBy(() -> resolver.lockAndValidateActive(List.of(1L)))
        .isInstanceOf(ConflictException.class)
        .extracting("code")
        .isEqualTo("WORK_TARGET_CHANGED");
    verify(groups).findAllForUpdateByIdIn(List.of(1L));
    verifyNoMoreInteractions(groups);
  }

  private void stubLocks() {
    when(groups.findAllForUpdateByIdIn(anyCollection()))
        .thenAnswer(
            invocation ->
                Collections.nCopies(
                    ((Collection<?>) invocation.getArgument(0)).size(), mock(OrchidGroup.class)));
  }
}
