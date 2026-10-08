package com.greenhouse.backend;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.farm.application.orchid.OrchidGroupReader;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.work.application.correction.WorkCorrectionPort;
import com.greenhouse.backend.work.application.operation.WorkCommandReceipts;
import com.greenhouse.backend.work.application.operation.WorkOperationSupport;
import com.greenhouse.backend.work.application.operation.WorkRequestFingerprint;
import com.greenhouse.backend.work.dto.operation.WorkOperationCreateRequest;
import com.greenhouse.backend.work.spi.operation.InboundPottingVoidPort;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Entity;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class PublicApplicationContractArchitectureTest {

  @Test
  void allUsedCrossModuleApplicationMembersExposeValueContracts() throws ClassNotFoundException {
    var classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.greenhouse.backend");
    Set<String> contracts = CrossModuleApplicationApiInspection.contracts(classes);
    for (String contract : contracts) {
      if (contract.startsWith("TYPE\t")) {
        assertValueType(Class.forName(contract.substring(5)), new HashSet<>());
      }
    }
    for (var owner : classes) {
      for (var method : owner.getMethods()) {
        if (!contracts.contains("METHOD\t" + method.getFullName())) continue;
        var reflected = method.reflect();
        var visited = new HashSet<Type>();
        assertValueType(reflected.getGenericReturnType(), visited);
        for (Type parameter : reflected.getGenericParameterTypes())
          assertValueType(parameter, visited);
      }
      for (var constructor : owner.getConstructors()) {
        if (!contracts.contains("CONSTRUCTOR\t" + constructor.getFullName())) continue;
        var visited = new HashSet<Type>();
        for (Type parameter : constructor.reflect().getGenericParameterTypes())
          assertValueType(parameter, visited);
      }
      // An implemented port is also a cross-module contract even without a direct invocation.
      if (owner.isInterface() && contracts.contains("TYPE\t" + owner.getName())) {
        for (var method : owner.getMethods()) {
          var reflected = method.reflect();
          var visited = new HashSet<Type>();
          assertValueType(reflected.getGenericReturnType(), visited);
          for (Type parameter : reflected.getGenericParameterTypes())
            assertValueType(parameter, visited);
        }
      }
    }
  }

  @Test
  void explicitBusinessApisAndSpisExposeOnlyValuesInEveryPublicMember()
      throws ClassNotFoundException {
    for (var type : ModuleBoundaryInventoryTest.CLASSES) {
      if (!Set.of("farm", "work", "sales")
              .contains(ArchitecturePackages.module(type.getPackageName()))
          || !ArchitecturePackages.isExplicitContract(type.getPackageName())) continue;
      var reflected = Class.forName(type.getName());
      assertValueType(reflected, new HashSet<>(), true);
      for (var method : reflected.getDeclaredMethods()) {
        if (!Modifier.isPublic(method.getModifiers())) continue;
        var visited = new HashSet<Type>();
        assertValueType(method.getGenericReturnType(), visited, true);
        for (Type parameter : method.getGenericParameterTypes())
          assertValueType(parameter, visited, true);
      }
      for (var constructor : reflected.getConstructors()) {
        for (Type parameter : constructor.getGenericParameterTypes())
          assertValueType(parameter, new HashSet<>(), true);
      }
      for (var field : reflected.getFields())
        assertValueType(field.getGenericType(), new HashSet<>(), true);
    }
  }

  @Test
  void internalFeatureApisAreNotVisibleToOtherTopLevelModules() {
    for (var origin : ModuleBoundaryInventoryTest.CLASSES) {
      for (var dependency : origin.getDirectDependenciesFromSelf()) {
        var target = dependency.getTargetClass();
        if (!Set.of("farm", "work", "sales")
                .contains(ArchitecturePackages.module(target.getPackageName()))
            || !ArchitecturePackages.isExplicitContract(target.getPackageName())
            || ArchitecturePackages.module(origin.getPackageName())
                .equals(ArchitecturePackages.module(target.getPackageName()))) continue;
        assertThat(ArchitecturePackages.isModuleContract(target.getPackageName()))
            .as("Top-level consumers require a module API/SPI: %s", dependency)
            .isTrue();
      }
    }
  }

  @Test
  void nestedEntityAndCallbackContractsAreRejected() {
    Assertions.assertThatThrownBy(() -> assertValueType(EntityLeak.class, new HashSet<>()))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("Entity in a public value contract");
    Assertions.assertThatThrownBy(() -> assertValueType(CallbackLeak.class, new HashSet<>()))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("Storage callback");
  }

  private record EntityLeak(List<OrchidGroup> groups) {}

  private record CallbackLeak(Supplier<Long> storage) {}

  private record HttpLeak(List<WorkOperationCreateRequest> requests) {}

  @Test
  void explicitContractsRejectNestedHttpDtos() {
    Assertions.assertThatThrownBy(() -> assertValueType(HttpLeak.class, new HashSet<>(), true))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("HTTP type in a public value contract");
  }

  @Test
  void orchidGroupReaderExposesValuesInsteadOfEntitiesIncludingNestedContainers() {
    for (var method : OrchidGroupReader.class.getDeclaredMethods()) {
      if (!Modifier.isPublic(method.getModifiers())) continue;
      var visited = new HashSet<Type>();
      assertValueType(method.getGenericReturnType(), visited);
      for (Type parameter : method.getGenericParameterTypes()) {
        assertValueType(parameter, visited);
      }
    }
  }

  @Test
  void correctionPortExposesValuesInsteadOfStorageCallbacks() {
    for (var method : WorkCorrectionPort.class.getDeclaredMethods()) {
      var visited = new HashSet<Type>();
      assertValueType(method.getGenericReturnType(), visited);
      for (Type parameter : method.getGenericParameterTypes()) {
        assertValueType(parameter, visited);
      }
    }
  }

  @Test
  void inboundPottingVoidPortExposesBusinessValuesInsteadOfReceiptCallbacks() {
    for (var method : InboundPottingVoidPort.class.getDeclaredMethods()) {
      var visited = new HashSet<Type>();
      assertValueType(method.getGenericReturnType(), visited);
      for (Type parameter : method.getGenericParameterTypes()) {
        assertValueType(parameter, visited);
      }
    }
  }

  @Test
  void workReceiptMechanismIsUsedOnlyInsideWork() {
    var classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.greenhouse.backend");
    for (Class<?> mechanism : List.of(WorkCommandReceipts.class, WorkRequestFingerprint.class)) {
      noClasses()
          .that()
          .resideOutsideOfPackage("..work..")
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName(mechanism.getName())
          .check(classes);
    }
  }

  @Test
  void workCompositionSupportIsUsedOnlyInsideWork() {
    var classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.greenhouse.backend");
    noClasses()
        .that()
        .resideOutsideOfPackage("..work..")
        .should()
        .dependOnClassesThat()
        .haveFullyQualifiedName(WorkOperationSupport.class.getName())
        .check(classes);
  }

  @Test
  void aggregateAuditSupportIsUsedOnlyInsideItsOwningModule() {
    var classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.greenhouse.backend");
    for (String helper :
        List.of(
            "sales.application.document.SalesSlipAuditSupport",
            "sales.application.payment.PaymentAuditSupport",
            "sales.application.partner.PartnerSettingsAuditSupport")) {
      String owner = "com.greenhouse.backend." + helper.substring(0, helper.lastIndexOf('.'));
      noClasses()
          .that()
          .resideOutsideOfPackage(owner + "..")
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName("com.greenhouse.backend." + helper)
          .check(classes);
    }
  }

  static void assertValueType(Type type, Set<Type> visited) {
    assertValueType(type, visited, false);
  }

  private static void assertValueType(Type type, Set<Type> visited, boolean rejectHttp) {
    if (!visited.add(type)) return;
    if (type instanceof Class<?> value) {
      assertThat(value.getPackageName())
          .as("Storage callback in a public value contract: %s", value.getName())
          .isNotEqualTo("java.util.function");
      assertThat(value.getPackageName())
          .as("Repository projection in a public contract: %s", value.getName())
          .doesNotContain(".repository");
      if (rejectHttp)
        assertThat(ArchitecturePackages.isWeb(value.getPackageName()))
            .as("HTTP type in a public value contract: %s", value.getName())
            .isFalse();
      assertThat(value.isAnnotationPresent(Entity.class))
          .as("Entity in a public value contract: %s", value.getName())
          .isFalse();
      if (value.isArray()) assertValueType(value.getComponentType(), visited, rejectHttp);
      if (value.isRecord()) {
        for (var component : value.getRecordComponents()) {
          assertValueType(component.getGenericType(), visited, rejectHttp);
        }
      }
    } else if (type instanceof ParameterizedType container) {
      assertValueType(container.getRawType(), visited, rejectHttp);
      for (Type argument : container.getActualTypeArguments()) {
        assertValueType(argument, visited, rejectHttp);
      }
    } else if (type instanceof GenericArrayType array) {
      assertValueType(array.getGenericComponentType(), visited, rejectHttp);
    } else if (type instanceof TypeVariable<?> variable) {
      for (Type bound : variable.getBounds()) assertValueType(bound, visited, rejectHttp);
    } else if (type instanceof WildcardType wildcard) {
      for (Type bound : wildcard.getUpperBounds()) assertValueType(bound, visited, rejectHttp);
      for (Type bound : wildcard.getLowerBounds()) assertValueType(bound, visited, rejectHttp);
    }
  }
}
