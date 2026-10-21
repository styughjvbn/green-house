package com.greenhouse.backend.ai.slip.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.greenhouse.backend.ai.api.AgentException;
import com.greenhouse.backend.ai.slip.application.port.out.AgentRuntimePort;
import com.greenhouse.backend.ai.tool.application.AgentToolGateway;
import com.greenhouse.backend.farm.api.variety.VarietySearchApi;
import com.greenhouse.backend.sales.api.partner.BusinessPartnerInfo;
import com.greenhouse.backend.sales.api.partner.BusinessPartnerQueryApi;
import com.greenhouse.backend.sales.api.partner.PartnerType;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import kr.co.slip.api.DocumentForm;
import org.junit.jupiter.api.Test;

class SlipConversationServiceTest {
  private final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();

  @org.junit.jupiter.api.AfterEach
  void closeFactory() {
    factory.close();
  }

  private final AgentRuntimePort runtime = mock(AgentRuntimePort.class);
  private final VarietySearchApi varieties = mock(VarietySearchApi.class);
  private final BusinessPartnerQueryApi partners = mock(BusinessPartnerQueryApi.class);
  private final Clock clock = mock(Clock.class);
  private final Instant now = Instant.parse("2026-10-15T00:00:00Z");

  private SlipConversationService service() {
    return service(0);
  }

  private SlipConversationService service(long defaultPartnerId) {
    when(clock.instant()).thenReturn(now);
    var tools = new AgentToolGateway(mock(VarietySearchApi.class), partners, clock, 20, 120);
    return new SlipConversationService(
        runtime,
        tools,
        new SlipExtractionService(runtime, tools, factory.getValidator(), defaultPartnerId),
        clock,
        varieties);
  }

  private byte[] image(int rgb) throws Exception {
    var image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
    image.setRGB(0, 0, rgb);
    var out = new ByteArrayOutputStream();
    ImageIO.write(image, "png", out);
    return out.toByteArray();
  }

  private SlipConversationInput.Photo photo(String id) throws Exception {
    return new SlipConversationInput.Photo(id, "image/png", image(0));
  }

  private SlipModelResult result(String name) {
    return new SlipModelResult(
        null, null, name, null, null, List.of(), List.of(), 90, 1024, 1, List.of());
  }

