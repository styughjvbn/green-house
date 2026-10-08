package com.greenhouse.backend.work.application.effect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.greenhouse.backend.work.api.effect.WorkEffectKind;
import com.greenhouse.backend.work.domain.effect.WorkAppliedEffect;
import com.greenhouse.backend.work.domain.effect.WorkEffectOrchidGroup;
import com.greenhouse.backend.work.domain.effect.WorkEffectOrchidGroupRelationType;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.repository.WorkEffectOrchidGroupRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class StructureChangeLineageQueryServiceTest {
  private final WorkEffectOrchidGroupRepository repository =
      mock(WorkEffectOrchidGroupRepository.class);
  private final WorkOperation operation = mock(WorkOperation.class);
  private final StructureChangeLineageQueryService service =
      new StructureChangeLineageQueryService(repository);

  @ParameterizedTest
  @ValueSource(ints = {1, 20})
  void storedAliasesAndSourceRowsAreClassifiedWithTwoBulkReads(int count) {
    when(operation.getId()).thenReturn(91L);
    var links = new ArrayList<WorkEffectOrchidGroup>();
    var ids = new ArrayList<Long>();
    for (long id = 1; id <= count; id++) {
      var effect = effect(id, id % 2 == 0 ? "MOVEMENT" : "MOVE", "EXECUTION:" + id, false);
      links.add(new WorkEffectOrchidGroup(effect, 11L, WorkEffectOrchidGroupRelationType.SOURCE));
      ids.add(id);
    }
    when(repository.findByOrchidGroupIdOrderByWorkAppliedEffectAppliedAtDescWorkAppliedEffectIdDesc(
            11L))
        .thenReturn(links);
    when(repository.findByWorkAppliedEffectIdInOrderByWorkAppliedEffectIdAscIdAsc(any()))
        .thenReturn(links);
    var views = service.findByOrchidGroupId(11L);
    assertThat(views)
        .hasSize(count)
        .allSatisfy(view -> assertThat(view.structureTypeCode()).isEqualTo("MOVEMENT"));
    verify(repository)
        .findByOrchidGroupIdOrderByWorkAppliedEffectAppliedAtDescWorkAppliedEffectIdDesc(11L);
    verify(repository)
        .findByWorkAppliedEffectIdInOrderByWorkAppliedEffectIdAscIdAsc(new LinkedHashSet<>(ids));
    verifyNoMoreInteractions(repository);
    verify(operation, never()).getWorkType();
  }

  @Test
  void preservesTargetAndOperationFallbackWithoutClassifyingUnrelatedEffects() {
    when(operation.getId()).thenReturn(91L);
    var included = effect(1L, "REPOT", "OPERATION", true);
    var ignored =
        List.of(
            effect(2L, "MOVE", "TARGET:12", false),
            effect(3L, "REPOT", "TARGET:12", false),
            effect(4L, "POTTING", "EXECUTION:12", true),
            effect(5L, "UNKNOWN", "EXECUTION:12", true));
    var links = new ArrayList<WorkEffectOrchidGroup>();
    links.add(new WorkEffectOrchidGroup(included, 11L, WorkEffectOrchidGroupRelationType.SOURCE));
    ignored.forEach(
        effect ->
            links.add(
                new WorkEffectOrchidGroup(effect, 11L, WorkEffectOrchidGroupRelationType.SOURCE)));
    when(repository.findByOrchidGroupIdOrderByWorkAppliedEffectAppliedAtDescWorkAppliedEffectIdDesc(
            11L))
        .thenReturn(links);
    when(repository.findByWorkAppliedEffectIdInOrderByWorkAppliedEffectIdAscIdAsc(any()))
        .thenReturn(List.of(links.getFirst()));
    assertThat(service.findByOrchidGroupId(11L))
        .singleElement()
        .satisfies(
            view -> {
              assertThat(view.structureTypeCode()).isEqualTo("REPOT");
              assertThat(view.sources())
                  .singleElement()
                  .satisfies(source -> assertThat(source.quantity()).isEqualTo(5));
            });
    verify(operation, never()).getWorkType();
  }

  @Test
  void decodedTypePreservesStoredHandlerAndStaysOutOfJson() {
    when(operation.getId()).thenReturn(91L);
    var effect = effect(1L, "MOVE", "EXECUTION:1", false);
    var links =
        List.of(new WorkEffectOrchidGroup(effect, 11L, WorkEffectOrchidGroupRelationType.SOURCE));
    when(repository.findByOrchidGroupIdOrderByWorkAppliedEffectAppliedAtDescWorkAppliedEffectIdDesc(
            11L))
        .thenReturn(links);
    when(repository.findByWorkAppliedEffectIdInOrderByWorkAppliedEffectIdAscIdAsc(any()))
        .thenReturn(links);

    var view = service.findByOrchidGroupId(11L).getFirst();
    assertThat(view.structureTypeCode()).isEqualTo("MOVEMENT");
    var json = JsonMapper.builder().findAndAddModules().build().valueToTree(view);
    assertThat(json.get("handlerCode").asText()).isEqualTo("MOVE");
    assertThat(json.has("structureTypeCode")).isFalse();
    assertThat(json.has("structureType")).isFalse();
  }

  private WorkAppliedEffect effect(Long id, String handler, String key, boolean sourceRows) {
    var effect =
        new WorkAppliedEffect(
            operation,
            null,
            key,
            WorkEffectKind.ATTRIBUTE_CHANGE,
            handler,
            LocalDateTime.of(2026, 9, 8, 0, 0),
            "worker",
            sourceRows
                ? Map.of("sources", List.of(Map.of("sourceOrchidGroupId", 11L, "inputQuantity", 5)))
                : Map.of(),
            Map.of("results", List.of(Map.of("orchidGroupId", 13L, "quantity", 5))));
    ReflectionTestUtils.setField(effect, "id", id);
    return effect;
  }
}
