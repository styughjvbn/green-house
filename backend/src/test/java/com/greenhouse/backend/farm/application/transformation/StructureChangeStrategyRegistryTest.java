package com.greenhouse.backend.farm.application.transformation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class StructureChangeStrategyRegistryTest {

	@Test
	void allDeclaredStructureWorkflowsHaveStrategies() {
		var strategies = List.<StructureChangeStrategy>of(new MovementStrategy(), new RepotStrategy(),
				new DivideStrategy(), new MergeStrategy());
		var registry = new StructureChangeStrategyRegistry(strategies);
		assertThatCode(registry::validateDefinitions).doesNotThrowAnyException();
		strategies.forEach(strategy -> assertThat(registry.get(strategy.supports())).isSameAs(strategy));
	}

	@Test
	void missingAndDuplicateStrategiesFailBeforeExecution() {
		var incomplete = new StructureChangeStrategyRegistry(
				List.of(new MovementStrategy(), new RepotStrategy(), new DivideStrategy()));
		assertThatThrownBy(incomplete::validateDefinitions).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("MERGE");
		assertThatThrownBy(() -> new StructureChangeStrategyRegistry(List.of(new RepotStrategy(), new RepotStrategy())))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("중복");
		assertThatThrownBy(() -> incomplete.get("UNKNOWN")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("UNKNOWN");
	}

}
