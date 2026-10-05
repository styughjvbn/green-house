package com.greenhouse.backend.settlement.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.greenhouse.backend.partner.domain.PartnerType;
import com.greenhouse.backend.settlement.dto.PartnerSettlementSettingsResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SettlementCapabilitiesTest {
  @ParameterizedTest
  @EnumSource(PartnerType.class)
  void storedAutomationAndMonthlyPreferencesDoNotEnableExecution(PartnerType type) {
    var settings = new PartnerSettlementSettings(1L, type);
    settings.update(
        SettlementUnit.MONTHLY_BATCH,
        3,
        PaymentDayMode.CALENDAR_DAY,
        true,
        true,
        0L,
        List.of(),
        true,
        true,
        Map.of("enabled", true),
        null);
    var response = PartnerSettlementSettingsResponse.from(settings, type);
    assertThat(response.settlementUnit()).isEqualTo(SettlementUnit.MONTHLY_BATCH);
    assertThat(response.autoSettleEnabled()).isTrue();
    assertThat(response.capabilities().executableUnits())
        .containsExactly(
            type == PartnerType.AUCTION_HOUSE
                ? SettlementUnit.AUCTION_DATE
                : SettlementUnit.SALES_SLIP);
    assertThat(response.capabilities().autoMatching()).isFalse();
    assertThat(response.capabilities().autoSettlement()).isFalse();
    assertThat(response.capabilities().prepayment()).isFalse();
    assertThat(response.capabilities().creditAutoApply()).isFalse();
    assertThat(response.capabilities().ruleExecution()).isFalse();
  }

  @Test
  void callersCannotMutateSupportedUnits() {
    var units = new ArrayList<>(List.of(SettlementUnit.SALES_SLIP));
    var capabilities = new SettlementCapabilities(units, false, false, false, false, false);
    units.add(SettlementUnit.MONTHLY_BATCH);
    assertThat(capabilities.executableUnits()).containsExactly(SettlementUnit.SALES_SLIP);
    assertThatThrownBy(() -> capabilities.executableUnits().add(SettlementUnit.MONTHLY_BATCH))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
