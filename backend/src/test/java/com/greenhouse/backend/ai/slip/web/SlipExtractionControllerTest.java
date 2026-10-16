package com.greenhouse.backend.ai.slip.web;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.greenhouse.backend.ai.slip.application.*;
import com.greenhouse.backend.ai.tool.application.AgentToolGateway;
import com.greenhouse.backend.ai.tool.web.AgentToolController;
import com.greenhouse.backend.farm.api.variety.VarietySearchApi;
import com.greenhouse.backend.sales.api.partner.BusinessPartnerQueryApi;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SlipExtractionControllerTest {
  @Test
  void authenticatedCallerIdentityAndMultipartImageArePassedToExtractionWithoutSaving()
      throws Exception {
    var service = mock(SlipExtractionService.class);
    when(service.extract(eq("worker"), eq("image/png"), any()))
        .thenReturn(
            new SlipExtractionResult(
                "r", null, null, null, null, null, null, List.of(), List.of(), 90, 1024, 1,
                List.of()));
    var mvc =
        MockMvcBuilders.standaloneSetup(new SlipExtractionController(service))
            .setControllerAdvice(new AgentExceptionHandler())
            .build();
    mvc.perform(
            multipart("/api/ai/slips/extract")
                .file(new MockMultipartFile("image", "slip.png", "image/png", new byte[] {1}))
                .principal(() -> "worker"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.requestId").value("r"));
    verify(service).extract(eq("worker"), eq("image/png"), any());
  }

  @Test
  void internalToolsRejectCallsWithoutExecutionScopeEvenWithoutBrowserSessionSecurity()
      throws Exception {
    var varieties = mock(VarietySearchApi.class);
    var partners = mock(BusinessPartnerQueryApi.class);
    var gateway = new AgentToolGateway(varieties, partners, Clock.systemUTC(), 2, 120);
    var mvc =
        MockMvcBuilders.standaloneSetup(new AgentToolController(gateway))
            .setControllerAdvice(new AgentExceptionHandler())
            .build();
    mvc.perform(
            post("/internal/ai/tools/search_varieties")
                .contentType("application/json")
                .content("{\"query\":\"난\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("AI_TOOL_ACCESS_DENIED"));
    verifyNoInteractions(varieties, partners);
  }

  @Test
  void batchMultipartPassesPhotoIdsAndFeedbackInOneCallAndRejectsMismatchedFiles()
      throws Exception {
    var service = mock(SlipConversationService.class);
    when(service.extract(eq("worker"), eq("session"), any()))
        .thenReturn(
            new SlipConversationResult(
                "session",
                "request",
                List.of(new SlipConversationResult.Photo("p", null, "AI_PHOTO_UNREADABLE"))));
    var mvc =
        MockMvcBuilders.standaloneSetup(new SlipConversationController(service))
            .setControllerAdvice(new AgentExceptionHandler())
            .build();
    var metadata =
        new MockMultipartFile(
            "metadata",
            "metadata.json",
            "application/json",
            "{\"photos\":[{\"photoId\":\"p\",\"feedback\":\"수량 20개\"}]}"
                .getBytes(StandardCharsets.UTF_8));
    mvc.perform(
            multipart("/api/ai/slips/sessions/session/extract")
                .file(metadata)
                .file(new MockMultipartFile("photos", "p.png", "image/png", new byte[] {1}))
                .principal(() -> "worker"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.photos[0].photoId").value("p"));
    verify(service)
        .extract(
            eq("worker"),
            eq("session"),
            argThat(
                input ->
                    input.photos().size() == 1 && input.photos().get(0).photoId().equals("p")));
    mvc.perform(
            multipart("/api/ai/slips/sessions/session/extract")
                .file(metadata)
                .file(new MockMultipartFile("photos", "p.png", "image/png", new byte[] {1}))
                .file(new MockMultipartFile("photos", "q.png", "image/png", new byte[] {1}))
                .principal(() -> "worker"))
        .andExpect(status().isBadRequest());
    verifyNoMoreInteractions(service);
  }
}
