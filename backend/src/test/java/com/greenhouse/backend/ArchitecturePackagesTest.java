package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ArchitecturePackagesTest {
  private static final String ROOT = "com.greenhouse.backend.";

  @Test
  void ownershipDistinguishesModuleContractsFromFeatureContracts() {
    for (String feature : new String[] {"document", "direct", "auction", "payment", "partner"}) {
      assertThat(ArchitecturePackages.contractOwner(ROOT + "sales." + feature + ".application"))
          .isEqualTo("sales." + feature);
      assertThat(ArchitecturePackages.contractOwner(ROOT + "sales." + feature + ".api"))
          .isEqualTo("sales." + feature);
      assertThat(ArchitecturePackages.contractOwner(ROOT + "sales." + feature + ".spi"))
          .isEqualTo("sales." + feature);
    }
    assertThat(ArchitecturePackages.contractOwner(ROOT + "sales.api.document"))
        .isEqualTo("sales.document");
    for (String feature : new String[] {"document", "direct", "auction", "payment", "partner"}) {
      assertThat(ArchitecturePackages.feature(ROOT + "sales.api." + feature)).isEqualTo(feature);
      assertThat(ArchitecturePackages.feature(ROOT + "sales.spi." + feature)).isEqualTo(feature);
      assertThat(ArchitecturePackages.isModuleContract(ROOT + "sales.api." + feature)).isTrue();
    }
  }

  @Test
  void movedStorageAndHttpDtosRemainInternal() {
    assertThat(ArchitecturePackages.role(ROOT + "sales.document.repository"))
        .isEqualTo("repository");
    assertThat(ArchitecturePackages.role(ROOT + "farm.mutation.ledger.repository"))
        .isEqualTo("repository");
    assertThat(ArchitecturePackages.role(ROOT + "farm.mutation.ledger.domain")).isEqualTo("domain");
    assertThat(ArchitecturePackages.isHttpDto(ROOT + "work.operation.web.dto")).isTrue();
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
          "audit.application",
          "analytics.repository"
        }) {
      assertThat(ArchitecturePackages.isValidLayout(ROOT + name)).as(name).isTrue();
    }
    for (String name :
        new String[] {
          "farm.unknown.application",
          "sales.settlement.domain",
          "farm.mutation.unknown",
          "work.effect",
          "sales.document.helper",
          "farm.application.orchid.mutation",
          "farm.application",
          "farm.domain.orchid",
          "work.repository.target",
          "work.dto.operation",
          "sales.application.document",
          "sales.controller",
          "farm.web",
          "work.integration"
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
