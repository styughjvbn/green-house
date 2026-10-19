package com.greenhouse.backend.sales.document.web;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.greenhouse.backend.sales.document.application.DocumentImagePreparationService;
import com.greenhouse.backend.sales.document.application.DocumentImagePreparationService.PreparationException;
import java.util.List;
import java.util.UUID;
import kr.co.slip.api.ProcessingOptions;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DocumentImagePreviewControllerTest {
  @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
  @org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
  static class MethodSecurity {}

  @Test
  void demoCannotProcessOrReadPreviewImages() {
    try (var context = new AnnotationConfigApplicationContext()) {
      var service = mock(DocumentImagePreparationService.class);
      context.register(MethodSecurity.class);
      context.registerBean(DocumentImagePreparationService.class, () -> service);
      context.register(DocumentImagePreviewController.class);
      context.refresh();
      var controller = context.getBean(DocumentImagePreviewController.class);
      var security = SecurityContextHolder.getContext();
      security.setAuthentication(
          new UsernamePasswordAuthenticationToken(
              "demo", "", List.of(new SimpleGrantedAuthority("ROLE_DEMO"))));
      try {
        Assertions.assertThatThrownBy(
                () ->
                    controller.prepare(
                        new MockMultipartFile("image", new byte[] {1}), -1, .015, () -> "demo"))
            .isInstanceOf(AccessDeniedException.class);
        Assertions.assertThatThrownBy(
                () -> controller.image(UUID.randomUUID(), UUID.randomUUID(), () -> "demo"))
            .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(service);
      } finally {
        SecurityContextHolder.clearContext();
      }
    }
  }

  @Test
  void passesActorAndRotationAndUsesCommonErrorEnvelope() throws Exception {
    var service = mock(DocumentImagePreparationService.class);
    when(service.prepare(eq("worker"), eq("image/png"), any(), eq(90), eq(.025)))
        .thenThrow(new PreparationException(429, "DOCUMENT_IMAGE_PROCESSING_BUSY", "busy"));
    var mvc =
        MockMvcBuilders.standaloneSetup(new DocumentImagePreviewController(service))
            .setControllerAdvice(new DocumentImagePreviewExceptionHandler())
            .build();
    mvc.perform(
            multipart("/api/sales-slips/image-previews")
                .file(new MockMultipartFile("image", "a.png", "image/png", new byte[] {1}))
                .param("rotationDegrees", "90")
                .param("minimumInkRatio", "0.025")
                .principal(() -> "worker"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.error.code").value("DOCUMENT_IMAGE_PROCESSING_BUSY"));
    mvc.perform(multipart("/api/sales-slips/image-previews"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("DOCUMENT_IMAGE_INVALID_INPUT"));
  }

  @Test
  void omittedInkRatioUsesTheCoreDefault() throws Exception {
    var service = mock(DocumentImagePreparationService.class);
    var mvc = MockMvcBuilders.standaloneSetup(new DocumentImagePreviewController(service)).build();
    mvc.perform(
            multipart("/api/sales-slips/image-previews")
                .file(new MockMultipartFile("image", "a.png", "image/png", new byte[] {1})))
        .andExpect(status().isOk());
    verify(service)
        .prepare(
            eq("local"),
            eq("image/png"),
            any(),
            eq(-1),
            eq(ProcessingOptions.defaults().minimumInkRatio()));
  }

  @Test
  void servesPrivateJpegWithoutCachingAndDeletesOnlyWithCallerIdentity() throws Exception {
    var service = mock(DocumentImagePreparationService.class);
    UUID id = UUID.randomUUID(), imageId = UUID.randomUUID();
    when(service.image("worker", id, imageId)).thenReturn(new byte[] {1, 2});
    var mvc =
        MockMvcBuilders.standaloneSetup(new DocumentImagePreviewController(service))
            .setControllerAdvice(new DocumentImagePreviewExceptionHandler())
            .build();
    mvc.perform(
            get("/api/sales-slips/image-previews/{id}/images/{imageId}", id, imageId)
                .principal(() -> "worker"))
        .andExpect(status().isOk())
        .andExpect(content().contentType("image/jpeg"))
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(content().bytes(new byte[] {1, 2}));
    mvc.perform(delete("/api/sales-slips/image-previews/{id}", id).principal(() -> "worker"))
        .andExpect(status().isOk());
    verify(service).remove("worker", id);
    when(service.image("worker", id, imageId))
        .thenThrow(new PreparationException(404, "DOCUMENT_IMAGE_PREVIEW_NOT_FOUND", "expired"));
    mvc.perform(
            get("/api/sales-slips/image-previews/{id}/images/{imageId}", id, imageId)
                .principal(() -> "worker"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("DOCUMENT_IMAGE_PREVIEW_NOT_FOUND"));
  }
}
