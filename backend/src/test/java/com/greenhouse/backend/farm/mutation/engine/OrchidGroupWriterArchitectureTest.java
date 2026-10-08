package com.greenhouse.backend.farm.mutation.engine;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationWriter;
import com.greenhouse.backend.farm.mutation.ledger.OrchidGroupMutationRecorder;
import com.greenhouse.backend.farm.orchid.domain.OrchidGroup;
import com.greenhouse.backend.farm.orchid.repository.OrchidGroupRepository;
import com.greenhouse.backend.support.EntityWriterInspection;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class OrchidGroupWriterArchitectureTest {

  private static final JavaClasses APPLICATION_CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("com.greenhouse.backend");

  private static final Set<String> TARGET_DIRECT_STATE_WRITER_INVENTORY =
      Set.of("com.greenhouse.backend.farm.mutation.engine.OrchidGroupMutationEngine");

  private static final Set<String> TARGET_CONSTRUCTOR_WRITER_INVENTORY =
      Set.of("com.greenhouse.backend.farm.mutation.engine.OrchidGroupMutationEngine");

  private static final Set<String> TARGET_REPOSITORY_WRITER_INVENTORY =
      Set.of("com.greenhouse.backend.farm.mutation.engine.OrchidGroupMutationEngine");

  private static final Set<String> REPOSITORY_WRITE_METHODS =
      Set.of(
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
  void publicWriterHasOnlyTheEngineImplementationAndRequiresTheCallerTransaction()
      throws NoSuchMethodException {
    assertThat(
            APPLICATION_CLASSES.stream()
                .filter(type -> !type.isInterface())
                .filter(type -> type.isAssignableTo(OrchidGroupMutationWriter.class))
                .map(type -> type.getName()))
        .containsExactly(OrchidGroupMutationEngine.class.getName());
    for (var method : OrchidGroupMutationWriter.class.getMethods()) {
      var implementation =
          OrchidGroupMutationEngine.class.getMethod(method.getName(), method.getParameterTypes());
      var transaction = implementation.getAnnotation(Transactional.class);
      if (transaction == null)
        transaction = OrchidGroupMutationEngine.class.getAnnotation(Transactional.class);
      assertThat(transaction).as(method.getName()).isNotNull();
      assertThat(transaction.propagation()).as(method.getName()).isEqualTo(Propagation.MANDATORY);
      assertThat(transaction.readOnly()).as(method.getName()).isFalse();
    }
  }

  @Test
  void ledgerRecorderRemainsInternalToTheMutationSubsystem() {
    noClasses()
        .that()
        .resideOutsideOfPackage("..farm.mutation..")
        .should()
        .dependOnClassesThat()
        .haveFullyQualifiedName(OrchidGroupMutationRecorder.class.getName())
        .check(APPLICATION_CLASSES);
  }

  @Test
  void runtimeHasNoLegacyRoutingClasses() {
    assertThat(APPLICATION_CLASSES.stream().map(type -> type.getSimpleName()))
        .doesNotContain(
            "OrchidGroupMutationRoutingPolicy",
            "OrchidGroupLedgerWriterMode",
            "OrchidGroupReservationService",
            "OrchidGroupStateChainMigrationService",
            "OrchidGroupLedgerCutoverService",
            "OrchidGroupLedgerPreparationService");
  }

  @Test
  void directStateMutationCallersMatchTheLifecycleInventories() {
    assertThat(EntityWriterInspection.stateCallers(APPLICATION_CLASSES, OrchidGroup.class))
        .containsExactlyInAnyOrderElementsOf(TARGET_DIRECT_STATE_WRITER_INVENTORY);
  }

  @Test
  void constructorsAndRepositoryWritesMatchTheLifecycleInventories() {
    Set<String> constructorCallers =
        APPLICATION_CLASSES.stream()
            .flatMap(
                javaClass ->
                    Stream.concat(
                        javaClass.getConstructorCallsFromSelf().stream(),
                        javaClass.getConstructorReferencesFromSelf().stream()))
            .filter(call -> call.getTargetOwner().isEquivalentTo(OrchidGroup.class))
            .map(call -> call.getOriginOwner().getName())
            .collect(Collectors.toSet());

    assertThat(constructorCallers)
        .containsExactlyInAnyOrderElementsOf(union(TARGET_CONSTRUCTOR_WRITER_INVENTORY));
    assertThat(methodCallers(OrchidGroupRepository.class, REPOSITORY_WRITE_METHODS))
        .containsExactlyInAnyOrderElementsOf(union(TARGET_REPOSITORY_WRITER_INVENTORY));
  }

  private Set<String> methodCallers(Class<?> targetOwner, Set<String> methodNames) {
    return APPLICATION_CLASSES.stream()
        .flatMap(
            javaClass ->
                Stream.concat(
                    javaClass.getMethodCallsFromSelf().stream(),
                    javaClass.getMethodReferencesFromSelf().stream()))
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
