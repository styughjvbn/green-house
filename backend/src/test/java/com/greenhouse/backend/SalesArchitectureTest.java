package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class SalesArchitectureTest {
  @Test
  void derivedSettlementRuntimeRemainsRetired() {
    assertThat(
            CLASSES.stream()
                .filter(
                    type ->
                        type.getName().startsWith(ROOT)
                            && type.getName().contains(".auction.settlement."))
                .toList())
        .isEmpty();
  }

  private static final String ROOT = "com.greenhouse.backend.sales.";
  private static final JavaClasses CLASSES = ModuleBoundaryInventoryTest.CLASSES;
  private static final Map<String, Set<String>> GRAPH =
      Map.of(
          "document", Set.of("partner", "payment"),
          "direct", Set.of("document", "payment", "partner"),
          "auction", Set.of("document", "payment", "partner"),
          "payment", Set.of("partner"),
          "partner", Set.of());

  @Test
  void internalStorageNeverEscapesItsOwner() {
    for (JavaClass origin : CLASSES) {
      for (var dependency : origin.getDirectDependenciesFromSelf()) {
        JavaClass target = dependency.getTargetClass();
        String owner = feature(target);
        if (owner.isEmpty() || owner.equals(feature(origin))) continue;
        boolean storage =
            target.isAnnotatedWith("jakarta.persistence.Entity")
                || ArchitecturePackages.role(target.getPackageName()).equals("repository")
                || (target.getSimpleName().startsWith("Q")
                    && target.isAssignableTo("com.querydsl.core.types.EntityPath"));
        assertThat(storage)
            .as("Storage escaped %s: %s -> %s", owner, origin.getName(), target.getName())
            .isFalse();
      }
    }
  }

  @Test
  void implementationDependenciesFollowTheAcyclicFeatureGraph() {
    for (var entry : GRAPH.entrySet()) {
      assertThat(reaches(entry.getKey(), entry.getKey(), new HashSet<>())).isFalse();
    }
    for (JavaClass origin : CLASSES) {
      String owner = feature(origin);
      // HTTP controllers compose public contracts; they never own storage or transactions.
      if (owner.isEmpty() || ArchitecturePackages.isWeb(origin.getPackageName())) continue;
      for (var dependency : origin.getDirectDependenciesFromSelf()) {
        String target = feature(dependency.getTargetClass());
        if (target.isEmpty() || owner.equals(target)) continue;
        assertThat(ArchitecturePackages.role(dependency.getTargetClass().getPackageName()))
            .as("Sales feature consumers require an API/SPI: %s", dependency.getDescription())
            .isNotEqualTo("application");
        assertThat(GRAPH.get(owner))
            .as("Forbidden sales dependency: %s", dependency.getDescription())
            .contains(target);
      }
    }
  }

  @Test
  void documentDoesNotCallConcreteDirectAuctionOrPaymentServices() {
    for (JavaClass origin : CLASSES) {
      if (!feature(origin).equals("document")
          || ArchitecturePackages.isWeb(origin.getPackageName())) continue;
      for (var dependency : origin.getDirectDependenciesFromSelf()) {
        JavaClass target = dependency.getTargetClass();
        if (!Set.of("direct", "auction", "payment").contains(feature(target))) continue;
        assertThat(target.isInterface())
            .as("Document requires a port, not %s", target.getName())
            .isTrue();
      }
    }
  }

  @Test
  void queriesDoNotReadAnotherSalesOwnersEntitiesOrTables() {
    var entities =
        CLASSES.stream()
            .filter(type -> type.isAnnotatedWith("jakarta.persistence.Entity"))
            .collect(Collectors.toMap(JavaClass::getSimpleName, Function.identity()));
    var tables =
        entities.values().stream()
            .filter(type -> type.isAnnotatedWith("jakarta.persistence.Table"))
            .collect(
                Collectors.toMap(
                    type ->
                        type.getAnnotationOfType("jakarta.persistence.Table")
                            .get("name")
                            .orElseThrow()
                            .toString(),
                    Function.identity()));
    for (JavaClass origin : CLASSES) {
      String owner = feature(origin);
      if (owner.isEmpty()) continue;
      for (var method : origin.getMethods()) {
        if (!method.isAnnotatedWith("org.springframework.data.jpa.repository.Query")) continue;
        var query = method.getAnnotationOfType("org.springframework.data.jpa.repository.Query");
        boolean nativeQuery = Boolean.TRUE.equals(query.get("nativeQuery").orElse(false));
        for (String attribute : Set.of("value", "countQuery")) {
          for (String name :
              AnnotatedQueryTargets.names(
                  query.get(attribute).orElse("").toString(), nativeQuery)) {
            JavaClass target = (nativeQuery ? tables : entities).get(name);
            if (target == null || feature(target).isEmpty()) continue;
            assertThat(feature(target))
                .as("Foreign Sales storage in %s", method.getFullName())
                .isEqualTo(owner);
          }
        }
      }
    }
  }

  @Test
  void paymentTargetAdaptersMustJoinTheUseCaseTransaction() throws ClassNotFoundException {
    for (JavaClass type : CLASSES) {
      if (type.isInterface()
          || !type.isAssignableTo("com.greenhouse.backend.sales.payment.spi.PaymentTargetPort"))
        continue;
      var annotation = Class.forName(type.getName()).getAnnotation(Transactional.class);
      assertThat(annotation)
          .as("Payment target requires the caller transaction: %s", type.getName())
          .isNotNull();
      assertThat(annotation.propagation()).isEqualTo(Propagation.MANDATORY);
      // Resolve concrete generic target responses as well as the port's type-variable bounds.
      for (var contract : Class.forName(type.getName()).getGenericInterfaces()) {
        PublicApplicationContractArchitectureTest.assertValueType(contract, new HashSet<>());
      }
    }
  }

  @Test
  void documentPaidAmountIsOnlyInitializedOrAssignedAsAProjection() {
    var document = CLASSES.get(ROOT + "document.domain.SalesSlip");
    for (var access : document.getFieldAccessesFromSelf()) {
      if (!access.getTarget().getName().equals("paidAmount")
          || access.getAccessType() != JavaFieldAccess.AccessType.SET) continue;
      assertThat(Set.of("<init>", "applyFinancialProjection"))
          .contains(access.getOrigin().getName());
    }
  }

  private static String feature(JavaClass type) {
    if (!type.getPackageName().startsWith(ROOT)) return "";
    return ArchitecturePackages.feature(type.getPackageName());
  }

  private boolean reaches(String current, String target, Set<String> visited) {
    if (!visited.add(current)) return false;
    for (String next : GRAPH.get(current)) {
      if (next.equals(target) || reaches(next, target, visited)) return true;
    }
    return false;
  }
}
