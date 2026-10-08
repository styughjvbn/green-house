package com.greenhouse.backend.work.application.effect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.greenhouse.backend.work.api.effect.InboundPottingCommand;
import com.greenhouse.backend.work.api.effect.InboundPottingResultInput;
import com.greenhouse.backend.work.application.operation.WorkRequestFingerprint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InboundPottingCommandCodecTest {

  private final InboundPottingCommandCodec codec = new InboundPottingCommandCodec();
  private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

  @Test
  void retainsTheStoredJsonAndFingerprintWithEveryResultFieldAndRowOrder() throws Exception {
    var command =
        new InboundPottingCommand(
            "retry-key",
            51L,
            LocalDate.of(2026, 7, 18),
            List.of(
                new InboundPottingResultInput(
                    7L,
                    13,
                    "2인치",
                    2,
                    "트레이",
                    3,
                    true,
                    new BigDecimal("12.25"),
                    new BigDecimal("14.75"),
                    "결과 메모"),
                nullableResult()),
            "포트 담당",
            "포트 완료");

    assertGolden(
        command,
        "potting-command-rich",
        "8626edc61cf816bded501bfcfe2ad07a0401433b86ee2720226ab0a0c9ed89c5");
    assertThat(codec.decode(command.inboundRecordId(), codec.encode(command)))
        .isEqualTo(
            new InboundPottingCommand(
                null,
                51L,
                command.pottingDate(),
                command.results(),
                command.worker(),
                command.memo()));
  }

  @Test
  void retainsExplicitNullKeysInTheStoredJsonAndFingerprint() throws Exception {
    var command =
        new InboundPottingCommand(
            "key", 51L, LocalDate.of(2026, 7, 18), List.of(nullableResult()), null, null);
    assertGolden(
        command,
        "potting-command-nullable",
        "1e2767ba85eb0033dea190f42067996c5232c2399283bfa95a6ec7625a7cc487");
  }

  @ParameterizedTest
  @ValueSource(strings = {"[2026,7,18]", "\"2026-07-18\""})
  void decodesHistoricalArrayAndStringDatesWithoutReorderingResults(String date) throws Exception {
    var details = golden("potting-command-rich");
    details.put("pottingDate", mapper.readValue(date, Object.class));
    var command = codec.decode(51L, details);

    assertThat(command.pottingDate()).isEqualTo(LocalDate.of(2026, 7, 18));
    assertThat(command.inboundRecordId()).isEqualTo(51L);
    assertThat(command.idempotencyKey()).isNull();
    assertThat(command.results())
        .extracting(InboundPottingResultInput::bedZoneId)
        .containsExactly(7L, 9L);
    assertThat(command.results().getFirst().splitPlacementAllowed()).isTrue();
    assertThat(command.results().getFirst().memo()).isEqualTo("결과 메모");
  }

  @Test
  void decodesAbsentOptionalFieldsLikeExplicitNulls() throws Exception {
    var details = golden("potting-command-nullable");
    details.remove("worker");
    details.remove("memo");
    var row = new LinkedHashMap<>((Map<?, ?>) ((List<?>) details.get("results")).getFirst());
    List.of("potSize", "ageYear", "placementType", "trayCount", "splitPlacementAllowed", "memo")
        .forEach(row::remove);
    details.put("results", List.of(row));

    var decoded = codec.decode(51L, details);
    assertThat(decoded.results()).containsExactly(nullableResult());
    assertThat(decoded.worker()).isNull();
    assertThat(decoded.memo()).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"inboundRecordId", "idempotencyKey", "unknownField"})
  void preservesTheLegacyBoundaryRejectionOfUnknownStoredKeys(String key) throws Exception {
    var details = golden("potting-command-rich");
    details.put(key, "extra");
    assertThatThrownBy(() -> codec.decode(51L, details))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private InboundPottingResultInput nullableResult() {
    return new InboundPottingResultInput(
        9L,
        17,
        null,
        null,
        null,
        null,
        null,
        new BigDecimal("15.25"),
        new BigDecimal("17.75"),
        null);
  }

  private void assertGolden(InboundPottingCommand command, String name, String fingerprint)
      throws Exception {
    var details = codec.encode(command);
    assertThat(mapper.readTree(mapper.writeValueAsString(details)))
        .isEqualTo(mapper.readTree(mapper.writeValueAsString(golden(name))));
    assertThat(details.keySet()).containsExactly("pottingDate", "results", "worker", "memo");
    assertThat(new WorkRequestFingerprint().calculate(details)).isEqualTo(fingerprint);
  }

  private Map<String, Object> golden(String name) throws Exception {
    try (var input = getClass().getResourceAsStream("/work/compatibility/" + name + ".json")) {
      return mapper.readValue(input, new TypeReference<>() {});
    }
  }
}
