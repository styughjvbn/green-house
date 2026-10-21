package com.greenhouse.backend.sales.document.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.greenhouse.backend.sales.document.application.command.SalesSlipAllocationInput;
import com.greenhouse.backend.sales.document.application.command.SalesSlipCommand;
import com.greenhouse.backend.sales.document.application.command.SalesSlipItemInput;
import java.lang.reflect.RecordComponent;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class SalesCreationFingerprintCompatibilityTest {
  private final JsonMapper mapper =
      JsonMapper.builder()
          .findAndAddModules()
          .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .build();

  @Test
  void preservesLegacyCreationFingerprints() throws Exception {
    try (var input = getClass().getResourceAsStream("/sales/creation-v1.json")) {
      assertThat(input).isNotNull();
      for (var fixture : mapper.readTree(input)) {
        var request = mapper.treeToValue(fixture.get("input"), SalesSlipCommand.class);
        var payload = SalesCreationRequestPayload.from(request);
        assertThat(mapper.writeValueAsString(payload))
            .isEqualTo(mapper.writeValueAsString(request));
        assertThat(hash(payload))
            .as(fixture.get("name").asText())
            .isEqualTo(fixture.get("fingerprint").asText());
        assertThat(hash(request)).isEqualTo(fixture.get("fingerprint").asText());
      }
    }
  }

  @Test
  void numericUnitsKeepCanonicalLegacyTextFingerprints() throws Exception {
    var request = mapper.readValue("{\"items\":[{\"unit\":10}]}", SalesSlipCommand.class);
    var legacy =
        mapper.readTree(
            mapper.writeValueAsString(request).replace("\"unit\":10", "\"unit\":\"10\""));
    assertThat(hash(SalesCreationRequestPayload.from(request))).isEqualTo(hash(legacy));
  }

  @Test
  void storedTextUnitsAreProjectedAsNumbersWithoutChangingHistoricalJson() throws Exception {
    for (String raw : List.of("10", " 0010 ", "분", "10개", "0", "1.5", "2147483648")) {
      String snapshot = "{\"items\":[{\"unit\":\"" + raw + "\"}]}";
      var result = SalesSlipCreationService.readStoredResponse(snapshot);
      assertThat(result.items().getFirst().unit())
          .isEqualTo(raw.equals("10") || raw.equals(" 0010 ") ? 10 : null);
      assertThat(mapper.readTree(snapshot).path("items").get(0).path("unit").asText())
          .isEqualTo(raw);
    }
  }

  @Test
  void fieldChangesRequireCreationReceiptCompatibilityReview() throws Exception {
    try (var input = getClass().getResourceAsStream("/sales/creation-v1-fields.json")) {
      assertThat(input).isNotNull();
      Map<String, List<String>> schemas = mapper.readValue(input, new TypeReference<>() {});
      assertThat(schemas.keySet())
          .containsExactlyInAnyOrder(
              SalesSlipCommand.class.getName(),
              SalesSlipItemInput.class.getName(),
              SalesSlipAllocationInput.class.getName());
      for (var schema : schemas.entrySet()) {
        var type = Class.forName(schema.getKey());
        // Keep the v1 fixture unchanged: absent optional packing fields must retain its hash.
        var expected =
            schema.getKey().equals(SalesSlipItemInput.class.getName())
                ? Stream.concat(
                        schema.getValue().stream(), List.of("unit", "boxes", "varietyId").stream())
                    .toList()
                : schema.getKey().equals(SalesSlipCommand.class.getName())
                    ? Stream.concat(
                            schema.getValue().stream(), Stream.of("recordOnly", "sourcePhoto"))
                        .toList()
                    : schema.getValue();
        assertThat(Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList())
            .as("%s: review versioning/replay before updating the v1 fixture", schema.getKey())
            .containsExactlyInAnyOrderElementsOf(expected);
      }
    }
  }

  @Test
  void nullableCollectionsAndNullElementsKeepTheirLegacyHashMeaning() throws Exception {
    for (String json :
        List.of(
            "null",
            "{\"items\":null}",
            "{\"items\":[null]}",
            "{\"items\":[{\"allocations\":null}]}",
            "{\"items\":[{\"allocations\":[null]}]}")) {
      var request = mapper.readValue(json, SalesSlipCommand.class);
      assertThat(hash(SalesCreationRequestPayload.from(request))).isEqualTo(hash(request));
    }
  }

  @Test
  void sourcePhotoIsOptInAndParticipatesInTheFingerprint() throws Exception {
    var legacy = mapper.readValue("{\"items\":[]}", SalesSlipCommand.class);
    var photo =
        mapper.readValue(
            "{\"items\":[],\"sourcePhoto\":{\"fileName\":\"slip.png\",\"contentType\":\"image/png\",\"contentBase64\":\"first\"}}",
            SalesSlipCommand.class);
    var changed =
        mapper.readValue(
            "{\"items\":[],\"sourcePhoto\":{\"fileName\":\"slip.png\",\"contentType\":\"image/png\",\"contentBase64\":\"second\"}}",
            SalesSlipCommand.class);
    assertThat(mapper.writeValueAsString(SalesCreationRequestPayload.from(legacy)))
        .doesNotContain("sourcePhoto");
    assertThat(hash(SalesCreationRequestPayload.from(photo)))
        .isNotEqualTo(hash(SalesCreationRequestPayload.from(legacy)));
    assertThat(hash(SalesCreationRequestPayload.from(photo)))
        .isNotEqualTo(hash(SalesCreationRequestPayload.from(changed)));
  }

  private String hash(Object request) throws Exception {
    return HexFormat.of()
        .formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(request)));
  }

  @Test
  void recordOnlyIsOptInAndFalseRetainsTheLegacyFingerprint() throws Exception {
    var legacy = mapper.readValue("{\"items\":[]}", SalesSlipCommand.class);
    var disabled = mapper.readValue("{\"items\":[],\"recordOnly\":false}", SalesSlipCommand.class);
    var enabled = mapper.readValue("{\"items\":[],\"recordOnly\":true}", SalesSlipCommand.class);
    assertThat(hash(SalesCreationRequestPayload.from(disabled)))
        .isEqualTo(hash(SalesCreationRequestPayload.from(legacy)));
    assertThat(hash(SalesCreationRequestPayload.from(enabled)))
        .isNotEqualTo(hash(SalesCreationRequestPayload.from(legacy)));
  }
}
