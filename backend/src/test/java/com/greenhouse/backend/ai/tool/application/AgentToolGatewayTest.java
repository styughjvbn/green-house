package com.greenhouse.backend.ai.tool.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.greenhouse.backend.ai.api.AgentException;
import com.greenhouse.backend.farm.api.variety.VarietySearchApi;
import com.greenhouse.backend.sales.api.partner.BusinessPartnerQueryApi;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentToolGatewayTest {
  @Test
  void deniesUnknownExpiredAndClosedScopesAndEnforcesToolBudget() {
    var clock = mock(Clock.class);
    var now = Instant.parse("2026-10-15T00:00:00Z");
    when(clock.instant()).thenReturn(now);
    var varieties = mock(VarietySearchApi.class);
    var partners = mock(BusinessPartnerQueryApi.class);
    when(varieties.searchOptions("난", null, 10)).thenReturn(List.of());
    var gateway = new AgentToolGateway(varieties, partners, clock, 1, 60);
    var args = new AgentToolGateway.Arguments("난", null, null);
    assertThatThrownBy(() -> gateway.execute("Bearer invalid", "search_varieties", args))
        .isInstanceOf(AgentException.class);
    var scope = gateway.open("r", "worker");
    String authorization = "Bearer " + scope.token();
    assertThatThrownBy(() -> gateway.execute(authorization, "execute_sql", args))
        .isInstanceOf(AgentException.class);
    assertThat(gateway.execute(authorization, "search_varieties", args)).isEmpty();
    assertThatThrownBy(() -> gateway.execute(authorization, "search_varieties", args))
        .isInstanceOf(AgentException.class)
        .extracting("code")
        .isEqualTo("AI_TOOL_LIMIT");
    var expired = gateway.open("expired", "worker");
    when(clock.instant()).thenReturn(now.plusSeconds(61));
    assertThatThrownBy(() -> gateway.execute("Bearer " + expired.token(), "search_varieties", args))
        .isInstanceOf(AgentException.class);
    when(clock.instant()).thenReturn(now);
    scope.close();
    assertThatThrownBy(() -> gateway.execute(authorization, "search_varieties", args))
        .isInstanceOf(AgentException.class);
    expired.close();
    verify(varieties, times(1)).searchOptions("난", null, 10);
    verifyNoInteractions(partners);
  }
}
