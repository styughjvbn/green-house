package com.greenhouse.backend.ai.slip.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.greenhouse.backend.ai.api.AgentException;
import com.greenhouse.backend.ai.slip.application.port.out.AgentRuntimePort;
import com.greenhouse.backend.ai.tool.application.AgentToolGateway;
import com.greenhouse.backend.farm.api.variety.VarietySearchApi;
import com.greenhouse.backend.sales.api.document.SalesType;
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
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;

class SlipExtractionServiceTest {
  @Test
  void preservesUncertainValuesAndReturnsSpecificReviewTargets() {
    var tools =
        new AgentToolGateway(
            mock(VarietySearchApi.class),
            mock(BusinessPartnerQueryApi.class),
            Clock.systemUTC(),
            5,
            120);
    try (var factory = Validation.buildDefaultValidatorFactory();
        var scope = tools.open("r", "worker")) {
      var service =
          new SlipExtractionService(mock(AgentRuntimePort.class), tools, factory.getValidator());
      var item = new SlipModelResult.Item("하니문", "속", null, "중", 10, 2, 20, 100, 2000L, null);
      var review = new SlipFieldReview(SlipFieldReview.Field.QUANTITY, 0, "수량 끝자리 불명확");
      var raw =
          new SlipModelResult(
              "DIRECT",
              "2026-10-15",
              "거래처",
              null,
              null,
              List.of(item),
              List.of(),
              90,
              1024,
              1,
              List.of(review, review));
      var result = service.validate("r", raw, scope);
      assertThat(result.items().getFirst().quantity()).isEqualTo(20);
      assertThat(result.reviewFields()).contains(review);
      assertThat(
              result.reviewFields().stream()
                  .filter(r -> r.field() == SlipFieldReview.Field.QUANTITY))
          .hasSize(1);
      assertThat(result.reviewFields())
          .anySatisfy(
              r -> {
                assertThat(r.field()).isEqualTo(SlipFieldReview.Field.PARTNER);
                assertThat(r.itemIndex()).isNull();
              });
      assertThat(result.reviewFields())
          .anySatisfy(
              r -> {
                assertThat(r.field()).isEqualTo(SlipFieldReview.Field.ITEM_NAME);
                assertThat(r.itemIndex()).isZero();
              });
    }
  }

