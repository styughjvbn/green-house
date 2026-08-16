package com.greenhouse.backend.work.application.effect;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.work.domain.effect.StructureChangeResultPurpose;
import com.greenhouse.backend.work.dto.effect.StructureChangeExecutionRequest;
import com.greenhouse.backend.work.dto.effect.StructureChangeResultRequest;
import com.greenhouse.backend.work.dto.effect.StructureChangeSourceRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class MovementQuantityAllocatorTest {

	@Test
	void poolsThreeSourcesBeforeSplittingTwoResults() {
		var request = new StructureChangeExecutionRequest(
				"pooled-movement",
				LocalDate.of(2026, 8, 16),
				null,
				null,
				List.of(
						new StructureChangeSourceRequest(1L, 100, null, null),
						new StructureChangeSourceRequest(2L, 100, null, null),
						new StructureChangeSourceRequest(3L, 100, null, null)),
				List.of(
						result(10L, 250, "0", "25"),
						result(11L, 50, "25", "30")));

		assertThat(MovementQuantityAllocator.allocateMovedBySource(request))
				.containsExactly(
						java.util.Map.entry(1L, 100),
						java.util.Map.entry(2L, 100),
						java.util.Map.entry(3L, 100));
	}

	private StructureChangeResultRequest result(
			Long bedZoneId,
			int quantity,
			String startPosition,
			String endPosition) {
		return new StructureChangeResultRequest(
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
