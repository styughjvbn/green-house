package com.greenhouse.backend.work.domain.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkOperationStructureChangeCapabilityTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 22, 9, 0);

	@Test
	void movementCanBeCorrectedAndVoidedAsAStructureChange() {
		WorkOperation operation = completedOperation(WorkTypeDefinition.MOVEMENT, WorkTypeTemplate.MOVEMENT);

		operation.markCorrected();
		operation.voidCompletedMutationWork(NOW.plusMinutes(1), "잘못 등록한 이동", "void-movement", 101L);

		assertThat(operation.getStatus()).isEqualTo(WorkOperationStatus.VOIDED);
		assertThat(operation.getVoidMutationId()).isEqualTo(101L);
	}

	@Test
	void discardCannotBeCorrectedButCanBeVoided() {
		WorkOperation operation = completedOperation(WorkTypeDefinition.DISCARD, WorkTypeTemplate.DISCARD);

		assertThatThrownBy(operation::markCorrected).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("완료된 구조 변경 작업만 보정할 수 있습니다.");

		operation.voidCompletedMutationWork(NOW.plusMinutes(1), "잘못 등록한 폐기", "void-discard", 102L);

		assertThat(operation.getStatus()).isEqualTo(WorkOperationStatus.VOIDED);
		assertThat(operation.getVoidMutationId()).isEqualTo(102L);
	}

	private WorkOperation completedOperation(WorkTypeDefinition definition, WorkTypeTemplate template) {
		WorkType type = new WorkType(definition.name(), definition.name(), template, true, false, true, 1);
		WorkOperation operation = new WorkOperation(type, "테스트 작업", LocalDate.from(NOW), null,
				WorkSourceScopeType.ORCHID_GROUP, 1L, Map.of(), Map.of(), null, null, NOW.minusMinutes(1));
		operation.complete(NOW);
		return operation;
	}

}
