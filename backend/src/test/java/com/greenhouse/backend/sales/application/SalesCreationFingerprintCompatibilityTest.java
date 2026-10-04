package com.greenhouse.backend.sales.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.greenhouse.backend.sales.application.command.SalesSlipAllocationInput;
import com.greenhouse.backend.sales.application.command.SalesSlipCommand;
import com.greenhouse.backend.sales.application.command.SalesSlipItemInput;
import java.lang.reflect.RecordComponent;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
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
        assertThat(Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList())
            .as("%s: review versioning/replay before updating the v1 fixture", schema.getKey())
            .containsExactlyInAnyOrderElementsOf(schema.getValue());
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

  private String hash(Object request) throws Exception {
    return HexFormat.of()
        .formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(request)));
  }
}
