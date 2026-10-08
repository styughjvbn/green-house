package com.greenhouse.backend.work.operation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.work.api.effect.WorkEffectKind;
import com.greenhouse.backend.work.api.operation.WorkTypeTemplate;
import com.greenhouse.backend.work.api.operation.WorkTypeWorkflow;
import com.greenhouse.backend.work.api.target.WorkTargetReferenceType;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WorkTypeCapabilitiesTest {

  @ParameterizedTest
  @CsvSource({"MOVEMENT,MOVEMENT", "MOVE,MOVEMENT", "REPOT,REPOT", "DIVIDE,DIVIDE", "MERGE,MERGE"})
  void storedStructureHandlersResolveIndependentlyOfWorkTypeNames(
      String handler, WorkTypeDefinition expected) {
    assertThat(WorkTypeDefinition.forStoredStructureHandler(handler)).contains(expected);
  }

  @Test
  void nonStructureAndUnknownHandlersDoNotAcquireLineageFromCurrentMetadata() {
    for (String handler :
        List.of("DISCARD", "POTTING", "RECORD_ONLY", "RECONCILIATION", "CUSTOM_CARE", "UNKNOWN")) {
      assertThat(WorkTypeDefinition.forStoredStructureHandler(handler)).isEmpty();
    }
    assertThat(WorkTypeDefinition.forStoredStructureHandler(null)).isEmpty();
    assertThat(WorkTypeDefinition.forCode("MOVE")).isEqualTo(WorkTypeDefinition.GENERIC);
  }

  private static final Map<WorkTypeTemplate, String> TEMPLATE_HANDLERS =
      Map.ofEntries(
          Map.entry(WorkTypeTemplate.PESTICIDE, "RECORD_ONLY"),
          Map.entry(WorkTypeTemplate.FERTILIZER, "RECORD_ONLY"),
          Map.entry(WorkTypeTemplate.CLEANUP, "RECORD_ONLY"),
          Map.entry(WorkTypeTemplate.STATUS, "RECORD_ONLY"),
          Map.entry(WorkTypeTemplate.MEMO, "RECORD_ONLY"),
          Map.entry(WorkTypeTemplate.REPOT, "REPOT"),
          Map.entry(WorkTypeTemplate.MOVEMENT, "MOVE"),
          Map.entry(WorkTypeTemplate.DISCARD, "DISCARD"),
          Map.entry(WorkTypeTemplate.RECONCILIATION, "RECONCILIATION"));

  private static final Map<WorkTypeTemplate, WorkEffectKind> TEMPLATE_KINDS =
      Map.ofEntries(
          Map.entry(WorkTypeTemplate.PESTICIDE, WorkEffectKind.RECORD_ONLY),
          Map.entry(WorkTypeTemplate.FERTILIZER, WorkEffectKind.RECORD_ONLY),
          Map.entry(WorkTypeTemplate.CLEANUP, WorkEffectKind.RECORD_ONLY),
          Map.entry(WorkTypeTemplate.STATUS, WorkEffectKind.RECORD_ONLY),
          Map.entry(WorkTypeTemplate.MEMO, WorkEffectKind.RECORD_ONLY),
          Map.entry(WorkTypeTemplate.REPOT, WorkEffectKind.STRUCTURE_CHANGE),
          Map.entry(WorkTypeTemplate.DISCARD, WorkEffectKind.ATTRIBUTE_CHANGE),
          Map.entry(WorkTypeTemplate.MOVEMENT, WorkEffectKind.ATTRIBUTE_CHANGE),
          Map.entry(WorkTypeTemplate.RECONCILIATION, WorkEffectKind.ATTRIBUTE_CHANGE));

  @ParameterizedTest
  @CsvSource({
    "INBOUND,GENERIC,ORCHID_GROUP,true,false,false,false,",
    "POTTING,POTTING,INBOUND_RECORD,true,false,true,false,POTTING",
    "MOVEMENT,MOVEMENT,ORCHID_GROUP,false,true,true,true,",
    "REPOT,STRUCTURE_CHANGE,ORCHID_GROUP,false,true,true,true,",
    "DIVIDE,STRUCTURE_CHANGE,ORCHID_GROUP,false,true,true,true,DIVIDE",
    "MERGE,STRUCTURE_CHANGE,ORCHID_GROUP,false,true,true,true,MERGE",
    "DISCARD,DISCARD,ORCHID_GROUP,false,true,true,false,DISCARD",
    "RECONCILIATION,GENERIC,ORCHID_GROUP,true,false,false,false,",
    "CUSTOM_CARE,GENERIC,ORCHID_GROUP,false,false,false,false,"
  })
  void preservesExistingCodeTemplateAndFlagCombinations(
      String code,
      WorkTypeWorkflow workflow,
      WorkTargetReferenceType targetSource,
      boolean managedRegistration,
      boolean dedicatedPeriodPlan,
      boolean dedicatedRegistration,
      boolean structureExecution,
      String handlerOverride) {
    for (WorkTypeTemplate template : WorkTypeTemplate.values()) {
      for (boolean active : List.of(false, true)) {
        for (boolean system : List.of(false, true)) {
          var type = new WorkType(code, "호환 작업", template, false, system, active, 1);
          boolean manual =
              active
                  && !system
                  && !managedRegistration
                  && TEMPLATE_KINDS.get(template) == WorkEffectKind.RECORD_ONLY;
          assertThat(type.isManualCreateAllowed()).isEqualTo(manual);
          assertThat(type.isPeriodOperationAllowed())
              .isEqualTo(active && (manual || dedicatedPeriodPlan));
          assertThat(type.isSettingsEditable()).isEqualTo(!system && !managedRegistration);
          assertThat(type.registrationModes())
              .isEqualTo(
                  active && (manual || dedicatedRegistration)
                      ? List.of(WorkRegistrationMode.RECORD, WorkRegistrationMode.PLAN)
                      : List.of());
          assertThat(type.handlerCode())
              .isEqualTo(
                  handlerOverride == null ? TEMPLATE_HANDLERS.get(template) : handlerOverride);
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
    assertThat(
            Arrays.stream(WorkTypeTemplate.values()).filter(WorkTypeTemplate::isCustomTypeAllowed))
        .containsExactly(
            WorkTypeTemplate.PESTICIDE,
            WorkTypeTemplate.FERTILIZER,
            WorkTypeTemplate.CLEANUP,
            WorkTypeTemplate.STATUS,
            WorkTypeTemplate.MEMO);
  }

  @Test
  void exposesGenericRecordAndPlanCapabilities() {
    WorkType workType =
        new WorkType("CUSTOM_CARE", "관리", WorkTypeTemplate.MEMO, false, false, true, 1);

    assertThat(workType.registrationModes())
        .containsExactly(WorkRegistrationMode.RECORD, WorkRegistrationMode.PLAN);
    assertThat(workType.workflow()).isEqualTo(WorkTypeWorkflow.GENERIC);
    assertThat(workType.registrationTargetSource()).isEqualTo(WorkTargetReferenceType.ORCHID_GROUP);
  }

  @Test
  void exposesDedicatedPottingCapabilities() {
    WorkType workType =
        new WorkType(
            WorkTypeDefinition.POTTING.name(),
            "포트 작업",
            WorkTypeTemplate.REPOT,
            true,
            false,
            true,
            1);

    assertThat(workType.registrationModes())
        .containsExactly(WorkRegistrationMode.RECORD, WorkRegistrationMode.PLAN);
    assertThat(workType.workflow()).isEqualTo(WorkTypeWorkflow.POTTING);
    assertThat(workType.registrationTargetSource())
        .isEqualTo(WorkTargetReferenceType.INBOUND_RECORD);
    assertThat(workType.isSettingsEditable()).isFalse();
    assertThat(workType.supportsMutationVoid()).isTrue();
  }

  @Test
  void exposesMutationVoidOnlyForSupportedWorkflows() {
    assertThat(
            new WorkType("MOVEMENT", "자리 이동", WorkTypeTemplate.MOVEMENT, true, true, true, 1)
                .supportsMutationVoid())
        .isTrue();
    assertThat(
            new WorkType("REPOT", "분갈이", WorkTypeTemplate.REPOT, true, true, true, 1)
                .supportsMutationVoid())
        .isTrue();
    assertThat(
            new WorkType("DIVIDE", "분주", WorkTypeTemplate.REPOT, true, true, true, 1)
                .supportsMutationVoid())
        .isTrue();
    assertThat(
            new WorkType("MERGE", "합식", WorkTypeTemplate.REPOT, true, true, true, 1)
                .supportsMutationVoid())
        .isTrue();
    assertThat(
            new WorkType("DISCARD", "폐기", WorkTypeTemplate.DISCARD, true, true, true, 1)
                .supportsMutationVoid())
        .isTrue();
    assertThat(
            new WorkType("POTTING", "포트 작업", WorkTypeTemplate.REPOT, true, true, true, 1)
                .supportsMutationVoid())
        .isTrue();
    assertThat(
            new WorkType(
                    "RECONCILIATION",
                    "현장 상태 동기화",
                    WorkTypeTemplate.RECONCILIATION,
                    true,
                    true,
                    true,
                    1)
                .supportsMutationVoid())
        .isFalse();
  }

  @Test
  void requiresVarietySpecificOperationsForStructureChangeAndDiscard() {
    assertThat(WorkTypeDefinition.MOVEMENT.requiresVarietySpecificOperation()).isTrue();
    assertThat(WorkTypeDefinition.REPOT.requiresVarietySpecificOperation()).isTrue();
    assertThat(WorkTypeDefinition.DIVIDE.requiresVarietySpecificOperation()).isTrue();
    assertThat(WorkTypeDefinition.MERGE.requiresVarietySpecificOperation()).isTrue();
    assertThat(WorkTypeDefinition.DISCARD.requiresVarietySpecificOperation()).isTrue();
    assertThat(WorkTypeDefinition.POTTING.requiresVarietySpecificOperation()).isFalse();
    assertThat(WorkTypeDefinition.GENERIC.requiresVarietySpecificOperation()).isFalse();
  }

  @Test
  void providesStableAutomaticHistoryTitlesForManagedTypes() {
    assertThat(WorkTypeDefinition.INBOUND.historyTitle()).isEqualTo("입고");
    assertThat(WorkTypeDefinition.POTTING.historyTitle()).isEqualTo("포트 식재");
    assertThat(WorkTypeDefinition.MOVEMENT.historyTitle()).isEqualTo("자리 이동");
    assertThat(WorkTypeDefinition.REPOT.historyTitle()).isEqualTo("분갈이");
    assertThat(WorkTypeDefinition.DIVIDE.historyTitle()).isEqualTo("분주");
    assertThat(WorkTypeDefinition.MERGE.historyTitle()).isEqualTo("합식");
    assertThat(WorkTypeDefinition.DISCARD.historyTitle()).isEqualTo("폐기");
    assertThat(WorkTypeDefinition.RECONCILIATION.historyTitle()).isEqualTo("현장 상태 조정");
    assertThat(WorkTypeDefinition.GENERIC.historyTitle()).isNull();
  }

  @Test
  void doesNotExposeUnsupportedCustomStructureTemplate() {
    WorkType workType =
        new WorkType("CUSTOM_REPOT", "사용자 분갈이", WorkTypeTemplate.REPOT, false, false, true, 1);

    assertThat(workType.registrationModes()).isEmpty();
    assertThat(WorkTypeTemplate.REPOT.isCustomTypeAllowed()).isFalse();
    assertThat(WorkTypeTemplate.MEMO.isCustomTypeAllowed()).isTrue();
  }
}
