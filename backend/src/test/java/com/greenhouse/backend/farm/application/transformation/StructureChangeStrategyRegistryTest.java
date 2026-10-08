package com.greenhouse.backend.farm.application.transformation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.greenhouse.backend.farm.domain.transformation.OrchidGroupLineageRelationType;
import com.greenhouse.backend.work.operation.domain.WorkTypeDefinition;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StructureChangeStrategyRegistryTest {

  @Test
  void allDeclaredStructureWorkflowsHaveStrategies() {
    var strategies =
        List.<StructureChangeStrategy>of(
            new MovementStrategy(), new RepotStrategy(), new DivideStrategy(), new MergeStrategy());
    var registry = new StructureChangeStrategyRegistry(strategies);
    assertThatCode(registry::validateDefinitions).doesNotThrowAnyException();
    strategies.forEach(
        strategy -> assertThat(registry.get(strategy.supports())).isSameAs(strategy));
  }

  @Test
  void missingAndDuplicateStrategiesFailBeforeExecution() {
    var incomplete =
        new StructureChangeStrategyRegistry(
            List.of(new MovementStrategy(), new RepotStrategy(), new DivideStrategy()));
    assertThatThrownBy(incomplete::validateDefinitions)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("MERGE");
    assertThatThrownBy(
            () ->
                new StructureChangeStrategyRegistry(
                    List.of(new RepotStrategy(), new RepotStrategy())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("중복");
    assertThatThrownBy(() -> incomplete.get("UNKNOWN"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("UNKNOWN");
  }

  @Test
  void everyStoredStructureHandlerUsesTheExecutionStrategiesLineageRelation() {
    var registry =
        new StructureChangeStrategyRegistry(
            List.of(
                new MovementStrategy(),
                new RepotStrategy(),
                new DivideStrategy(),
                new MergeStrategy()));
    var relations =
        Map.of(
            "MOVEMENT",
            OrchidGroupLineageRelationType.MOVED_TO,
            "MOVE",
            OrchidGroupLineageRelationType.MOVED_TO,
            "REPOT",
            OrchidGroupLineageRelationType.REPOTTED_TO,
            "DIVIDE",
            OrchidGroupLineageRelationType.SPLIT_TO,
            "MERGE",
            OrchidGroupLineageRelationType.MERGED_TO);
    relations.forEach(
        (handler, relation) ->
            assertThat(
                    registry
                        .get(
                            WorkTypeDefinition.forStoredStructureHandler(handler)
                                .orElseThrow()
                                .name())
                        .lineageType())
                .isEqualTo(relation));
  }

  @Test
  void missingLineageRelationFailsBeforeAnyExecution() {
    var missingRelation = mock(StructureChangeStrategy.class);
    when(missingRelation.supports()).thenReturn("REPOT");
    var registry =
        new StructureChangeStrategyRegistry(
            List.of(
                new MovementStrategy(),
                missingRelation,
                new DivideStrategy(),
                new MergeStrategy()));
    assertThatThrownBy(registry::validateDefinitions)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("계보 관계")
        .hasMessageContaining("REPOT");
  }
}
