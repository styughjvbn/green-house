package com.greenhouse.backend.farm.orchid.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.orchid.repository.OrchidGroupRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class OrchidGroupReaderLockTest {
  private final OrchidGroupRepository repository = mock(OrchidGroupRepository.class);
  private final OrchidGroupReader reader = new OrchidGroupReader(repository);

  @Test
  void allIdsAreDeduplicatedAndOrderedBeforeSplittingLargeLockRequests() {
    var input =
        new ArrayList<>(
            LongStream.rangeClosed(1, 1001).boxed().sorted(Comparator.reverseOrder()).toList());
    input.addAll(List.of(1001L, 1L));
    when(repository.findAllForUpdateByIdIn(anyCollection()))
        .thenAnswer(
            invocation ->
                Collections.nCopies(((Collection<?>) invocation.getArgument(0)).size(), null));
    reader.lockGroups(input);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Collection<Long>> batches = ArgumentCaptor.forClass(Collection.class);
    verify(repository, Mockito.times(3)).findAllForUpdateByIdIn(batches.capture());
    assertThat(batches.getAllValues()).extracting(Collection::size).containsExactly(500, 500, 1);
    assertThat(batches.getAllValues().stream().flatMap(Collection::stream).toList())
        .containsExactlyElementsOf(LongStream.rangeClosed(1, 1001).boxed().toList());
    // Locking must not trigger association or DTO loading for every ID/batch.
    verifyNoMoreInteractions(repository);
  }

  @Test
  void emptyLockSetDoesNotQueryTheDatabase() {
    reader.lockGroups(List.of());
    verifyNoInteractions(repository);
  }

  @Test
  void aMissingIdFailsBeforeTakingLaterBatchLocks() {
    when(repository.findAllForUpdateByIdIn(anyCollection())).thenReturn(List.of());
    assertThatThrownBy(() -> reader.lockGroups(LongStream.rangeClosed(1, 501).boxed().toList()))
        .isInstanceOf(NotFoundException.class);
    verify(repository).findAllForUpdateByIdIn(LongStream.rangeClosed(1, 500).boxed().toList());
    verifyNoMoreInteractions(repository);
  }
}
