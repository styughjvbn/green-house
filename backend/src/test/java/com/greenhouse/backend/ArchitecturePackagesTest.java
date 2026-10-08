package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ArchitecturePackagesTest {
  private static final String ROOT = "com.greenhouse.backend.";

  @Test
  void ownershipSurvivesAFeatureFirstMove() {
    for (String feature : new String[] {"document", "direct", "auction", "payment", "partner"}) {
      assertThat(ArchitecturePackages.contractOwner(ROOT + "sales.application." + feature))
          .isEqualTo("sales." + feature);
      assertThat(ArchitecturePackages.contractOwner(ROOT + "sales." + feature + ".api"))
          .isEqualTo("sales." + feature);
      assertThat(ArchitecturePackages.contractOwner(ROOT + "sales." + feature + ".spi"))
          .isEqualTo("sales." + feature);
    }
    assertThat(ArchitecturePackages.contractOwner(ROOT + "sales.api.document")).isEqualTo("sales");
  }

  @Test
  void movedStorageAndHttpDtosRemainInternal() {
    assertThat(ArchitecturePackages.role(ROOT + "sales.document.repository"))
        .isEqualTo("repository");
    assertThat(ArchitecturePackages.role(ROOT + "farm.mutation.ledger.repository"))
        .isEqualTo("repository");
    assertThat(ArchitecturePackages.role(ROOT + "farm.mutation.ledger.domain")).isEqualTo("domain");
    assertThat(ArchitecturePackages.isHttpDto(ROOT + "work.operation.web.dto")).isTrue();
    assertThat(ArchitecturePackages.isHttpDto(ROOT + "work.dto.operation")).isTrue();
    assertThat(ArchitecturePackages.isHttpDto(ROOT + "work.operation.web")).isFalse();
  }

  @Test
  void onlyKnownFeaturesAndMutationSubsystemsAreAccepted() {
    for (String name :
        new String[] {
          "farm.orchid.application",
          "work.effect.spi",
          "sales.document.web.dto",
          "farm.mutation.engine",
          "farm.mutation.ledger.domain",
          "farm.mutation.verification",
          "farm.api.orchid.command",
          "work.spi.effect",
          "farm.application.orchid.mutation"
        }) {
      assertThat(ArchitecturePackages.isValidLayout(ROOT + name)).as(name).isTrue();
    }
    for (String name :
        new String[] {
          "farm.unknown.application",
          "sales.settlement.domain",
          "farm.mutation.unknown",
          "work.effect",
          "sales.document.helper"
        }) {
      assertThat(ArchitecturePackages.isValidLayout(ROOT + name)).as(name).isFalse();
    }
  }

  @Test
  void featureApisDoNotBecomeTopLevelModuleContracts() {
    assertThat(ArchitecturePackages.isModuleContract(ROOT + "sales.document.api")).isFalse();
    assertThat(ArchitecturePackages.isModuleContract(ROOT + "sales.api.document")).isTrue();
    assertThat(ArchitecturePackages.isModuleContract(ROOT + "work.spi.effect")).isTrue();
    assertThat(ArchitecturePackages.isContract(ROOT + "work.effect.application")).isTrue();
    assertThat(ArchitecturePackages.isContract(ROOT + "work.effect.spi")).isTrue();
    assertThat(ArchitecturePackages.isContract(ROOT + "work.effect.domain")).isFalse();
  }
}
