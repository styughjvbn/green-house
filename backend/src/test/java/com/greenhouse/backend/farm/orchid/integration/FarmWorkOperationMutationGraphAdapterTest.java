package com.greenhouse.backend.farm.orchid.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationEntryRole;
import com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationType;
import com.greenhouse.backend.farm.mutation.ledger.domain.OrchidGroupMutation;
import com.greenhouse.backend.farm.mutation.ledger.domain.OrchidGroupMutationEntry;
import com.greenhouse.backend.farm.mutation.ledger.domain.OrchidGroupMutationRelation;
import com.greenhouse.backend.farm.mutation.ledger.domain.OrchidGroupMutationRelationType;
import com.greenhouse.backend.farm.mutation.ledger.repository.OrchidGroupMutationEntryRepository;
import com.greenhouse.backend.farm.mutation.ledger.repository.OrchidGroupMutationRelationRepository;
import com.greenhouse.backend.farm.structure.repository.BedZoneRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;

@ExtendWith(MockitoExtension.class)
class FarmWorkOperationMutationGraphAdapterTest {

  @Mock OrchidGroupMutationEntryRepository entryRepository;

  @Mock OrchidGroupMutationRelationRepository relationRepository;

  @Mock BedZoneRepository bedZoneRepository;

  FarmWorkOperationMutationGraphAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter =
        new FarmWorkOperationMutationGraphAdapter(
            entryRepository, relationRepository, bedZoneRepository);
  }

  @Test
  void preservesCorrectionRelationBetweenVisibleMutations() {
    OrchidGroupMutation original = mutation(111L);
    OrchidGroupMutation correction = mutation(112L);
    OrchidGroupMutationEntry originalEntry = entry(1L, original, 401L);
    OrchidGroupMutationEntry correctionEntry = entry(2L, correction, 401L);
    when(entryRepository.findGraphEntriesByMutationIdIn(
            eq(List.of(111L, 112L)), any(Pageable.class)))
        .thenReturn(new SliceImpl<>(List.of(originalEntry, correctionEntry)));
    OrchidGroupMutationRelation relation = mock(OrchidGroupMutationRelation.class);
    when(relation.getId()).thenReturn(7L);
    when(relation.getMutation()).thenReturn(correction);
    when(relation.getRelatedMutation()).thenReturn(original);
    when(relation.getRelationType()).thenReturn(OrchidGroupMutationRelationType.CORRECTS);
    when(relationRepository.findVisibleGraphRelations(anyCollection(), any(Pageable.class)))
        .thenReturn(new SliceImpl<>(List.of(relation)));

    var fragment = adapter.load(List.of(111L, 112L), false, 1, 20);

    assertThat(fragment.mutations()).extracting(node -> node.id()).containsExactly(111L, 112L);
    assertThat(fragment.edges())
        .filteredOn(edge -> edge.type().equals("MUTATION_RELATION"))
        .singleElement()
        .satisfies(
            edge -> {
              assertThat(edge.sourceNodeId()).isEqualTo("mutation-112");
              assertThat(edge.targetNodeId()).isEqualTo("mutation-111");
              assertThat(edge.relationType()).isEqualTo("CORRECTS");
            });
  }

  @Test
  void reportsTruncationWhenDirectMutationEntriesExceedNodeQueryLimit() {
    OrchidGroupMutation mutation = mutation(120L);
    OrchidGroupMutationEntry entry = entry(3L, mutation, 501L);
    when(entryRepository.findGraphEntriesByMutationIdIn(eq(List.of(120L)), any(Pageable.class)))
        .thenReturn(new SliceImpl<>(List.of(entry), Pageable.ofSize(10), true));
    when(relationRepository.findVisibleGraphRelations(anyCollection(), any(Pageable.class)))
        .thenReturn(new SliceImpl<>(List.of()));

    var fragment = adapter.load(List.of(120L), false, 1, 10);

    assertThat(fragment.truncated()).isTrue();
  }

  private OrchidGroupMutation mutation(Long id) {
    OrchidGroupMutation mutation = mock(OrchidGroupMutation.class);
    when(mutation.getId()).thenReturn(id);
    when(mutation.getMutationType()).thenReturn(OrchidGroupMutationType.TRANSFORM);
    when(mutation.getEffectiveBusinessDate()).thenReturn(LocalDate.of(2026, 9, 1));
    when(mutation.getOccurredAt()).thenReturn(Instant.parse("2026-09-01T00:00:00Z"));
    return mutation;
  }

  private OrchidGroupMutationEntry entry(
      Long id, OrchidGroupMutation mutation, Long orchidGroupId) {
    OrchidGroupMutationEntry entry = mock(OrchidGroupMutationEntry.class);
    when(entry.getId()).thenReturn(id);
    when(entry.getMutation()).thenReturn(mutation);
    when(entry.getOrchidGroupId()).thenReturn(orchidGroupId);
    when(entry.getStateRevisionBefore()).thenReturn(null);
    when(entry.getStateRevisionAfter()).thenReturn(1L);
    when(entry.getAfterState()).thenReturn(null);
    when(entry.getRole()).thenReturn(OrchidGroupMutationEntryRole.RESULT);
    return entry;
  }
}
