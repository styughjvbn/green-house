package com.greenhouse.backend.work.application.operation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.greenhouse.backend.work.dto.operation.WorkOperationBatchCreateRequest;
import com.greenhouse.backend.work.dto.operation.WorkOperationCreateRequest;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkCreationFingerprintCompatibilityTest {
  private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();

  @Test
  void preservesLegacyCreationFingerprints() throws Exception {
    try (var input = getClass().getResourceAsStream("/work/creation-v1.json")) {
      assertThat(input).isNotNull();
      for (var fixture : mapper.readTree(input)) {
        var request = mapper.treeToValue(fixture.get("input"), WorkOperationCreateRequest.class);
        var payload = WorkCreationRequestPayload.from(request);
        var projectedJson = mapper.readTree(mapper.writeValueAsString(payload));
        assertThat(projectedJson).isEqualTo(mapper.readTree(mapper.writeValueAsString(request)));
        assertThat(new WorkRequestFingerprint().calculate(payload))
            .as(fixture.get("name").asText())
            .isEqualTo(fixture.get("fingerprint").asText());
        assertThat(new WorkRequestFingerprint().calculate(request))
            .isEqualTo(fixture.get("fingerprint").asText());
        assertThat(
                new WorkRequestFingerprint()
                    .calculate(
                        WorkCreationRequestPayload.from(
                            new WorkOperationBatchCreateRequest(request))))
            .isEqualTo(fixture.get("batchFingerprint").asText());
      }
    }
  }

  @Test
  void fieldChangesRequireCreationReceiptCompatibilityReview() throws Exception {
    try (var input = getClass().getResourceAsStream("/work/creation-v1-fields.json")) {
      assertThat(input).isNotNull();
      Map<String, List<String>> schemas = mapper.readValue(input, new TypeReference<>() {});
      assertThat(schemas.keySet())
          .containsExactlyInAnyOrder(
              WorkOperationCreateRequest.class.getName(),
              WorkOperationBatchCreateRequest.class.getName());
      for (var schema : schemas.entrySet()) {
        var type = Class.forName(schema.getKey());
        assertThat(Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList())
            .as("%s: review versioning/replay before updating the v1 fixture", schema.getKey())
            .containsExactlyInAnyOrderElementsOf(schema.getValue());
      }
    }
  }

  @Test
  void allFreeFormDetailsRemainInTheRequestComparison() throws Exception {
    var first =
        mapper.readValue(
            "{\"title\":\" title \",\"details\":{\"futureDetail\":null,\"amount\":6.00,\"order\":[3,1]}}",
            WorkOperationCreateRequest.class);
    var equivalent =
        mapper.readValue(
            "{\"title\":\" title \",\"details\":{\"order\":[3,1],\"amount\":6,\"futureDetail\":null}}",
            WorkOperationCreateRequest.class);
    var changed =
        mapper.readValue(
            "{\"title\":\" title \",\"details\":{\"futureDetail\":false,\"amount\":6,\"order\":[3,1]}}",
            WorkOperationCreateRequest.class);
    var fingerprint = new WorkRequestFingerprint();
    assertThat(fingerprint.calculate(WorkCreationRequestPayload.from(first)))
        .isEqualTo(fingerprint.calculate(first))
        .isEqualTo(fingerprint.calculate(WorkCreationRequestPayload.from(equivalent)))
        .isNotEqualTo(fingerprint.calculate(WorkCreationRequestPayload.from(changed)));
  }
}
