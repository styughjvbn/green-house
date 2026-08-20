package com.greenhouse.backend.work.application.target;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.greenhouse.backend.work.domain.operation.WorkSourceScopeType;
import com.greenhouse.backend.work.domain.target.WorkTargetInclusionSource;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkTargetSelectionTest {

	@Test
	void normalizesDerivedGroupKey() {
		var selection = new WorkTargetSelection(
				WorkSourceScopeType.DERIVED_GROUP, null, " 12:3:POT_35 ", null);

		assertThat(selection.sourceDerivedGroupKey()).isEqualTo("12:3:POT_35");
		assertThat(selection.sourceOrchidGroupIds()).isEmpty();
	}

	@Test
	void normalizesManualSelectionIds() {
		var selection = new WorkTargetSelection(
				WorkSourceScopeType.MANUAL_SELECTION, null, null, List.of(3L, 1L, 3L));

		assertThat(selection.sourceOrchidGroupIds()).containsExactly(3L, 1L);
		assertThat(selection.conditionSnapshot()).containsEntry("orchidGroupIds", List.of(3L, 1L));
		assertThat(selection.inclusionSource()).isEqualTo(WorkTargetInclusionSource.MANUAL_ADDITION);
	}

	@Test
	void derivesSingleTargetIdFromOrchidGroupScope() {
		var selection = WorkTargetSelection.orchidGroup(7L);

		assertThat(selection.sourceOrchidGroupIds()).containsExactly(7L);
		assertThat(selection.conditionSnapshot()).containsEntry("orchidGroupId", 7L);
		assertThat(selection.inclusionSource()).isEqualTo(WorkTargetInclusionSource.DIRECT);
	}

	@Test
	void rejectsMissingValueRequiredBySourceScopeType() {
		assertThatThrownBy(() -> new WorkTargetSelection(
				WorkSourceScopeType.DERIVED_GROUP, null, " ", List.of()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("자동 그룹 키가 필요합니다.");
		assertThatThrownBy(() -> new WorkTargetSelection(
				WorkSourceScopeType.MANUAL_SELECTION, null, null, List.of()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("직접 선택한 난 묶음이 한 개 이상 필요합니다.");
	}
}
