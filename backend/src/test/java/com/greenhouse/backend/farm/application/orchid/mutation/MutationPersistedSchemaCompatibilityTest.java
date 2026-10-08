package com.greenhouse.backend.farm.application.orchid.mutation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.greenhouse.backend.farm.api.orchid.OrchidGroupStateSnapshot;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class MutationPersistedSchemaCompatibilityTest {

  private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();

  @Test
  void fieldChangesRequireAPersistedFormatDecisionEvenWhenLegacyHashesStillMatch()
      throws Exception {
    Map<String, List<String>> schemas;
    try (var input = getClass().getResourceAsStream("/farm/mutation-v1-fields.json")) {
      assertThat(input).isNotNull();
      schemas = mapper.readValue(input, new TypeReference<>() {});
    }
    List<Class<?>> nestedSchemas =
        List.of(
            OrchidGroupMutationDetails.class,
            CreateOrchidGroupMutationItem.class,
            TransformOrchidGroupMutationSource.class,
            TransformOrchidGroupMutationResult.class,
            MoveOrchidGroupMutationItem.class,
            OrchidGroupQuantityMutationItem.class,
            CorrectOrchidGroupMutationItem.class,
            RelatedOrchidGroupMutations.class,
            OrchidGroupStateSnapshot.class);
    var expectedTypes =
        Stream.concat(
                Arrays.stream(OrchidGroupMutationCommand.class.getPermittedSubclasses()),
                nestedSchemas.stream())
            .map(Class::getName)
            .toList();
    assertThat(schemas.keySet()).containsExactlyInAnyOrderElementsOf(expectedTypes);
    for (var schema : schemas.entrySet()) {
      var type = Class.forName(schema.getKey());
      assertThat(type.isRecord()).as(schema.getKey()).isTrue();
      assertThat(Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList())
          .as("%s: review versioning/replay before updating the v1 fixture", schema.getKey())
          .containsExactlyInAnyOrderElementsOf(schema.getValue());
    }
  }

  @Test
  void keepsSnapshotJsonNullAndCanonicalFingerprintContracts() throws Exception {
    try (var input = getClass().getResourceAsStream("/farm/state-snapshot-v1.json")) {
      assertThat(input).isNotNull();
      for (var fixture : mapper.readTree(input)) {
        var snapshot =
            mapper.treeToValue(fixture.get("input"), OrchidGroupStateSnapshot.class).canonical();
        assertThat(mapper.readTree(mapper.writeValueAsString(snapshot)))
            .as(fixture.get("name").asText())
            .isEqualTo(fixture.get("canonical"));
        assertThat(new OrchidGroupMutationFingerprint().calculate(snapshot))
            .as(fixture.get("name").asText())
            .isEqualTo(fixture.get("fingerprint").asText());
        var roundTrip =
            mapper.readValue(mapper.writeValueAsString(snapshot), OrchidGroupStateSnapshot.class);
        assertThat(roundTrip.canonical()).isEqualTo(snapshot);
      }
    }
  }

  @Test
  void missingHistoricalValuesStayUnknownInsteadOfBecomingCurrentDefaults() throws Exception {
    var sparse = mapper.readValue("{\"quantity\":10}", OrchidGroupStateSnapshot.class);
    var defaulted =
        mapper.readValue(
            "{\"quantity\":10,\"reservedQuantity\":0,\"splitPlacementAllowed\":false}",
            OrchidGroupStateSnapshot.class);
    assertThat(sparse.reservedQuantity()).isNull();
    assertThat(sparse.splitPlacementAllowed()).isNull();
    assertThat(sparse.canonical()).isNotEqualTo(defaulted.canonical());
    var fingerprint = new OrchidGroupMutationFingerprint();
    assertThat(fingerprint.calculate(sparse.canonical()))
        .isNotEqualTo(fingerprint.calculate(defaulted.canonical()));
  }

  @Test
  void rejectsUnknownSnapshotFieldsAndLossyPositionCanonicalization() throws Exception {
    assertThatThrownBy(
            () ->
                mapper.readValue(
                    "{\"quantity\":10,\"futureAttribute\":true}", OrchidGroupStateSnapshot.class))
        .isInstanceOf(UnrecognizedPropertyException.class);
    var imprecise = mapper.readValue("{\"startPosition\":6.001}", OrchidGroupStateSnapshot.class);
    assertThatThrownBy(imprecise::canonical).isInstanceOf(ArithmeticException.class);
  }
}
