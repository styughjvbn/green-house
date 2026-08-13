package com.greenhouse.backend.work.domain.operation;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.work.domain.target.WorkTargetReferenceType;
import org.junit.jupiter.api.Test;

class WorkTypeCapabilitiesTest {

	@Test
	void exposesGenericRecordAndPlanCapabilities() {
		WorkType workType = new WorkType(
				"CUSTOM_CARE", "관리", WorkTypeTemplate.MEMO, false, false, true, 1);

		assertThat(workType.registrationModes()).containsExactly(
				WorkRegistrationMode.RECORD,
				WorkRegistrationMode.PLAN);
		assertThat(workType.workflow()).isEqualTo(WorkTypeWorkflow.GENERIC);
		assertThat(workType.registrationTargetSource()).isEqualTo(WorkTargetReferenceType.ORCHID_GROUP);
	}

	@Test
	void exposesDedicatedPottingCapabilities() {
		WorkType workType = new WorkType(
				WorkType.POTTING_CODE, "포트 작업", WorkTypeTemplate.REPOT, true, false, true, 1);

		assertThat(workType.registrationModes()).containsExactly(
				WorkRegistrationMode.RECORD,
				WorkRegistrationMode.PLAN);
		assertThat(workType.workflow()).isEqualTo(WorkTypeWorkflow.POTTING);
		assertThat(workType.registrationTargetSource()).isEqualTo(WorkTargetReferenceType.INBOUND_RECORD);
		assertThat(workType.isSettingsEditable()).isFalse();
	}

	@Test
	void doesNotExposeUnsupportedCustomStructureTemplate() {
		WorkType workType = new WorkType(
				"CUSTOM_REPOT", "사용자 분갈이", WorkTypeTemplate.REPOT, false, false, true, 1);

		assertThat(workType.registrationModes()).isEmpty();
		assertThat(WorkTypeTemplate.REPOT.isCustomTypeAllowed()).isFalse();
		assertThat(WorkTypeTemplate.MEMO.isCustomTypeAllowed()).isTrue();
	}
}
