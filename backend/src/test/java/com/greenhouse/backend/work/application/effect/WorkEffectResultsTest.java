package com.greenhouse.backend.work.application.effect;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.work.domain.effect.StructureChangeResultPurpose;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkEffectResultsTest {
	@Test
	void transformationKeepsSingleSourceCompatibilityAndResultOrder() {
		var rows = List.of(new WorkEffectResults.ResultGroup(3L, 6, StructureChangeResultPurpose.NORMAL),
				new WorkEffectResults.ResultGroup(4L, 2, StructureChangeResultPurpose.HELD));
		var result = new WorkEffectResults.Transformation("round", Map.of(1L, 10), 2, rows, 5).toMap();
		assertThat(result).containsEntry("sourceOrchidGroupId", 1L).containsEntry("inputQuantity", 10)
				.containsEntry("remainingQuantity", 5).containsEntry("lossQuantity", 2)
				.containsEntry("resultOrchidGroupIds", List.of(3L, 4L));
		assertThat(result.get("results")).isEqualTo(List.of(
				Map.of("orchidGroupId", 3L, "quantity", 6, "purpose", "NORMAL"),
				Map.of("orchidGroupId", 4L, "quantity", 2, "purpose", "HELD")));
		assertThat(new WorkEffectResults.Transformation("round", Map.of(1L, 4, 2L, 6), 2, rows, null).toMap())
				.doesNotContainKeys("sourceOrchidGroupId", "inputQuantity", "remainingQuantity", "resultOrchidGroupIds");
	}

	@Test
	void creationAndPottingKeepTheirDistinctCountKeys() {
		assertThat(new WorkEffectResults.Created(List.of(3L, 4L)).toMap()).isEqualTo(
				Map.of("createdCount", 2, "createdOrchidGroupIds", List.of(3L, 4L)));
		assertThat(new WorkEffectResults.Potted(9L, List.of(3L, 4L), 20).toMap()).isEqualTo(
				Map.of("inboundRecordId", 9L, "createdOrchidGroupIds", List.of(3L, 4L), "actualQuantity", 20, "resultCount", 2));
	}

	@Test
	void movementKeepsRawSnapshotAndNullPositions() {
		assertThat(new WorkEffectResults.Moved(1L, "old-zone", 9L, null, null).toMap())
				.containsEntry("fromBedZoneId", "old-zone").containsEntry("startPosition", null).containsEntry("endPosition", null);
	}

	@Test
	void discardOmitsMissingReasonAndTrimsPresentReason() {
		assertThat(new WorkEffectResults.Discarded(1L, 10, 2, 8, "관리", "관리", " ").toMap())
				.doesNotContainKey("reason").containsEntry("discardedQuantity", 2).containsEntry("remainingQuantity", 8);
		assertThat(new WorkEffectResults.Discarded(1L, 10, 2, 8, "관리", "관리", " 사유 ").toMap())
				.containsEntry("reason", "사유");
	}

	@Test
	void correctionKeepsDatesAndAdjustmentFields() {
		var date = LocalDate.of(2026, 8, 20);
		assertThat(new WorkEffectResults.Corrected(1L, date, date.minusDays(1), List.of(
				new WorkEffectResults.Adjustment(3L, 10, "관리", 8, "정상"))).toMap())
				.containsEntry("beforeWorkDate", date).containsEntry("afterWorkDate", date.minusDays(1))
				.containsEntry("adjustments", List.of(Map.of("orchidGroupId", 3L, "beforeQuantity", 10,
						"beforeStatus", "관리", "afterQuantity", 8, "afterStatus", "정상")));
	}

	@Test
	void lineageKeepsNumericOnlyIdsAndLegacyMergeQuantity() {
		var merged = new WorkEffectResults.Merged(List.of(1L, 2L), Map.of(1L, 4, 2L, 6), 10, 2, 3L).toMap();
		assertThat(WorkEffectResults.resultQuantities(merged)).containsExactlyEntriesOf(Map.of(3L, 8));
		assertThat(WorkEffectResults.sourceQuantities(Map.of("sources", List.of(
				Map.of("sourceOrchidGroupId", "1", "inputQuantity", 4),
				Map.of("sourceOrchidGroupId", 2L, "inputQuantity", 6)))))
				.containsExactlyEntriesOf(Map.of(2L, 6));
	}
}
