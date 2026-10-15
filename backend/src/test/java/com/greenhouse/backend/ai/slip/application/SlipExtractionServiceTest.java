package com.greenhouse.backend.ai.slip.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.greenhouse.backend.ai.api.AgentException;
import com.greenhouse.backend.ai.slip.application.port.out.AgentRuntimePort;
import com.greenhouse.backend.ai.tool.application.AgentToolGateway;
import com.greenhouse.backend.farm.api.variety.VarietySearchApi;
import com.greenhouse.backend.sales.api.partner.BusinessPartnerQueryApi;
import com.greenhouse.backend.sales.api.partner.PartnerType;
import jakarta.validation.Validation;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class SlipExtractionServiceTest {
  @Test
  void acceptsOnlyCandidatesReturnedByThisRunAndKeepsRawValuesAndAmountWarning() {
    var varieties = mock(VarietySearchApi.class);
    var partners = mock(BusinessPartnerQueryApi.class);
    when(varieties.searchOptions("오독", null, 10))
        .thenReturn(List.of(new VarietySearchApi.Option(7L, "정식 품종", "온시디움", true)));
    when(partners.searchOptions("농장", false, 10))
        .thenReturn(
            List.of(new BusinessPartnerQueryApi.Option(3L, "정식 거래처", PartnerType.WHOLESALE, true)));
    var tools = new AgentToolGateway(varieties, partners, Clock.systemUTC(), 5, 120);
    try (var factory = Validation.buildDefaultValidatorFactory();
        var scope = tools.open("request", "worker")) {
      var service =
          new SlipExtractionService(mock(AgentRuntimePort.class), tools, factory.getValidator());
      tools.execute(
          "Bearer " + scope.token(),
          "search_varieties",
          new AgentToolGateway.Arguments("오독", null, null));
      tools.execute(
          "Bearer " + scope.token(),
          "search_partners",
          new AgentToolGateway.Arguments("농장", null, false));
      var raw =
          new SlipModelResult(
              "DIRECT",
              "2026-02-30",
              "원문 거래처",
              3L,
              null,
              List.of(
                  new SlipModelResult.Item(
                      "오독 품종", "오독 속", 7L, "A", "분", null, 2, 100, 999L, null)),
              List.of());
      var result = service.validate("request", raw, scope);
      assertThat(result.saleDate()).isNull();
      assertThat(result.partnerName()).isEqualTo("정식 거래처");
      assertThat(result.rawPartnerName()).isEqualTo("원문 거래처");
      assertThat(result.items().getFirst().itemName()).isEqualTo("정식 품종");
      assertThat(result.items().getFirst().rawItemName()).isEqualTo("오독 품종");
      assertThat(result.items().getFirst().calculatedAmount()).isEqualTo(200L);
      assertThat(result.items().getFirst().amount()).isEqualTo(999L);
      assertThat(result.warnings()).anyMatch(w -> w.contains("금액"));
      try (var other = tools.open("other", "worker")) {
        var rejected = service.validate("other", raw, other);
        assertThat(rejected.partnerId()).isNull();
        assertThat(rejected.items().getFirst().varietyId()).isNull();
      }
    }
    verify(varieties).searchOptions("오독", null, 10);
    verify(partners).searchOptions("농장", false, 10);
    verifyNoMoreInteractions(varieties, partners);
  }

  @Test
  void rejectsMalformedModelNumbersInsteadOfApplyingThemToTheForm() {
    var tools =
        new AgentToolGateway(
            mock(VarietySearchApi.class),
            mock(BusinessPartnerQueryApi.class),
            Clock.systemUTC(),
            5,
            120);
    try (var factory = Validation.buildDefaultValidatorFactory();
        var scope = tools.open("r", "a")) {
      var service =
          new SlipExtractionService(mock(AgentRuntimePort.class), tools, factory.getValidator());
      var raw =
          new SlipModelResult(
              "DIRECT",
              null,
              null,
              null,
              null,
              List.of(
                  new SlipModelResult.Item(null, null, null, null, null, null, -1, 0, null, null)),
              List.of());
      assertThatThrownBy(() -> service.validate("r", raw, scope))
          .isInstanceOf(AgentException.class)
          .extracting("code")
          .isEqualTo("AI_INVALID_RESULT");
    }
  }

  @Test
  void concurrentExtractionIsRejectedWhileTheFirstRunCanCallTools() throws Exception {
    var varieties = mock(VarietySearchApi.class);
    var partners = mock(BusinessPartnerQueryApi.class);
    var tools = new AgentToolGateway(varieties, partners, Clock.systemUTC(), 5, 120);
    when(varieties.searchOptions("난", null, 10)).thenReturn(List.of());
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    AgentRuntimePort runtime = mock(AgentRuntimePort.class);
    when(runtime.extract(anyString(), anyString(), anyString(), any()))
        .thenAnswer(
            call -> {
              String token = call.getArgument(1);
              tools.execute(
                  "Bearer " + token,
                  "search_varieties",
                  new AgentToolGateway.Arguments("난", null, null));
              entered.countDown();
              try {
                if (!release.await(5, TimeUnit.SECONDS))
                  throw new IllegalStateException("Test timed out");
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
              }
              return new SlipModelResult(null, null, null, null, null, List.of(), List.of());
            });
    var png = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", png);
    try (var factory = Validation.buildDefaultValidatorFactory();
        var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var service = new SlipExtractionService(runtime, tools, factory.getValidator());
      var first = executor.submit(() -> service.extract("worker", "image/png", png.toByteArray()));
      try {
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> service.extract("worker", "image/png", png.toByteArray()))
            .isInstanceOf(AgentException.class)
            .extracting("code")
            .isEqualTo("AI_BUSY");
      } finally {
        release.countDown();
      }
      assertThat(first.get(5, TimeUnit.SECONDS).items()).isEmpty();
    }
  }
}
