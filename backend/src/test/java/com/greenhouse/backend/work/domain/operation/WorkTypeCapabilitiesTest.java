package com.greenhouse.backend.work.domain.operation;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.work.domain.effect.WorkEffectKind;
import com.greenhouse.backend.work.domain.target.WorkTargetReferenceType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WorkTypeCapabilitiesTest {

	private static final Map<WorkTypeTemplate, String> TEMPLATE_HANDLERS = Map.of(WorkTypeTemplate.PESTICIDE,
			"RECORD_ONLY", WorkTypeTemplate.FERTILIZER, "RECORD_ONLY", WorkTypeTemplate.CLEANUP, "RECORD_ONLY",
			WorkTypeTemplate.STATUS, "RECORD_ONLY", WorkTypeTemplate.MEMO, "RECORD_ONLY", WorkTypeTemplate.REPOT,
			"REPOT", WorkTypeTemplate.MOVEMENT, "MOVE", WorkTypeTemplate.DISCARD, "DISCARD",
			WorkTypeTemplate.MULTI_CREATE, "MULTI_CREATE", WorkTypeTemplate.CORRECTION, "CORRECTION");

	private static final Map<WorkTypeTemplate, WorkEffectKind> TEMPLATE_KINDS = Map.of(WorkTypeTemplate.PESTICIDE,
			WorkEffectKind.RECORD_ONLY, WorkTypeTemplate.FERTILIZER, WorkEffectKind.RECORD_ONLY,
			WorkTypeTemplate.CLEANUP, WorkEffectKind.RECORD_ONLY, WorkTypeTemplate.STATUS, WorkEffectKind.RECORD_ONLY,
			WorkTypeTemplate.MEMO, WorkEffectKind.RECORD_ONLY, WorkTypeTemplate.CORRECTION, WorkEffectKind.RECORD_ONLY,
			WorkTypeTemplate.REPOT, WorkEffectKind.STRUCTURE_CHANGE, WorkTypeTemplate.MULTI_CREATE,
			WorkEffectKind.STRUCTURE_CHANGE, WorkTypeTemplate.DISCARD, WorkEffectKind.ATTRIBUTE_CHANGE,
			WorkTypeTemplate.MOVEMENT, WorkEffectKind.ATTRIBUTE_CHANGE);

	@ParameterizedTest
	@CsvSource({ "INBOUND,GENERIC,ORCHID_GROUP,true,false,false,false,",
			"POTTING,POTTING,INBOUND_RECORD,true,false,true,false,POTTING",
			"MOVEMENT,MOVEMENT,ORCHID_GROUP,false,true,true,true,",
			"REPOT,STRUCTURE_CHANGE,ORCHID_GROUP,false,true,true,true,",
			"DIVIDE,STRUCTURE_CHANGE,ORCHID_GROUP,false,true,true,true,DIVIDE",
			"MERGE,STRUCTURE_CHANGE,ORCHID_GROUP,false,true,true,true,MERGE",
			"DISCARD,DISCARD,ORCHID_GROUP,false,true,true,false,DISCARD",
			"MULTI_CREATE,GENERIC,ORCHID_GROUP,false,false,false,false,",
			"CORRECTION,GENERIC,ORCHID_GROUP,false,false,false,false,",
			"CUSTOM_CARE,GENERIC,ORCHID_GROUP,false,false,false,false," })
	void preservesExistingCodeTemplateAndFlagCombinations(String code, WorkTypeWorkflow workflow,
			WorkTargetReferenceType targetSource, boolean managedRegistration, boolean dedicatedPeriodPlan,
			boolean dedicatedRegistration, boolean structureExecution, String handlerOverride) {
		for (WorkTypeTemplate template : WorkTypeTemplate.values()) {
			for (boolean active : List.of(false, true)) {
				for (boolean system : List.of(false, true)) {
					var type = new WorkType(code, "호환 작업", template, false, system, active, 1);
					boolean manual = active && !system && !managedRegistration
							&& TEMPLATE_KINDS.get(template) == WorkEffectKind.RECORD_ONLY;
					assertThat(type.isManualCreateAllowed()).isEqualTo(manual);
					assertThat(type.isPeriodOperationAllowed()).isEqualTo(active && (manual || dedicatedPeriodPlan));
					assertThat(type.isSettingsEditable()).isEqualTo(!system && !managedRegistration);
					assertThat(type.registrationModes()).isEqualTo(active && (manual || dedicatedRegistration)
							? List.of(WorkRegistrationMode.RECORD, WorkRegistrationMode.PLAN) : List.of());
					assertThat(type.handlerCode())
						.isEqualTo(handlerOverride == null ? TEMPLATE_HANDLERS.get(template) : handlerOverride);
					assertThat(type.effectKind()).isEqualTo(TEMPLATE_KINDS.get(template));
					assertThat(type.workflow()).isEqualTo(workflow);
					assertThat(type.registrationTargetSource()).isEqualTo(targetSource);
					assertThat(type.definition().supportsStructureExecution()).isEqualTo(structureExecution);
				}
			}
		}
	}

	@Test
	void metadataStillOffersOnlyTheFiveRecordTemplates() {
		assertThat(java.util.Arrays.stream(WorkTypeTemplate.values()).filter(WorkTypeTemplate::isCustomTypeAllowed))
			.containsExactly(WorkTypeTemplate.PESTICIDE, WorkTypeTemplate.FERTILIZER, WorkTypeTemplate.CLEANUP,
					WorkTypeTemplate.STATUS, WorkTypeTemplate.MEMO);
	}

	@Test
	void exposesGenericRecordAndPlanCapabilities() {
		WorkType workType = new WorkType("CUSTOM_CARE", "관리", WorkTypeTemplate.MEMO, false, false, true, 1);

		assertThat(workType.registrationModes()).containsExactly(WorkRegistrationMode.RECORD,
				WorkRegistrationMode.PLAN);
		assertThat(workType.workflow()).isEqualTo(WorkTypeWorkflow.GENERIC);
		assertThat(workType.registrationTargetSource()).isEqualTo(WorkTargetReferenceType.ORCHID_GROUP);
	}

	@Test
	void exposesDedicatedPottingCapabilities() {
		WorkType workType = new WorkType(WorkTypeDefinition.POTTING.name(), "포트 작업", WorkTypeTemplate.REPOT, true,
				false, true, 1);

		assertThat(workType.registrationModes()).containsExactly(WorkRegistrationMode.RECORD,
				WorkRegistrationMode.PLAN);
		assertThat(workType.workflow()).isEqualTo(WorkTypeWorkflow.POTTING);
		assertThat(workType.registrationTargetSource()).isEqualTo(WorkTargetReferenceType.INBOUND_RECORD);
		assertThat(workType.isSettingsEditable()).isFalse();
	}

	@Test
	void doesNotExposeUnsupportedCustomStructureTemplate() {
		WorkType workType = new WorkType("CUSTOM_REPOT", "사용자 분갈이", WorkTypeTemplate.REPOT, false, false, true, 1);

		assertThat(workType.registrationModes()).isEmpty();
		assertThat(WorkTypeTemplate.REPOT.isCustomTypeAllowed()).isFalse();
		assertThat(WorkTypeTemplate.MEMO.isCustomTypeAllowed()).isTrue();
	}

}
