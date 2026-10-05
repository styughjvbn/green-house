package com.greenhouse.backend;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.farm.application.orchid.OrchidGroupReader;
import com.greenhouse.backend.work.application.correction.WorkCorrectionPort;
import com.greenhouse.backend.work.application.operation.WorkOperationSupport;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Entity;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PublicApplicationContractArchitectureTest {

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

  private void assertValueType(Type type, Set<Type> visited) {
    if (!visited.add(type)) return;
    if (type instanceof Class<?> value) {
      assertThat(value.getPackageName())
          .as("Storage callback in a public value contract: %s", value.getName())
          .isNotEqualTo("java.util.function");
      assertThat(value.isAnnotationPresent(Entity.class))
          .as("Entity in a public value contract: %s", value.getName())
          .isFalse();
      if (value.isArray()) assertValueType(value.getComponentType(), visited);
      if (value.isRecord()) {
        for (var component : value.getRecordComponents()) {
          assertValueType(component.getGenericType(), visited);
        }
      }
    } else if (type instanceof ParameterizedType container) {
      assertValueType(container.getRawType(), visited);
      for (Type argument : container.getActualTypeArguments()) {
        assertValueType(argument, visited);
      }
    } else if (type instanceof GenericArrayType array) {
      assertValueType(array.getGenericComponentType(), visited);
    } else if (type instanceof WildcardType wildcard) {
      for (Type bound : wildcard.getUpperBounds()) assertValueType(bound, visited);
      for (Type bound : wildcard.getLowerBounds()) assertValueType(bound, visited);
    }
  }
}
