package com.greenhouse.backend.work.application.effect;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.work.api.effect.StructureChangeCommand;
import com.greenhouse.backend.work.api.effect.StructureChangeResultInput;
import com.greenhouse.backend.work.api.effect.StructureChangeResultPurpose;
import com.greenhouse.backend.work.api.effect.StructureChangeSourceInput;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MovementQuantityAllocatorTest {

  @Test
  void poolsThreeSourcesBeforeSplittingTwoResults() {
    var request =
        new StructureChangeCommand(
            "pooled-movement",
            LocalDate.of(2026, 8, 16),
            null,
            null,
            List.of(
                new StructureChangeSourceInput(1L, 100, null, null),
                new StructureChangeSourceInput(2L, 100, null, null),
                new StructureChangeSourceInput(3L, 100, null, null)),
            List.of(result(10L, 250, "0", "25"), result(11L, 50, "25", "30")));

    assertThat(MovementQuantityAllocator.allocateMovedBySource(request))
        .containsExactly(Map.entry(1L, 100), Map.entry(2L, 100), Map.entry(3L, 100));
  }

  @Test
  void allocatesDiscardProportionallyByInputQuantity() {
    var request =
        new StructureChangeCommand(
            "proportional-discard",
            LocalDate.of(2026, 9, 29),
            null,
            null,
            List.of(
                new StructureChangeSourceInput(1L, 200, null, null),
                new StructureChangeSourceInput(2L, 300, null, null)),
            List.of(result(10L, 450, "0", "45")));

    assertThat(MovementQuantityAllocator.allocateDiscardBySource(request))
        .containsExactly(Map.entry(1L, 20), Map.entry(2L, 30));
    assertThat(MovementQuantityAllocator.allocateMovedBySource(request))
        .containsExactly(Map.entry(1L, 180), Map.entry(2L, 270));
  }

  @Test
  void assignsRoundingRemainderByLargestFractionThenSourceId() {
    var request =
        new StructureChangeCommand(
            "rounded-discard",
            LocalDate.of(2026, 9, 29),
            null,
            null,
            List.of(
                new StructureChangeSourceInput(2L, 1, null, null),
                new StructureChangeSourceInput(1L, 1, null, null),
                new StructureChangeSourceInput(3L, 1, null, null)),
            List.of(result(10L, 2, "0", "2")));

    assertThat(MovementQuantityAllocator.allocateDiscardBySource(request))
        .containsExactly(Map.entry(1L, 1), Map.entry(2L, 0), Map.entry(3L, 0));
  }

  private StructureChangeResultInput result(
      Long bedZoneId, int quantity, String startPosition, String endPosition) {
    return new StructureChangeResultInput(
        bedZoneId,
        quantity,
        1L,
        null,
        null,
        StructureChangeResultPurpose.NORMAL,
        null,
        null,
        false,
        new BigDecimal(startPosition),
        new BigDecimal(endPosition),
        null);
  }
}
