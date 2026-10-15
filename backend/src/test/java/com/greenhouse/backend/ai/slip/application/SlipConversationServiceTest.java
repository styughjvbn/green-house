package com.greenhouse.backend.ai.slip.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.greenhouse.backend.ai.api.AgentException;
import com.greenhouse.backend.ai.slip.application.port.out.AgentRuntimePort;
import com.greenhouse.backend.ai.tool.application.AgentToolGateway;
import com.greenhouse.backend.farm.api.variety.VarietySearchApi;
import com.greenhouse.backend.sales.api.partner.BusinessPartnerQueryApi;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class SlipConversationServiceTest {
  private final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();

  @org.junit.jupiter.api.AfterEach
  void closeFactory() {
    factory.close();
  }

  private final AgentRuntimePort runtime = mock(AgentRuntimePort.class);
  private final Clock clock = mock(Clock.class);
  private final Instant now = Instant.parse("2026-10-15T00:00:00Z");

  private SlipConversationService service() {
    when(clock.instant()).thenReturn(now);
    var tools =
        new AgentToolGateway(
            mock(VarietySearchApi.class), mock(BusinessPartnerQueryApi.class), clock, 20, 120);
    return new SlipConversationService(
        runtime, tools, new SlipExtractionService(runtime, tools, factory.getValidator()), clock);
  }

  private byte[] image(int rgb) throws Exception {
    var image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
    image.setRGB(0, 0, rgb);
    var out = new ByteArrayOutputStream();
    ImageIO.write(image, "png", out);
    return out.toByteArray();
  }

  private SlipConversationInput.Photo photo(String id, String feedback) throws Exception {
    return new SlipConversationInput.Photo(id, "image/png", image(0), feedback);
  }

  private SlipModelResult result(String name) {
    return new SlipModelResult(null, null, name, null, null, List.of(), List.of(), 90, 1024, 1);
  }

  @Test
  void tenPhotosSingleAndFeedbackRequestsShareOneOwnedConversationAndResultsFollowIds()
      throws Exception {
    var service = service();
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
    for (int i = 0; i < 10; i++) photos.add(photo("photo-" + i, null));
    var batch = service.extract("worker", session, new SlipConversationInput(photos));
    assertThat(batch.photos()).hasSize(10);
    for (var row : batch.photos()) assertThat(row.result().partnerName()).isEqualTo(row.photoId());
    service.extract("worker", session, new SlipConversationInput(List.of(photos.get(0))));
    service.extract(
        "worker", session, new SlipConversationInput(List.of(photo("photo-0", "수량은 20개"))));
    verify(runtime).openSession(session);
    verify(runtime, times(3)).extractConversation(anyString(), eq(session), anyString(), any());
    verify(runtime)
        .extractConversation(
            anyString(),
            eq(session),
            anyString(),
            argThat(
                input ->
                    input.photos().get(0).feedback() != null
                        && input.photos().get(0).feedback().equals("수량은 20개")));
  }

  @Test
  void sessionOwnershipExpiryAndClosePreventFurtherInference() throws Exception {
    var service = service();
    var session = service.open("worker").sessionId();
    var input = new SlipConversationInput(List.of(photo("p", null)));
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
    var input = new SlipConversationInput(List.of(photo("p", null), photo("q", null)));
    assertThatThrownBy(() -> service.extract("worker", session, input))
        .isInstanceOf(AgentException.class)
        .extracting("code")
        .isEqualTo("AI_INVALID_RESULT");
    var replaced =
        new SlipConversationInput(
            List.of(new SlipConversationInput.Photo("p", "image/png", image(0xffffff), null)));
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
