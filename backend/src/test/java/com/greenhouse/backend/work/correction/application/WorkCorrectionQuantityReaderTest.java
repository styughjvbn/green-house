package com.greenhouse.backend.work.correction.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.greenhouse.backend.work.api.effect.WorkEffectKind;
import com.greenhouse.backend.work.api.operation.WorkTypeTemplate;
import com.greenhouse.backend.work.correction.repository.WorkOperationCorrectionRepository;
import com.greenhouse.backend.work.effect.domain.WorkAppliedEffect;
import com.greenhouse.backend.work.effect.repository.WorkAppliedEffectRepository;
import com.greenhouse.backend.work.operation.domain.WorkOperation;
import com.greenhouse.backend.work.operation.domain.WorkType;
import com.greenhouse.backend.work.operation.repository.WorkOperationRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class WorkCorrectionQuantityReaderTest {
  @Test
  void reconstructsPottingFromSavedPositionsAndRejectsPartialOrDuplicateIds() {
    var rows = List.of(Map.of("quantity", 2), Map.of("quantity", 3));
    var service =
        service(
            Map.of("results", rows),
            Map.of("actualQuantity", 5, "createdOrchidGroupIds", List.of(20, 21)));
    assertThat(service.context(1L))
        .singleElement()
        .satisfies(
            balance -> {
              assertThat(balance.sourceInputQuantities()).containsExactlyEntriesOf(Map.of(10L, 5));
              assertThat(balance.resultQuantities())
                  .containsExactlyInAnyOrderEntriesOf(Map.of(20L, 2, 21L, 3));
              assertThat(balance.inputEditable()).isFalse();
            });
    assertThat(
            service(
                    Map.of("results", List.of(Map.of(), Map.of("quantity", 3))),
                    Map.of("actualQuantity", 5, "createdOrchidGroupIds", List.of(20, 21)))
                .context(1L))
        .isEmpty();
    assertThat(
            service(
                    Map.of("results", rows),
                    Map.of("actualQuantity", 5, "createdOrchidGroupIds", List.of(20, 20)))
                .context(1L))
        .isEmpty();
  }

  @Test
  void savedResultFactsOverridePottingFallbackAndMalformedInputIsNotSilentlyDiscarded() {
    assertThat(service(null, Map.of("actualQuantity", 5)).context(1L)).isEmpty();
    var service =
        service(
            Map.of("results", List.of(Map.of("quantity", 99))),
            Map.of(
                "actualQuantity",
                5,
                "createdOrchidGroupIds",
                List.of(21),
                "results",
                List.of(Map.of("orchidGroupId", 20, "quantity", 5))));
    assertThat(service.context(1L))
        .singleElement()
        .satisfies(
            balance ->
                assertThat(balance.resultQuantities()).containsExactlyEntriesOf(Map.of(20L, 5)));
    assertThatThrownBy(
            () ->
                service(
                        Map.of(),
                        Map.of("actualQuantity", 5, "sourceInputQuantities", Map.of("bad", 5)))
                    .context(1L))
        .isInstanceOf(NumberFormatException.class);
  }

  private WorkCorrectionQuantityService service(
      Map<String, Object> command, Map<String, Object> result) {
    var operations = mock(WorkOperationRepository.class);
    var effects = mock(WorkAppliedEffectRepository.class);
    var corrections = mock(WorkOperationCorrectionRepository.class);
    var operation = mock(WorkOperation.class);
    var type = new WorkType("POTTING", "포트", WorkTypeTemplate.REPOT, true, true, true, 1);
    when(operation.getWorkType()).thenReturn(type);
    when(operations.findWithWorkTypeById(1L)).thenReturn(Optional.of(operation));
    var effect =
        new WorkAppliedEffect(
            operation,
            null,
            "EXECUTION:saved",
            WorkEffectKind.STRUCTURE_CHANGE,
            "POTTING",
            LocalDateTime.of(2026, 10, 4, 0, 0),
            "작업자",
            command,
            result);
    ReflectionTestUtils.setField(effect, "id", 10L);
    when(effects.findByWorkOperationIdOrderByIdAsc(1L)).thenReturn(List.of(effect));
    return new WorkCorrectionQuantityService(effects, corrections, operations);
  }
}
