package com.greenhouse.backend.work.domain.operation;

import com.greenhouse.backend.work.domain.target.WorkTargetReferenceType;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;

/** Stable work behavior; active/system flags and lifecycle state remain on the entities. */
@RequiredArgsConstructor
public enum WorkTypeDefinition {
	GENERIC(null, WorkTypeWorkflow.GENERIC, WorkTargetReferenceType.ORCHID_GROUP, true),
	INBOUND(null, WorkTypeWorkflow.GENERIC, WorkTargetReferenceType.ORCHID_GROUP, false),
	POTTING("POTTING", WorkTypeWorkflow.POTTING, WorkTargetReferenceType.INBOUND_RECORD, false),
	MOVEMENT(null, WorkTypeWorkflow.MOVEMENT, WorkTargetReferenceType.ORCHID_GROUP, true),
	REPOT(null, WorkTypeWorkflow.STRUCTURE_CHANGE, WorkTargetReferenceType.ORCHID_GROUP, true),
	DIVIDE("DIVIDE", WorkTypeWorkflow.STRUCTURE_CHANGE, WorkTargetReferenceType.ORCHID_GROUP, true),
	MERGE("MERGE", WorkTypeWorkflow.STRUCTURE_CHANGE, WorkTargetReferenceType.ORCHID_GROUP, true),
	DISCARD("DISCARD", WorkTypeWorkflow.DISCARD, WorkTargetReferenceType.ORCHID_GROUP, true),
	MULTI_CREATE(null, WorkTypeWorkflow.GENERIC, WorkTargetReferenceType.ORCHID_GROUP, true),
	CORRECTION(null, WorkTypeWorkflow.GENERIC, WorkTargetReferenceType.ORCHID_GROUP, true);

	private static final Map<String, WorkTypeDefinition> BY_CODE = Arrays.stream(values())
			.collect(Collectors.toUnmodifiableMap(Enum::name, Function.identity()));

	private final String handlerOverride;
	private final WorkTypeWorkflow workflow;
	private final WorkTargetReferenceType targetSource;
	private final boolean manualRegistrationAllowed;

	public static WorkTypeDefinition forCode(String code) {
		return code == null ? GENERIC : BY_CODE.getOrDefault(code, GENERIC);
	}

	public String handlerCode(WorkTypeTemplate template) {
		// Existing code overrides take precedence; other codes retain their stored template's handler.
		return handlerOverride == null ? template.handlerCode() : handlerOverride;
	}

	public WorkTypeWorkflow workflow() {
		return workflow;
	}

	public WorkTargetReferenceType targetSource() {
		return targetSource;
	}

	public boolean allowsManualRegistration() {
		return manualRegistrationAllowed;
	}

	public boolean hasDedicatedRegistration() {
		return workflow != WorkTypeWorkflow.GENERIC;
	}

	public boolean supportsPeriodPlanning() {
		return hasDedicatedRegistration() && workflow != WorkTypeWorkflow.POTTING;
	}

	public boolean supportsStructureExecution() {
		return workflow == WorkTypeWorkflow.STRUCTURE_CHANGE || workflow == WorkTypeWorkflow.MOVEMENT;
	}

	public static Set<String> requiredHandlerCodes() {
		return Stream.concat(
				Arrays.stream(values()).map(definition -> definition.handlerOverride).filter(Objects::nonNull),
				Arrays.stream(WorkTypeTemplate.values()).map(WorkTypeTemplate::handlerCode))
				.collect(Collectors.toUnmodifiableSet());
	}
}
