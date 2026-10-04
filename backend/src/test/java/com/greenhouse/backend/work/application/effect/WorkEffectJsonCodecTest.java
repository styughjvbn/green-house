package com.greenhouse.backend.work.application.effect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.greenhouse.backend.work.application.target.WorkOperationTargetView;
import com.greenhouse.backend.work.domain.target.WorkOperationTarget;
import com.greenhouse.backend.work.domain.target.WorkTargetExecution;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class WorkEffectJsonCodecTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  static Stream<JsonNode> readerFixtures() throws Exception {
    var fixtures =
        MAPPER.readTree(
            WorkEffectJsonCodecTest.class.getResourceAsStream(
                "/work/compatibility/effect-reader-contract.json"));
    return Stream.iterate(0, i -> i < fixtures.size(), i -> i + 1).map(fixtures::get);
  }

  @ParameterizedTest
  @MethodSource("readerFixtures")
  void preservesConsumerSpecificStoredFormats(JsonNode fixture) {
    Map<String, Object> details =
        MAPPER.convertValue(fixture.get("result"), new TypeReference<>() {});
    List<Long> targetIds = MAPPER.convertValue(fixture.get("targetIds"), new TypeReference<>() {});
    List<Long> detailIds = MAPPER.convertValue(fixture.get("detailIds"), new TypeReference<>() {});
    Map<Long, Integer> quantities =
        MAPPER.convertValue(fixture.get("quantities"), new TypeReference<>() {});
    assertThat(WorkEffectJsonCodec.targetResultIds(details)).containsExactlyElementsOf(targetIds);
    assertThat(WorkEffectJsonCodec.detailResultIds(details)).containsExactlyElementsOf(detailIds);
    assertThat(WorkEffectJsonCodec.resultQuantities(details)).containsExactlyEntriesOf(quantities);
    var execution = mock(WorkTargetExecution.class);
    when(execution.getResultDetails()).thenReturn(details);
    var target = WorkOperationTargetView.from(mock(WorkOperationTarget.class), execution);
    assertThat(target.resultOrchidGroupIds()).containsExactlyElementsOf(targetIds);
    assertThat(target.resultDetails()).isSameAs(details);
  }

  @Test
  void sourceFactsRequireNumericIdsAndAmountsAndKeepLastQuantityInFirstSeenOrder() {
    assertThat(
            WorkEffectJsonCodec.sourceQuantities(
                Map.of(
                    "sources",
                    List.of(
                        Map.of("sourceOrchidGroupId", 3, "inputQuantity", 4),
                        Map.of("sourceOrchidGroupId", 2L, "inputQuantity", new BigDecimal("6.9")),
                        Map.of("sourceOrchidGroupId", 3L, "inputQuantity", 5),
                        Map.of("sourceOrchidGroupId", "4", "inputQuantity", 8),
                        Map.of("sourceOrchidGroupId", 5, "inputQuantity", "9"),
                        "bad",
                        Map.of()))))
        .containsExactly(Map.entry(3L, 5), Map.entry(2L, 6));
  }

  @Test
  void quantitySnapshotsAcceptJsonObjectKeysButRejectMalformedFacts() {
    assertThat(WorkEffectJsonCodec.inputQuantities(Map.of("3", 4, "2", 5L)))
        .containsExactlyInAnyOrderEntriesOf(Map.of(3L, 4, 2L, 5));
    assertThat(WorkEffectJsonCodec.inputQuantities(List.of(1))).isEmpty();
    assertThatThrownBy(() -> WorkEffectJsonCodec.inputQuantities(Map.of("bad", 4)))
        .isInstanceOf(NumberFormatException.class);
    assertThatThrownBy(() -> WorkEffectJsonCodec.inputQuantities(Map.of("3", "4")))
        .isInstanceOf(ClassCastException.class);
    var nullAmount = new LinkedHashMap<String, Object>();
    nullAmount.put("3", null);
    assertThatThrownBy(() -> WorkEffectJsonCodec.inputQuantities(nullAmount))
        .isInstanceOf(NullPointerException.class);
  }

  static Stream<Object[]> pottingFixtures() {
    return Stream.of(
        new Object[] {
          List.of(3, 4), List.of(Map.of("quantity", 2), Map.of("quantity", 3)), Map.of(3L, 2, 4L, 3)
        },
        new Object[] {List.of(3, 4), List.of(Map.of(), Map.of("quantity", 3)), Map.of()},
        new Object[] {List.of(3, 4), Arrays.asList(null, Map.of("quantity", 3)), Map.of()},
        new Object[] {
          List.of(3, 3), List.of(Map.of("quantity", 2), Map.of("quantity", 3)), Map.of()
        },
        new Object[] {
          List.of("3", 4), List.of(Map.of("quantity", 2), Map.of("quantity", 3)), Map.of()
        },
        new Object[] {
          List.of(3, 4), List.of(Map.of("quantity", "2"), Map.of("quantity", 3)), Map.of()
        },
        new Object[] {List.of(3, 4), List.of(Map.of("quantity", 3)), Map.of()},
        new Object[] {null, null, Map.of()});
  }

  @ParameterizedTest
  @MethodSource("pottingFixtures")
  void pottingRequiresAllPositionalRowsAndDistinctNumericIds(
      Object ids, Object rows, Map<Long, Integer> expected) {
    assertThat(WorkEffectJsonCodec.pottingResultQuantities(ids, rows))
        .containsExactlyInAnyOrderEntriesOf(expected);
  }

  @Test
  void detailCoercionPreservesNullsAndCompactsRowsWithoutChangingQuantityRules() {
    var raw = new LinkedHashMap<Object, Object>();
    raw.put(3, null);
    raw.put(null, "saved");
    assertThat(WorkEffectJsonCodec.map(raw))
        .containsEntry("3", null)
        .containsEntry("null", "saved");
    assertThat(WorkEffectJsonCodec.map(raw).keySet()).containsExactly("3", "null");
    assertThat(WorkEffectJsonCodec.mapList(Arrays.asList(Map.of(), null, "bad", raw)))
        .containsExactly(WorkEffectJsonCodec.map(raw));
    assertThat(WorkEffectJsonCodec.integerValue("4")).isNull();
    assertThat(WorkEffectJsonCodec.integerValue(4.9)).isEqualTo(4);
    assertThat(WorkEffectJsonCodec.longValue(" 3 ")).isNull();
    assertThat(WorkEffectJsonCodec.longValue("3")).isEqualTo(3L);
    assertThat(WorkEffectJsonCodec.decimalValue("1.25")).isEqualByComparingTo("1.25");
    assertThat(WorkEffectJsonCodec.decimalValue("bad")).isNull();
    assertThat(WorkEffectJsonCodec.targetResultIds(null)).isEmpty();
  }
}
