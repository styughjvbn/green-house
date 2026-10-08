package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.greenhouse.backend.farm.application.BoundaryFarmProbe;
import com.greenhouse.backend.farm.orchid.integration.BoundaryWorkAdapterProbe;
import com.greenhouse.backend.work.api.BoundaryWorkApiProbe;
import com.greenhouse.backend.work.application.BoundaryWorkProbe;
import com.greenhouse.backend.work.spi.BoundaryWorkSpiProbe;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CrossModuleApplicationApiInspectionTest {
  @Test
  void explicitApiMembersAndImplementedSpiStillRequireReview() {
    var classes =
        new ClassFileImporter()
            .importClasses(
                BoundaryWorkAdapterProbe.class,
                BoundaryWorkApiProbe.class,
                BoundaryWorkSpiProbe.class);
    assertThat(CrossModuleApplicationApiInspection.contracts(classes))
        .contains(
            "TYPE\t" + BoundaryWorkApiProbe.class.getName(),
            "TYPE\t" + BoundaryWorkSpiProbe.class.getName(),
            "CONSTRUCTOR\t" + BoundaryWorkApiProbe.class.getName() + ".<init>(java.lang.String)",
            "METHOD\t" + BoundaryWorkApiProbe.class.getName() + ".approvedOperation()",
            "METHOD\t" + BoundaryWorkApiProbe.class.getName() + ".value()");
  }

  @Test
  void anApprovedApplicationTypeDoesNotAuthorizeANewHelperMethodOrReference() {
    var classes =
        new ClassFileImporter().importClasses(BoundaryFarmProbe.class, BoundaryWorkProbe.class);
    var actual = CrossModuleApplicationApiInspection.contracts(classes);
    var approved = Set.of("TYPE\t" + BoundaryWorkProbe.class.getName());
    assertThat(actual)
        .contains("METHOD\t" + BoundaryWorkProbe.class.getName() + ".internalHelper()");
    assertThatThrownBy(() -> assertThat(actual).containsExactlyInAnyOrderElementsOf(approved))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("internalHelper");
  }

  @Test
  void aMethodReferenceAloneStillRequiresApproval() {
    var classes =
        new ClassFileImporter()
            .importClasses(BoundaryFarmProbe.ReferenceOnly.class, BoundaryWorkProbe.class);
    assertThat(CrossModuleApplicationApiInspection.contracts(classes))
        .contains("METHOD\t" + BoundaryWorkProbe.class.getName() + ".internalHelper()");
  }

  @Test
  void sameModuleHelpersDoNotBecomeCrossModuleContracts() {
    var classes = new ClassFileImporter().importClasses(BoundaryWorkProbe.class);
    assertThat(CrossModuleApplicationApiInspection.contracts(classes)).isEmpty();
  }
}