  @Test
  void progressIsOwnedRequestScopedAndTracksSearchesUntilTheRequestEnds() throws Exception {
    when(clock.instant()).thenReturn(now);
    var gateway = new AgentToolGateway(varieties, partners, clock, 20, 120);
    var service =
        new SlipConversationService(
            runtime,
            gateway,
            new SlipExtractionService(runtime, gateway, factory.getValidator(), 0),
            clock,
            varieties);
    String session = service.open("worker").sessionId();
    assertThat(service.progress("worker", session, "r").stage())
        .isEqualTo(SlipConversationProgress.Stage.WAITING);
    assertThatThrownBy(() -> service.progress("other", session, "r"))
        .isInstanceOf(AgentException.class)
        .extracting("code")
        .isEqualTo("AI_SESSION_EXPIRED");
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    when(partners.searchOptions("대구 싱싱", null, 10))
        .thenAnswer(
            call -> {
              entered.countDown();
              if (!release.await(5, TimeUnit.SECONDS))
                throw new IllegalStateException("test timeout");
              return List.of();
            });
    when(runtime.extractConversation(eq("r"), eq(session), anyString(), any()))
        .thenAnswer(
            call -> {
              gateway.execute(
                  "Bearer " + call.getArgument(2, String.class),
                  "search_partners",
                  new AgentToolGateway.Arguments("대구 싱싱", null, null));
              gateway.reportSummary(
                  "Bearer " + call.getArgument(2, String.class), "전표의 품종 후보를 확인합니다.");
              var after = service.progress("worker", session, "r");
              assertThat(after.activity().searching()).isFalse();
              assertThat(after.activity().summary()).isEqualTo("전표의 품종 후보를 확인합니다.");
              assertThat(after.activity().latestLookup().query()).isEqualTo("대구 싱싱");
              return new SlipConversationModelResult(
                  List.of(new SlipConversationModelResult.Photo("p", null)));
            });
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      var task =
          pool.submit(
              () ->
                  service.extract(
                      "worker", session, new SlipConversationInput(List.of(photo("p"))), "r"));
      try {
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        var progress = service.progress("worker", session, "r");
        assertThat(progress.stage()).isEqualTo(SlipConversationProgress.Stage.READING);
        assertThat(progress.photoIds()).containsExactly("p");
        assertThat(progress.activity().searching()).isTrue();
        assertThat(progress.activity().latestLookup().tool()).isEqualTo("search_partners");
        assertThat(progress.activity().latestLookup().query()).isEqualTo("대구 싱싱");
        assertThatThrownBy(() -> service.progress("worker", session, "other"))
            .isInstanceOf(AgentException.class)
            .extracting("code")
            .isEqualTo("AI_BUSY");
      } finally {
        release.countDown();
      }
      task.get(5, TimeUnit.SECONDS);
    }
    assertThat(service.progress("worker", session, "r").stage())
        .isEqualTo(SlipConversationProgress.Stage.DONE);
    assertThat(service.progress("worker", session, "r").activity()).isNull();
    when(clock.instant()).thenReturn(now.plusSeconds(1801));
    assertThatThrownBy(() -> service.progress("worker", session, "r"))
        .isInstanceOf(AgentException.class);
  }

  @Test
  void cancellationIsOwnedAndPreventsRequestsThatHaveNotStarted() throws Exception {
    var service = service();
    String session = service.open("worker").sessionId();
    assertThatThrownBy(() -> service.cancel("other", session, "r"))
        .isInstanceOf(AgentException.class)
        .extracting("code")
        .isEqualTo("AI_SESSION_EXPIRED");
    verify(runtime, never()).cancelRequest(anyString(), anyString());
    service.cancel("worker", session, "r");
    assertThatThrownBy(
            () ->
                service.extract(
                    "worker", session, new SlipConversationInput(List.of(photo("p"))), "r"))
        .isInstanceOf(AgentException.class)
        .extracting("code")
        .isEqualTo("AI_CANCELLED");
    verify(runtime, never()).extractConversation(anyString(), anyString(), anyString(), any());
  }

  @Test
  void cancellationWaitsForTheActiveRequestAndKeepsTheConversationReusable() throws Exception {
    var service = service();
    String session = service.open("worker").sessionId();
    var entered = new CountDownLatch(1);
    var stopped = new CountDownLatch(1);
    when(runtime.extractConversation(eq("r"), eq(session), anyString(), any()))
        .thenAnswer(
            call -> {
              entered.countDown();
              if (!stopped.await(5, TimeUnit.SECONDS))
                throw new IllegalStateException("test timeout");
              throw new AgentException(409, "AI_CANCELLED", "중지");
            });
    doAnswer(
            call -> {
              stopped.countDown();
              return null;
            })
        .when(runtime)
        .cancelRequest(session, "r");
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      var task =
          pool.submit(
              () ->
                  service.extract(
                      "worker", session, new SlipConversationInput(List.of(photo("p"))), "r"));
      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
      service.cancel("worker", session, "r");
      assertThatThrownBy(() -> task.get(5, TimeUnit.SECONDS))
          .hasCauseInstanceOf(AgentException.class);
      when(runtime.extractConversation(eq("next"), eq(session), anyString(), any()))
          .thenReturn(
              new SlipConversationModelResult(
                  List.of(new SlipConversationModelResult.Photo("p", null))));
      assertThat(
              service
                  .extract(
                      "worker", session, new SlipConversationInput(List.of(photo("p"))), "next")
                  .photos())
          .hasSize(1);
      verify(runtime, times(1)).openSession(eq(session), any());
    }
  }

  @Test
  void tenPhotosAndIndividualRereadsShareOneOwnedConversationAndResultsFollowIds()
      throws Exception {
    var service = service();
    var names = List.of("하니문", "프라그랑스");
    when(varieties.findActiveNamesWithLinkedGroups()).thenReturn(names);
    String session = service.open("worker").sessionId();
    when(runtime.extractConversation(anyString(), eq(session), anyString(), any()))
        .thenAnswer(
            call -> {
              SlipConversationInput input = call.getArgument(3);
              var rows = new ArrayList<SlipConversationModelResult.Photo>();
              for (var photo : input.photos())
                rows.add(
                    new SlipConversationModelResult.Photo(
                        photo.photoId(), result(photo.photoId())));
              Collections.reverse(rows);
              return new SlipConversationModelResult(rows);
            });
    var photos = new ArrayList<SlipConversationInput.Photo>();
    for (int i = 0; i < 10; i++) photos.add(photo("photo-" + i));
    var batch = service.extract("worker", session, new SlipConversationInput(photos));
    assertThat(batch.photos()).hasSize(10);
    for (var row : batch.photos()) assertThat(row.result().partnerName()).isEqualTo(row.photoId());
    service.extract("worker", session, new SlipConversationInput(List.of(photos.get(0))));
    service.extract("worker", session, new SlipConversationInput(List.of(photo("photo-0"))));
    verify(runtime).openSession(session, names);
    verify(varieties, times(1)).findActiveNamesWithLinkedGroups();
    verify(runtime, times(3)).extractConversation(anyString(), eq(session), anyString(), any());
  }

  @Test
  void batchesCanAccumulateMoreThanTenDocumentsButEachRequestStillHasTenAtMost() throws Exception {
    var service = service();
    String session = service.open("worker").sessionId();
    when(runtime.extractConversation(anyString(), eq(session), anyString(), any()))
        .thenAnswer(
            call -> {
              SlipConversationInput input = call.getArgument(3);
              return new SlipConversationModelResult(
                  input.photos().stream()
                      .map(photo -> new SlipConversationModelResult.Photo(photo.photoId(), null))
                      .toList());
            });
    var photos = new ArrayList<SlipConversationInput.Photo>();
    for (int i = 0; i < 21; i++) photos.add(photo("photo-" + i));
    for (int offset = 0; offset < photos.size(); offset += 10) {
      var batch = photos.subList(offset, Math.min(offset + 10, photos.size()));
      assertThat(service.extract("worker", session, new SlipConversationInput(batch)).photos())
          .extracting(SlipConversationResult.Photo::photoId)
          .containsExactlyElementsOf(
              batch.stream().map(SlipConversationInput.Photo::photoId).toList());
    }
    service.extract("worker", session, new SlipConversationInput(List.of(photos.get(0))));
    assertThatThrownBy(
            () ->
                service.extract(
                    "worker", session, new SlipConversationInput(photos.subList(0, 11))))
        .isInstanceOf(AgentException.class)
        .extracting("code")
        .isEqualTo("AI_INVALID_REQUEST");
    var changed = new SlipConversationInput.Photo("photo-20", "image/png", image(0xffffff));
    assertThatThrownBy(
            () -> service.extract("worker", session, new SlipConversationInput(List.of(changed))))
        .isInstanceOf(AgentException.class)
        .extracting("code")
        .isEqualTo("AI_PHOTO_ID_CONFLICT");
    verify(runtime, times(4)).extractConversation(anyString(), eq(session), anyString(), any());
    verify(runtime, times(1)).openSession(eq(session), any());
  }

  @Test
  void documentFormsFollowPhotoIdsAndCannotChangeWithinAnExistingPhotoIdentity() throws Exception {
    var service = service(3);
    var session = service.open("worker").sessionId();
    when(partners.getInfo(3L))
        .thenReturn(
            new BusinessPartnerInfo(
                3L, "양재", PartnerType.AUCTION_HOUSE, true, null, null, null, null));
    var raw =
        new SlipModelResult(
            "AUCTION",
            "2026-10-19",
            null,
            null,
            null,
            List.of(),
            List.of(),
            90,
            1024,
            1,
            List.of());
    when(runtime.extractConversation(anyString(), eq(session), anyString(), any()))
        .thenReturn(
            new SlipConversationModelResult(
                List.of(
                    new SlipConversationModelResult.Photo("q", raw),
                    new SlipConversationModelResult.Photo("p", raw))));
    var p =
        new SlipConversationInput.Photo(
            "p", "image/png", image(0), DocumentForm.CONSIGNMENT_RECEIPT);
    var q =
        new SlipConversationInput.Photo("q", "image/png", image(0), DocumentForm.LISTING_REQUEST);
    var rows =
        service.extract("worker", session, new SlipConversationInput(List.of(p, q))).photos();
    assertThat(rows)
        .filteredOn(r -> r.photoId().equals("p"))
        .allSatisfy(r -> assertThat(r.result().partnerId()).isEqualTo(3L));
    assertThat(rows)
        .filteredOn(r -> r.photoId().equals("q"))
        .allSatisfy(r -> assertThat(r.result().partnerId()).isNull());
    var changed =
        new SlipConversationInput.Photo("p", "image/png", image(0), DocumentForm.LISTING_REQUEST);
    assertThatThrownBy(
            () -> service.extract("worker", session, new SlipConversationInput(List.of(changed))))
        .isInstanceOf(AgentException.class)
        .extracting("code")
        .isEqualTo("AI_PHOTO_ID_CONFLICT");
  }

  @Test
  void sessionOwnershipExpiryAndClosePreventFurtherInference() throws Exception {
    var service = service();
    var session = service.open("worker").sessionId();
    var input = new SlipConversationInput(List.of(photo("p")));
    assertThatThrownBy(() -> service.extract("other", session, input))
        .isInstanceOf(AgentException.class)
        .extracting("code")
        .isEqualTo("AI_SESSION_EXPIRED");
    verify(runtime, never()).extractConversation(anyString(), anyString(), anyString(), any());
    service.close("worker", session);
    verify(runtime).closeSession(session);
    assertThatThrownBy(() -> service.extract("worker", session, input))
        .isInstanceOf(AgentException.class)
        .extracting("code")
        .isEqualTo("AI_SESSION_EXPIRED");
    var expiring = service.open("worker").sessionId();
    when(clock.instant()).thenReturn(now.plusSeconds(1801));
    assertThatThrownBy(() -> service.extract("worker", expiring, input))
        .isInstanceOf(AgentException.class)
        .extracting("code")
        .isEqualTo("AI_SESSION_EXPIRED");
  }

  @Test
  void rejectsDuplicateOutputIdsAndPhotoReplacementAndCanRemovePhotoFromSession() throws Exception {
    var service = service();
    var session = service.open("worker").sessionId();
    when(runtime.extractConversation(anyString(), anyString(), anyString(), any()))
        .thenReturn(
            new SlipConversationModelResult(
                List.of(
                    new SlipConversationModelResult.Photo("p", null),
                    new SlipConversationModelResult.Photo("p", null))));
    var input = new SlipConversationInput(List.of(photo("p"), photo("q")));
    assertThatThrownBy(() -> service.extract("worker", session, input))
        .isInstanceOf(AgentException.class)
        .extracting("code")
        .isEqualTo("AI_INVALID_RESULT");
    var replaced =
        new SlipConversationInput(
            List.of(new SlipConversationInput.Photo("p", "image/png", image(0xffffff))));
    assertThatThrownBy(() -> service.extract("worker", session, replaced))
        .isInstanceOf(AgentException.class)
        .extracting("code")
        .isEqualTo("AI_PHOTO_ID_CONFLICT");
    service.removePhoto("worker", session, "p");
    when(runtime.extractConversation(anyString(), anyString(), anyString(), any()))
        .thenReturn(
            new SlipConversationModelResult(
                List.of(new SlipConversationModelResult.Photo("p", null))));
    assertThat(service.extract("worker", session, replaced).photos().get(0).errorCode())
        .isEqualTo("AI_PHOTO_UNREADABLE");
  }
}
