package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.sales.api.partner.PartnerType;
import com.greenhouse.backend.sales.partner.domain.BusinessPartner;
import com.greenhouse.backend.sales.partner.repository.BusinessPartnerRepository;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("work-e2e")
class PartnerSettlementCapabilitiesPostgresE2ETest extends WorkE2ETestBase {
  @Autowired BusinessPartnerRepository partners;
  @Autowired JdbcTemplate jdbc;

  @ParameterizedTest
  @EnumSource(PartnerType.class)
  void persistsPreferencesWithoutClaimingOrPerformingUnsupportedExecution(PartnerType type)
      throws Exception {
    var partner =
        partners.saveAndFlush(new BusinessPartner("실행 계약 " + type, type, null, null, null, null));
    String path = "/api/business-partners/%d/settlement-settings".formatted(partner.getId());
    var initial = get(path);
    assertThat(initial.status()).isEqualTo(200);
    var executable = initial.data().path("capabilities").path("executableUnits");
    if (type == PartnerType.AUCTION_HOUSE) assertThat(executable).isEmpty();
    else assertThat(executable.get(0).asText()).isEqualTo("SALES_SLIP");
    var financialCounts = financialCounts();
    long audits = jdbc.queryForObject("select count(*) from audit_events", Long.class);
    var saved =
        putJson(
            path,
            """
        {"settlementUnit":"MONTHLY_BATCH", "paymentDelayDays":3, "paymentDayMode":"CALENDAR_DAY",
         "autoMatchEnabled":true, "autoSettleEnabled":true, "amountTolerance":1000,
         "depositorAliases":["보관"], "allowPrepayment":true, "creditAutoApplyEnabled":true,
         "ruleJson":{"enabled":true}, "memo":"기존 선호값"}
        """);
    assertThat(saved.status()).isEqualTo(200);
    var loaded = get(path);
    assertThat(loaded.status()).isEqualTo(200);
    assertThat(loaded.data()).isEqualTo(saved.data());
    assertThat(loaded.data().path("id")).isEqualTo(initial.data().path("id"));
    assertThat(loaded.data().path("settlementUnit").asText()).isEqualTo("MONTHLY_BATCH");
    for (String field :
        List.of(
            "autoMatchEnabled", "autoSettleEnabled", "allowPrepayment", "creditAutoApplyEnabled")) {
      assertThat(loaded.data().path(field).asBoolean()).as("stored %s", field).isTrue();
    }
    assertThat(loaded.data().path("ruleJson").path("enabled").asBoolean()).isTrue();
    assertThat(loaded.data().path("capabilities")).isEqualTo(initial.data().path("capabilities"));
    for (String capability :
        List.of(
            "autoMatching", "autoSettlement", "prepayment", "creditAutoApply", "ruleExecution")) {
      assertThat(loaded.data().path("capabilities").path(capability).isBoolean()).isTrue();
      assertThat(loaded.data().path("capabilities").path(capability).asBoolean()).isFalse();
    }
    assertThat(financialCounts()).isEqualTo(financialCounts);
    assertThat(jdbc.queryForObject("select count(*) from audit_events", Long.class))
        .isEqualTo(audits + 1);
  }

  private List<Long> financialCounts() {
    return List.of(
            "auction_proceeds",
            "partner_payment_events",
            "sales_slips",
            "partner_balance_summaries")
        .stream()
        .map(table -> jdbc.queryForObject("select count(*) from " + table, Long.class))
        .toList();
  }
}
