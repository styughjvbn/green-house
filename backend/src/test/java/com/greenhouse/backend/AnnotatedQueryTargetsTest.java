package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AnnotatedQueryTargetsTest {
  @Test
  void schemaAndQuotedRootsCannotHideForeignTables() {
    assertThat(
            AnnotatedQueryTargets.names(
                """
        SELECT s.id FROM "public" . "sales_slips" s JOIN public.auction_shipments a ON true
        JOIN ORCHID_GROUPS g ON true
        """,
                true))
        .containsExactly("sales_slips", "auction_shipments", "orchid_groups");
  }

  @Test
  void detectsWriteAndCountQueryRoots() {
    assertThat(AnnotatedQueryTargets.names("UPDATE public.orchid_groups SET quantity=0", true))
        .containsExactly("orchid_groups");
    assertThat(AnnotatedQueryTargets.names("INSERT INTO \"audit_events\" (id) VALUES (1)", true))
        .containsExactly("audit_events");
    assertThat(AnnotatedQueryTargets.names("SELECT count(*) FROM public.sales_slips", true))
        .containsExactly("sales_slips");
  }

  @Test
  void qualifiedJpqlUsesTheEntityNameWithoutChangingItsCase() {
    assertThat(
            AnnotatedQueryTargets.names(
                "select g from com.greenhouse.backend.farm.domain.orchid.OrchidGroup g join g.variety v",
                false))
        .contains("OrchidGroup")
        .doesNotContain("Variety");
  }
}
