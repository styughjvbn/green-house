package com.greenhouse.backend.farm.application.orchid.mutation;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class OrchidGroupWriterArchitectureTest {

	private static final JavaClasses APPLICATION_CLASSES = new ClassFileImporter()
			.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
			.importPackages("com.greenhouse.backend");

	private static final Set<String> LEGACY_RETIRE_ROUTING_CALLER_INVENTORY = Set.of(
			"com.greenhouse.backend.farm.application.inbound.InboundPottingService",
			"com.greenhouse.backend.farm.application.inbound.InboundRecordService",
			"com.greenhouse.backend.farm.application.orchid.DiscardWorkHandler",
			"com.greenhouse.backend.farm.application.orchid.MovementWorkHandler",
			"com.greenhouse.backend.farm.application.orchid.OrchidGroupCommandService",
			"com.greenhouse.backend.farm.application.transformation.BatchStructureTransformationExecutor",
			"com.greenhouse.backend.farm.application.transformation.CorrectionWorkHandler",
			"com.greenhouse.backend.farm.application.transformation.MergeWorkHandler",
			"com.greenhouse.backend.farm.application.transformation.MultiCreateWorkHandler",
			"com.greenhouse.backend.farm.application.transformation.MultiCreateWorkOperationService",
			"com.greenhouse.backend.farm.application.variety.VarietyService",
			"com.greenhouse.backend.sales.application.SalesSlipInventoryService");

	private static final Set<String> ORCHID_GROUP_STATE_METHODS = Set.of(
			"updateDetails",
			"moveTo",
			"assignVariety",
			"assignInboundRecord",
			"cancelCreation",
			"applyRepot",
			"applyTransformation",
			"discard",
			"correctQuantityAndStatus",
			"reserve",
			"releaseReserved",
			"outboundReserved",
			"restoreOutbound",
			"establishBaselineRevision",
			"establishCreationRevision",
			"advanceStateRevision");

	private static final Set<String> TARGET_DIRECT_STATE_WRITER_INVENTORY = Set.of(
			"com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationEngine");

	private static final Set<String> TRANSITION_ONLY_DIRECT_STATE_WRITER_INVENTORY = Set.of(
			"com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerPreparationService");

	private static final Set<String> LEGACY_RETIRE_DIRECT_STATE_WRITER_INVENTORY = Set.of(
			"com.greenhouse.backend.farm.application.inbound.InboundPottingService",
			"com.greenhouse.backend.farm.application.inbound.InboundRecordService",
			"com.greenhouse.backend.farm.application.orchid.DiscardWorkHandler",
			"com.greenhouse.backend.farm.application.orchid.OrchidGroupCommandService",
			"com.greenhouse.backend.farm.application.transformation.BatchStructureTransformationExecutor",
			"com.greenhouse.backend.farm.application.transformation.CorrectionWorkHandler",
			"com.greenhouse.backend.farm.application.transformation.MergeWorkHandler",
			"com.greenhouse.backend.farm.application.transformation.MultiCreateWorkOperationService",
			"com.greenhouse.backend.farm.application.variety.VarietyService",
			"com.greenhouse.backend.sales.application.SalesSlipInventoryService");

	private static final Set<String> TARGET_CONSTRUCTOR_WRITER_INVENTORY = Set.of(
			"com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationEngine");

	private static final Set<String> LEGACY_RETIRE_CONSTRUCTOR_WRITER_INVENTORY = Set.of(
			"com.greenhouse.backend.farm.application.inbound.InboundPottingService",
			"com.greenhouse.backend.farm.application.inbound.InboundRecordService",
			"com.greenhouse.backend.farm.application.orchid.OrchidGroupCommandService");

	private static final Set<String> TARGET_REPOSITORY_WRITER_INVENTORY = Set.of(
			"com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationEngine");

	private static final Set<String> LEGACY_RETIRE_REPOSITORY_WRITER_INVENTORY = Set.of(
			"com.greenhouse.backend.farm.application.inbound.InboundPottingService",
			"com.greenhouse.backend.farm.application.inbound.InboundRecordService",
			"com.greenhouse.backend.farm.application.orchid.OrchidGroupCommandService");

	private static final Set<String> REPOSITORY_WRITE_METHODS = Set.of(
			"save",
			"saveAll",
			"saveAndFlush",
			"saveAllAndFlush",
			"delete",
			"deleteAll",
			"deleteById",
			"deleteAllById",
			"deleteAllInBatch",
			"deleteAllByIdInBatch");

	@Test
	void routingFlagCallersMatchTheTransitionInventory() {
		assertThat(methodCallers(OrchidGroupMutationRoutingPolicy.class, Set.of("routesToEngine")))
				.containsExactlyInAnyOrderElementsOf(LEGACY_RETIRE_ROUTING_CALLER_INVENTORY);
	}

	@Test
	void directStateMutationCallersMatchTheLifecycleInventories() {
		assertThat(methodCallers(OrchidGroup.class, ORCHID_GROUP_STATE_METHODS))
				.containsExactlyInAnyOrderElementsOf(union(
						TARGET_DIRECT_STATE_WRITER_INVENTORY,
						TRANSITION_ONLY_DIRECT_STATE_WRITER_INVENTORY,
						LEGACY_RETIRE_DIRECT_STATE_WRITER_INVENTORY));
	}

	@Test
	void constructorsAndRepositoryWritesMatchTheLifecycleInventories() {
		Set<String> constructorCallers = APPLICATION_CLASSES.stream()
				.flatMap(javaClass -> javaClass.getConstructorCallsFromSelf().stream())
				.filter(call -> call.getTargetOwner().isEquivalentTo(OrchidGroup.class))
				.map(call -> call.getOriginOwner().getName())
				.collect(Collectors.toSet());

		assertThat(constructorCallers)
				.containsExactlyInAnyOrderElementsOf(union(
						TARGET_CONSTRUCTOR_WRITER_INVENTORY,
						LEGACY_RETIRE_CONSTRUCTOR_WRITER_INVENTORY));
		assertThat(methodCallers(OrchidGroupRepository.class, REPOSITORY_WRITE_METHODS))
				.containsExactlyInAnyOrderElementsOf(union(
						TARGET_REPOSITORY_WRITER_INVENTORY,
						LEGACY_RETIRE_REPOSITORY_WRITER_INVENTORY));
	}

	@Test
	void lifecycleInventoriesDoNotOverlap() {
		assertThat(TARGET_DIRECT_STATE_WRITER_INVENTORY)
				.doesNotContainAnyElementsOf(TRANSITION_ONLY_DIRECT_STATE_WRITER_INVENTORY)
				.doesNotContainAnyElementsOf(LEGACY_RETIRE_DIRECT_STATE_WRITER_INVENTORY);
		assertThat(TRANSITION_ONLY_DIRECT_STATE_WRITER_INVENTORY)
				.doesNotContainAnyElementsOf(LEGACY_RETIRE_DIRECT_STATE_WRITER_INVENTORY);
		assertThat(TARGET_CONSTRUCTOR_WRITER_INVENTORY)
				.doesNotContainAnyElementsOf(LEGACY_RETIRE_CONSTRUCTOR_WRITER_INVENTORY);
		assertThat(TARGET_REPOSITORY_WRITER_INVENTORY)
				.doesNotContainAnyElementsOf(LEGACY_RETIRE_REPOSITORY_WRITER_INVENTORY);
	}

	private Set<String> methodCallers(Class<?> targetOwner, Set<String> methodNames) {
		return APPLICATION_CLASSES.stream()
				.flatMap(javaClass -> javaClass.getMethodCallsFromSelf().stream())
				.filter(call -> call.getTargetOwner().isEquivalentTo(targetOwner))
				.filter(call -> !call.getOriginOwner().isEquivalentTo(targetOwner))
				.filter(call -> methodNames.contains(call.getTarget().getName()))
				.map(call -> call.getOriginOwner().getName())
				.collect(Collectors.toSet());
	}

	@SafeVarargs
	private static Set<String> union(Set<String>... inventories) {
		Set<String> result = new HashSet<>();
		for (Set<String> inventory : inventories) {
			result.addAll(inventory);
		}
		return Set.copyOf(result);
	}
}