  @Test
  void rejectsReviewTargetsOutsideTheirSlipOrItemScope() {
    var tools =
        new AgentToolGateway(
            mock(VarietySearchApi.class),
            mock(BusinessPartnerQueryApi.class),
            Clock.systemUTC(),
            5,
            120);
    try (var factory = Validation.buildDefaultValidatorFactory();
        var scope = tools.open("r", "worker")) {
      var service =
          new SlipExtractionService(mock(AgentRuntimePort.class), tools, factory.getValidator());
      for (var review :
          List.of(
              new SlipFieldReview(SlipFieldReview.Field.SALE_DATE, 0, "날짜 확인 필요"),
              new SlipFieldReview(SlipFieldReview.Field.QUANTITY, null, "수량 확인 필요"),
              new SlipFieldReview(SlipFieldReview.Field.QUANTITY, 0, "수량 확인 필요"),
              new SlipFieldReview(SlipFieldReview.Field.SALE_DATE, null, " "))) {
        var raw =
            new SlipModelResult(
                null, null, null, null, null, List.of(), List.of(), 90, 1024, 1, List.of(review));
        assertThatThrownBy(() -> service.validate("r", raw, scope))
            .isInstanceOf(AgentException.class)
            .extracting("code")
            .isEqualTo("AI_INVALID_RESULT");
      }
    }
  }

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
                  new SlipModelResult.Item("오독 품종", "오독 속", 7L, "A", 10, null, 2, 100, 999L, null)),
              List.of(),
              90,
              1024,
              1,
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
              List.of(),
              90,
              1024,
              1,
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
              return new SlipModelResult(
                  null, null, null, null, null, List.of(), List.of(), 90, 1024, 1, List.of());
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

  @Test
  void validatesSelfRatedConfidenceAndPassesImageResolutionMetadataToReview() {
    var tools =
        new AgentToolGateway(
            mock(VarietySearchApi.class),
            mock(BusinessPartnerQueryApi.class),
            Clock.systemUTC(),
            5,
            120);
    try (var factory = Validation.buildDefaultValidatorFactory();
        var scope = tools.open("r", "worker")) {
      var service =
          new SlipExtractionService(mock(AgentRuntimePort.class), tools, factory.getValidator());
      var valid =
          new SlipModelResult(
              null, null, null, null, null, List.of(), List.of(), 65, 2048, 2, List.of());
      var result = service.validate("r", valid, scope);
      assertThat(result.confidence()).isEqualTo(65);
      assertThat(result.imageLongEdge()).isEqualTo(2048);
      assertThat(result.attempts()).isEqualTo(2);
      for (Integer confidence : Arrays.asList(-1, 101, null)) {
        var invalid =
            new SlipModelResult(
                null, null, null, null, null, List.of(), List.of(), confidence, 1024, 1, List.of());
        assertThatThrownBy(() -> service.validate("r", invalid, scope))
            .isInstanceOf(AgentException.class)
            .extracting("code")
            .isEqualTo("AI_INVALID_RESULT");
      }
    }
  }

  static Stream<Arguments> partnerTypes() {
    return Arrays.stream(PartnerType.values())
        .flatMap(
            partnerType ->
                Arrays.asList("DIRECT", "AUCTION", null).stream()
                    .map(modelType -> Arguments.of(partnerType, modelType)));
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.MethodSource("partnerTypes")
  void preservesPriceBasedSalesTypeAndRejectsIncompatiblePartners(
      PartnerType partnerType, String modelType) {
    var partners = mock(BusinessPartnerQueryApi.class);
    when(partners.searchOptions("거래처", null, 10))
        .thenReturn(List.of(new BusinessPartnerQueryApi.Option(3L, "등록 거래처", partnerType, true)));
    var tools =
        new AgentToolGateway(mock(VarietySearchApi.class), partners, Clock.systemUTC(), 5, 120);
    try (var factory = Validation.buildDefaultValidatorFactory();
        var scope = tools.open("r", "worker")) {
      tools.execute(
          "Bearer " + scope.token(),
          "search_partners",
          new AgentToolGateway.Arguments("거래처", null, null));
      var service =
          new SlipExtractionService(mock(AgentRuntimePort.class), tools, factory.getValidator());
      var raw =
          new SlipModelResult(
              modelType, null, "사진 원문", 3L, null, List.of(), List.of(), 90, 1024, 1, List.of());
      var result = service.validate("r", raw, scope);
      var expected = modelType == null ? null : SalesType.valueOf(modelType);
      assertThat(result.salesType()).isEqualTo(expected);
      boolean incompatible =
          expected != null
              && (partnerType == PartnerType.AUCTION_HOUSE) != (expected == SalesType.AUCTION);
      assertThat(result.partnerId()).isEqualTo(incompatible ? null : 3L);
      assertThat(result.rawPartnerName()).isEqualTo("사진 원문");
      if (incompatible) assertThat(result.warnings()).anyMatch(w -> w.contains("거래처 유형 불일치"));
      assertThat(
              result.reviewFields().stream()
                  .anyMatch(r -> r.field() == SlipFieldReview.Field.SALES_TYPE))
          .isEqualTo(modelType == null);
    }
  }

  @Test
  void preservesPriceBasedSalesTypeForUnmatchedInactiveOrUnselectedPartners() {
    var partners = mock(BusinessPartnerQueryApi.class);
    when(partners.searchOptions("거래처", null, 10))
        .thenReturn(
            List.of(
                new BusinessPartnerQueryApi.Option(
                    3L, "비활성 거래처", PartnerType.AUCTION_HOUSE, false)));
    var tools =
        new AgentToolGateway(mock(VarietySearchApi.class), partners, Clock.systemUTC(), 5, 120);
    try (var factory = Validation.buildDefaultValidatorFactory();
        var scope = tools.open("r", "worker")) {
      tools.execute(
          "Bearer " + scope.token(),
          "search_partners",
          new AgentToolGateway.Arguments("거래처", null, null));
      var service =
          new SlipExtractionService(mock(AgentRuntimePort.class), tools, factory.getValidator());
      for (Long id : Arrays.asList(3L, 99L, null)) {
        var result =
            service.validate(
                "r",
                new SlipModelResult(
                    "AUCTION", null, "사진 원문", id, null, List.of(), List.of(), 90, 1024, 1,
                    List.of()),
                scope);
        assertThat(result.partnerId()).isNull();
        assertThat(result.salesType()).isEqualTo(SalesType.AUCTION);
        assertThat(result.partnerName()).isEqualTo("사진 원문");
      }
    }
  }
}
