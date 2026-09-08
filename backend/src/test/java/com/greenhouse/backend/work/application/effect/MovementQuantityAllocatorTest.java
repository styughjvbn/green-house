package com.greenhouse.backend.work.application.effect;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.work.domain.effect.StructureChangeResultPurpose;
import com.greenhouse.backend.work.application.effect.StructureChangeCommand;
import com.greenhouse.backend.work.application.effect.StructureChangeResultInput;
import com.greenhouse.backend.work.application.effect.StructureChangeSourceInput;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class MovementQuantityAllocatorTest {

	@Test
	void poolsThreeSourcesBeforeSplittingTwoResults() {
		var request = new StructureChangeCommand(
				"pooled-movement",
				LocalDate.of(2026, 8, 16),
				null,
				null,
				List.of(
						new StructureChangeSourceInput(1L, 100, null, null),
						new StructureChangeSourceInput(2L, 100, null, null),
						new StructureChangeSourceInput(3L, 100, null, null)),
				List.of(
						result(10L, 250, "0", "25"),
						result(11L, 50, "25", "30")));

		assertThat(MovementQuantityAllocator.allocateMovedBySource(request))
				.containsExactly(
						java.util.Map.entry(1L, 100),
						java.util.Map.entry(2L, 100),
						java.util.Map.entry(3L, 100));
	}

	private StructureChangeResultInput result(
			Long bedZoneId,
			int quantity,
			String startPosition,
			String endPosition) {
		return new StructureChangeResultInput(
				bedZoneId,
				quantity,
				1L,
				null,
				null,
				StructureChangeResultPurpose.NORMAL,
				null,
				null,
				false,
				new BigDecimal(startPosition),
				new BigDecimal(endPosition),
				null);
	}
}
