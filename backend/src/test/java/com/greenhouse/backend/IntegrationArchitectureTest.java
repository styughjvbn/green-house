package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.farm.orchid.integration.BoundaryWorkAdapterProbe;
import com.greenhouse.backend.work.api.BoundaryWorkApiProbe;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IntegrationArchitectureTest {
  // Concrete technology isolation without a port requires an exact class and reviewed reason.
  // No such integration implementation exists today; do not create a port to satisfy this test.
  private static final Map<String, String> REVIEWED_TECHNOLOGY_BOUNDARIES = Map.of();

  @Test
  void integrationImplementationsRequireAnExplicitBoundary() {
    for (var type : ModuleBoundaryInventoryTest.CLASSES) {
      if (!ArchitecturePackages.role(type.getPackageName()).equals("integration")
          || type.getName().contains("$")
          || type.isInterface()) continue;
      assertThat(
              implementsBoundary(type)
                  || REVIEWED_TECHNOLOGY_BOUNDARIES.containsKey(type.getName()))
          .as(
              "Optional integration requires an SPI, outbound port or reviewed technology: %s",
              type)
          .isTrue();
    }
    for (var entry : REVIEWED_TECHNOLOGY_BOUNDARIES.entrySet()) {
      assertThat(entry.getValue()).isNotBlank();
      assertThat(ModuleBoundaryInventoryTest.CLASSES.get(entry.getKey()).getPackageName())
          .contains(".integration");
    }
  }

  @Test
  void forwardingAnAllowedApiDoesNotJustifyAnIntegrationWrapper() {
    var classes =
        new ClassFileImporter()
            .importClasses(DirectApiForwarder.class, BoundaryWorkAdapterProbe.class);
    assertThat(implementsBoundary(classes.get(DirectApiForwarder.class))).isFalse();
    assertThat(implementsBoundary(classes.get(BoundaryWorkAdapterProbe.class))).isTrue();
  }

  private static boolean implementsBoundary(JavaClass type) {
    String owner = ArchitecturePackages.module(type.getPackageName());
    return type.getAllRawInterfaces().stream()
        .anyMatch(
            contract -> {
              String provider = ArchitecturePackages.module(contract.getPackageName());
              if (provider.isEmpty()) return false;
              if (!owner.equals(provider))
                return ArchitecturePackages.role(contract.getPackageName()).equals("spi")
                    && ArchitecturePackages.isModuleContract(contract.getPackageName());
              String path = contract.getPackageName();
              return path.endsWith(".application.port.out")
                  || path.contains(".application.port.out.");
            });
  }

  private static class DirectApiForwarder {
    String read(BoundaryWorkApiProbe api) {
      return api.value();
    }
  }
}
