package com.greenhouse.backend.work.application.effect;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.greenhouse.backend.work.application.correction.WorkQuantityBalance;
import com.greenhouse.backend.work.application.correction.WorkQuantityBalanceChange;
import com.greenhouse.backend.work.domain.effect.StructureChangeResultPurpose;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class WorkExecutionResultTest {

  private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

  @ParameterizedTest(name = "{0}")
  @MethodSource("fixedResults")
  void carriesTypedValuesAndWritesTheExistingHistoricalJson(
      String name, WorkEffectResultDetails details) throws Exception {
    var link = new WorkMutationLink(91L, UUID.randomUUID());
    var result = new WorkExecutionResult("TEST", details, List.of(31L, 32L), link);

    assertThat(result.details()).isSameAs(details);
    assertThat(result.resultOrchidGroupIds()).containsExactly(31L, 32L);
    assertThat(result.mutationLink()).isEqualTo(link);
    try (var input =
        getClass().getResourceAsStream("/work/compatibility/effect-result-details.json")) {
      JsonNode expected = mapper.readTree(input).path(name);
      assertThat(mapper.readTree(mapper.writeValueAsString(result.storedDetails())))
          .isEqualTo(expected);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"null", "empty", "historical"})
  void replayPreservesTheStoredJsonWithoutInferringIdsOrRebuildingCurrentTypes(String kind) {
    var stored = raw(kind);
    var link = new WorkMutationLink(91L, UUID.randomUUID());

    var replay = WorkExecutionResult.fromStored("LEGACY", stored, List.of(41L, 42L), link);

    assertThat(replay.handlerCode()).isEqualTo("LEGACY");
    assertThat(replay.details()).isInstanceOf(WorkEffectResults.Json.class);
    assertThat(replay.storedDetails()).isSameAs(stored);
    assertThat(replay.resultOrchidGroupIds()).containsExactly(41L, 42L);
    assertThat(replay.mutationLink()).isEqualTo(link);
  }

  @ParameterizedTest
  @ValueSource(strings = {"null", "empty", "historical"})
  void recordOnlyEffectsKeepFreeFormValuesIncludingUnknownAndNullFields(String kind) {
    var details = raw(kind);
    var command = new WorkEffectCommand(LocalDateTime.of(2026, 7, 18, 0, 0), null, details, null);

    var result = new RecordOnlyWorkHandler().execute(null, command);

    assertThat(result.details()).isInstanceOf(WorkEffectResults.Json.class);
    assertThat(result.storedDetails()).isSameAs(details);
    assertThat(result.resultOrchidGroupIds()).isEmpty();
    assertThat(result.mutationLink()).isNull();
  }

  private Map<String, Object> raw(String kind) {
    if (kind.equals("null")) return null;
    if (kind.equals("empty")) return Map.of();
    var stored = new LinkedHashMap<String, Object>();
    stored.put("resultOrchidGroupIds", List.of("7", 9L, 9L));
    stored.put("createdOrchidGroupIds", List.of(13L));
    stored.put(
        "results", List.of(Map.of("orchidGroupId", 17L, "quantity", "6", "futureField", true)));
    stored.put("unknownEmpty", null);
    stored.put("futureExtension", List.of("second", "first"));
    return stored;
  }

  static Stream<Arguments> fixedResults() {
    var rows =
        List.of(
            new WorkEffectResults.ResultGroup(31L, 6, StructureChangeResultPurpose.NORMAL),
            new WorkEffectResults.ResultGroup(32L, 2, StructureChangeResultPurpose.HELD));
    var inputs = new LinkedHashMap<Long, Integer>();
    inputs.put(7L, 4);
    inputs.put(9L, 6);
    var date = LocalDate.of(2026, 7, 18);
    var adjustments = List.of(new WorkEffectResults.Adjustment(31L, 6, "관리", 5, "정상"));
    var before =
        new WorkQuantityBalance(
            23L, Map.of(7L, 10), Map.of(31L, 6, 32L, 2), 10, 8, 2, 0, true, false, true);
    var after =
        new WorkQuantityBalance(
            23L, Map.of(7L, 10), Map.of(31L, 5, 32L, 2), 10, 7, 3, 0, true, false, true);
    return Stream.of(
        Arguments.of(
            "transformation-single",
            new WorkEffectResults.Transformation("round", Map.of(7L, 10), 2, 0, rows, 5)),
        Arguments.of(
            "transformation-multi",
            new WorkEffectResults.Transformation("round", inputs, 2, 0, rows, null)),
        Arguments.of(
            "movement-identity-single",
            new WorkEffectResults.Transformation("round", Map.of(7L, 10), 2, 0, rows, 5, true)),
        Arguments.of(
            "movement-identity-multi",
            new WorkEffectResults.Transformation("round", inputs, 2, 0, rows, null, true)),
        Arguments.of("merged", new WorkEffectResults.Merged(List.of(7L, 9L), inputs, 10, 2, 31L)),
        Arguments.of("created", new WorkEffectResults.Created(List.of(31L, 32L))),
        Arguments.of("potted", new WorkEffectResults.Potted(51L, List.of(31L, 32L), 8)),
        Arguments.of("moved-null", new WorkEffectResults.Moved(7L, "legacy-zone", 9L, null, null)),
        Arguments.of(
            "discarded-reason", new WorkEffectResults.Discarded(7L, 10, 2, 8, "관리", "관리", " 사유 ")),
        Arguments.of(
            "discarded-missing-reason",
            new WorkEffectResults.Discarded(7L, 10, 2, 8, "관리", "관리", " ")),
        Arguments.of(
            "corrected",
            new WorkEffectResults.Corrected(17L, date, date.minusDays(1), adjustments)),
        Arguments.of(
            "corrected-balances",
            new WorkEffectResults.Corrected(
                17L,
                date,
                date.minusDays(1),
                adjustments,
                List.of(new WorkQuantityBalanceChange(before, after)))),
        Arguments.of(
            "corrected-date-only", new WorkEffectResults.Corrected(17L, null, date, List.of())));
  }
}
