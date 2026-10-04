package com.greenhouse.backend.farm.application.transformation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.greenhouse.backend.farm.dto.transformation.MergeWorkOperationRequest;
import com.greenhouse.backend.farm.dto.transformation.RepotWorkOperationRequest;
import com.greenhouse.backend.work.application.effect.InboundPottingCommand;
import com.greenhouse.backend.work.application.effect.WorkEffectCommand;
import com.greenhouse.backend.work.application.effect.WorkEffectPayload;
import com.greenhouse.backend.work.application.operation.WorkRequestFingerprint;
import com.greenhouse.backend.work.domain.effect.StructureChangeResultPurpose;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class LegacyStructureChangeRequestMapperTest {
  private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
  private final LegacyStructureChangeRequestMapper mapper =
      new LegacyStructureChangeRequestMapper();
  private final WorkRequestFingerprint fingerprints = new WorkRequestFingerprint();

  static Stream<JsonNode> legacyFixtures() throws Exception {
    var fixtures =
        JSON.readTree(
            LegacyStructureChangeRequestMapperTest.class.getResourceAsStream(
                "/work/compatibility/legacy-repot-payload.json"));
    return Stream.iterate(0, i -> i < fixtures.size(), i -> i + 1).map(fixtures::get);
  }

  @ParameterizedTest
  @MethodSource("legacyFixtures")
  void keepsLegacyRequestSerializationAndReceiptFingerprint(JsonNode fixture) throws Exception {
    var oldDto = JSON.treeToValue(fixture.get("request"), RepotWorkOperationRequest.class);
    var typed = mapper.fromRequest(oldDto);
    var expected = fixture.get("serialized");
    assertThat(JSON.readTree(JSON.writeValueAsBytes(oldDto))).isEqualTo(expected);
    assertThat(JSON.readTree(JSON.writeValueAsBytes(typed))).isEqualTo(expected);
    assertThat(fingerprints.calculate(oldDto)).isEqualTo(fixture.path("fingerprint").asText());
    assertThat(fingerprints.calculate(typed)).isEqualTo(fixture.path("fingerprint").asText());
    Map<String, Object> raw = JSON.convertValue(fixture.get("request"), new TypeReference<>() {});
    var decoded = mapper.read(command(raw, null));
    assertThat(decoded).isEqualTo(typed);
    var execution = mapper.from(decoded);
    assertThat(execution.sources())
        .singleElement()
        .satisfies(
            source -> {
              assertThat(source.sourceOrchidGroupId()).isEqualTo(31L);
              assertThat(source.inputQuantity()).isEqualTo(13);
            });
    assertThat(execution.results())
        .singleElement()
        .satisfies(
            result -> {
              assertThat(result.attributeSourceOrchidGroupId()).isEqualTo(31L);
              assertThat(result.purpose()).isEqualTo(StructureChangeResultPurpose.NORMAL);
            });
  }

  @Test
  void typedLegacyInputDoesNotDecodeItsSeparateStoredDetails() throws Exception {
    var fixture = legacyFixtures().findFirst().orElseThrow();
    var typed =
        mapper.fromRequest(
            JSON.treeToValue(fixture.get("request"), RepotWorkOperationRequest.class));
    assertThat(mapper.read(command(Map.of("results", "invalid persistence input"), typed)))
        .isEqualTo(typed);
    assertThat(typed.inheritCollectionIds()).containsExactly(3L, 9L);
    assertThatThrownBy(
            () ->
                mapper.read(
                    command(
                        Map.of(),
                        new InboundPottingCommand("key", 1L, null, List.of(), null, null))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("명령 형식");
  }

  @Test
  void legacyMergeKeepsUnknownRootPolicyAndStrictNestedRows() {
    var source = Map.of("sourceOrchidGroupId", 31, "inputQuantity", 13);
    var result = Map.of("bedZoneId", 7, "quantity", 13, "startPosition", 1, "endPosition", 2);
    var raw = Map.<String, Object>of("sources", List.of(source), "result", result, "future", true);
    var oldDto = JSON.convertValue(raw, MergeWorkOperationRequest.class);
    var decoded = mapper.readMerge(raw);
    assertThat(JSON.<JsonNode>valueToTree(decoded)).isEqualTo(JSON.<JsonNode>valueToTree(oldDto));
    var malformed =
        Map.<String, Object>of("sources", List.of(source), "result", Map.of("future", true));
    assertThatThrownBy(() -> JSON.convertValue(malformed, MergeWorkOperationRequest.class))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> mapper.readMerge(malformed))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> mapper.read(command(Map.of("results", List.of(Map.of("future", true))), null)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private WorkEffectCommand command(Map<String, Object> details, WorkEffectPayload payload) {
    return new WorkEffectCommand(LocalDateTime.of(2026, 7, 15, 0, 0), "작업자", details, payload);
  }
}
