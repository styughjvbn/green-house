package com.greenhouse.backend.sales.document.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.greenhouse.backend.sales.document.integration.image.SlipImageConfiguration;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.*;
import java.util.*;
import javax.imageio.ImageIO;
import kr.co.slip.api.*;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class DocumentImagePreparationServiceTest {
  private final SlipImageProcessor processor = mock(SlipImageProcessor.class);
  private final Clock clock = mock(Clock.class);
  private final Instant now = Instant.parse("2026-10-16T00:00:00Z");

  private DocumentImagePreparationService service() {
    when(clock.instant()).thenReturn(now);
    when(processor.process(any(), any())).thenReturn(result());
    return new DocumentImagePreparationService(processor, clock, 2200);
  }

  @Test
  void preservesOriginalUsesProductionOptionsAndExcludesDiagnosticImages() throws Exception {
    var service = service();
    byte[] original = png();
    byte[] before = original.clone();
    var preview = service.prepare("owner", "image/png", original, 90, .025);
    assertThat(original).containsExactly(before);
    assertThat(preview.images()).hasSize(4);
    assertThat(preview.images())
        .extracting(DocumentImagePreparationService.PreviewImage::kind)
        .contains(DocumentImagePreparationService.ImageKind.DOCUMENT_COMPACTED);
    assertThat(preview.reviewRequired()).isTrue();
    assertThat(preview.warnings()).containsExactly("방향 확인");
    assertThat(preview.expiresAt()).isEqualTo(now.plusSeconds(300));
    verify(processor)
        .process(
            same(original),
            argThat(
                options ->
                    options.outputMode() == OutputMode.PRODUCTION
                        && options.rotationDegrees() == 90
                        && options.maxDimension() == 2200
                        && options.minimumInkRatio() == .025));
    assertThat(service.image("owner", preview.id(), preview.images().getFirst().id()))
        .containsExactly(1, 2);
  }

  @Test
  void exportsSourceCoordinatesForTheCompactedDocumentOnly() throws Exception {
    var service = service();
    var row = new ProcessingResult.Row(1, 600, 700, 0, 0, true, "CONSECUTIVE_EMPTY_ROWS_CUTOFF");
    var table =
        new ProcessingResult.Table(
            1, new ProcessingResult.Box(40, 200, 400, 600), List.of(), List.of(), List.of(row), 1);
    var paper =
        new ProcessingResult.Paper(
            1, new ProcessingResult.Box(40, 200, 400, 600), List.of(), "TEST", false, List.of(1));
    var report =
        new ProcessingResult.Report(
            "TEST",
            400,
            500,
            800,
            1000,
            List.of(),
            List.of(),
            List.of(table),
            new ProcessingResult.Rotation("MANUAL", 0, 0, false, "TEST"),
            List.of(paper),
            List.of(.5, 0., 0., 0., .5, 0., 0., 0., 1.));
    when(processor.process(any(), any()))
        .thenReturn(new ProcessingResult(report, result().images()));
    var preview = service.prepare("owner", "image/png", png(), 0, .015);
    var document =
        preview.images().stream()
            .filter(i -> i.kind() == DocumentImagePreparationService.ImageKind.DOCUMENT_COMPACTED)
            .findFirst()
            .orElseThrow();
    assertThat(document.sourceRegion())
        .isEqualTo(new DocumentImagePreparationService.SourceRegion(.05, .2, .5, .4));
    assertThat(
            preview.images().stream()
                .filter(
                    i -> i.kind() != DocumentImagePreparationService.ImageKind.DOCUMENT_COMPACTED))
        .allMatch(i -> i.sourceRegion() == null);
  }

  @Test
  void isolatesOwnersExpiresPreviewsAndReleasesExplicitly() throws Exception {
    var service = service();
    var preview = service.prepare("owner", "image/png", png(), -1, .015);
    var image = preview.images().getFirst().id();
    assertCode(
        () -> service.image("other", preview.id(), image), "DOCUMENT_IMAGE_PREVIEW_NOT_FOUND");
    service.remove("other", preview.id());
    assertThat(service.image("owner", preview.id(), image)).isNotEmpty();
    service.remove("owner", preview.id());
    assertCode(
        () -> service.image("owner", preview.id(), image), "DOCUMENT_IMAGE_PREVIEW_NOT_FOUND");
    var next = service.prepare("owner", "image/png", png(), -1, .015);
    when(clock.instant()).thenReturn(now.plusSeconds(300));
    assertCode(
        () -> service.image("owner", next.id(), next.images().getFirst().id()),
        "DOCUMENT_IMAGE_PREVIEW_NOT_FOUND");
  }

  @Test
  void rejectsInvalidInputsBeforeNativeProcessing() throws Exception {
    var service = service();
    assertCode(
        () -> service.prepare("o", "image/png", new byte[] {1}, -1, .015),
        "DOCUMENT_IMAGE_INVALID_INPUT");
    assertCode(
        () -> service.prepare("o", "image/png", png(), 45, .015), "DOCUMENT_IMAGE_INVALID_INPUT");
    assertCode(
        () -> service.prepare("o", "image/gif", png(), -1, .015), "DOCUMENT_IMAGE_INVALID_INPUT");
    verify(processor, never()).process(any(), any());
  }

  @Test
  void rejectsInvalidInkRatiosBeforeNativeProcessing() throws Exception {
    var service = service();
    for (double ratio :
        new double[] {
          -.001, .101, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY
        }) {
      assertCode(
          () -> service.prepare("o", "image/png", png(), -1, ratio),
          "DOCUMENT_IMAGE_INVALID_INPUT");
    }
    verify(processor, never()).process(any(), any());
  }

  @Test
  void mapsCapacityFailureAndInterruptedWaitToStableCodes() throws Exception {
    var service = service();
    when(processor.process(any(), any())).thenThrow(new ProcessingRejectedException("full"));
    assertCode(
        () -> service.prepare("o", "image/png", png(), -1, .015), "DOCUMENT_IMAGE_PROCESSING_BUSY");
    doThrow(new ProcessingInterruptedException(new InterruptedException()))
        .when(processor)
        .process(any(), any());
    assertCode(
        () -> service.prepare("o", "image/png", png(), -1, .015),
        "DOCUMENT_IMAGE_PROCESSING_INTERRUPTED");
  }

  @Test
  void boundsRetainedResultsAndAllowsNewPreviewAfterRemoval() throws Exception {
    var service = service();
    var first = service.prepare("o", "image/png", png(), -1, .015);
    for (int i = 1; i < 16; i++) service.prepare("o", "image/png", png(), -1, .015);
    assertCode(
        () -> service.prepare("o", "image/png", png(), -1, .015),
        "DOCUMENT_IMAGE_PREVIEW_CAPACITY");
    service.remove("o", first.id());
    assertThat(service.prepare("o", "image/png", png(), -1, .015)).isNotNull();
  }

  @Test
  void rejectsOversizedResultWithoutRetainingIt() throws Exception {
    var service = service();
    when(processor.process(any(), any()))
        .thenReturn(
            new ProcessingResult(
                result().report(), Map.of("01-corrected.jpg", new byte[64 * 1024 * 1024 + 1])));
    assertCode(
        () -> service.prepare("o", "image/png", png(), -1, .015),
        "DOCUMENT_IMAGE_PREVIEW_CAPACITY");
    when(processor.process(any(), any())).thenReturn(result());
    assertThat(service.prepare("o", "image/png", png(), -1, .015)).isNotNull();
  }

  @Test
  void realNativeBeanProducesReadableJpegThroughTheHostService() throws Exception {
    var configuration = new SlipImageConfiguration();
    try (var nativeProcessor = configuration.slipImageProcessor(1, 0, 1)) {
      var service =
          new DocumentImagePreparationService(
              nativeProcessor, Clock.fixed(now, ZoneOffset.UTC), 2200);
      var preview = service.prepare("o", "image/png", png(), 0, .015);
      assertThat(preview.images()).isNotEmpty();
      for (var image : preview.images()) {
        assertThat(
                ImageIO.read(
                    new ByteArrayInputStream(service.image("o", preview.id(), image.id()))))
            .isNotNull();
      }
    }
  }

  private static void assertCode(ThrowingCallable action, String code) {
    assertThatThrownBy(action)
        .isInstanceOfSatisfying(
            DocumentImagePreparationService.PreparationException.class,
            e -> assertThat(e.code()).isEqualTo(code));
  }

  private static byte[] png() throws Exception {
    var image = new BufferedImage(400, 500, BufferedImage.TYPE_INT_RGB);
    var graphics = image.createGraphics();
    graphics.setColor(Color.WHITE);
    graphics.fillRect(0, 0, 400, 500);
    graphics.setColor(Color.BLACK);
    graphics.drawRect(30, 80, 340, 360);
    for (int y = 140; y < 440; y += 60) graphics.drawLine(30, y, 370, y);
    graphics.drawLine(200, 80, 200, 440);
    graphics.drawString("SLIP", 50, 50);
    graphics.dispose();
    var output = new ByteArrayOutputStream();
    ImageIO.write(image, "png", output);
    return output.toByteArray();
  }

  private static ProcessingResult result() {
    var report =
        new ProcessingResult.Report(
            "test",
            400,
            500,
            400,
            500,
            List.of(),
            List.of("방향 확인"),
            List.of(),
            new ProcessingResult.Rotation("auto", 0, 0, true, "test"),
            List.of(),
            List.of(1., 0., 0., 0., 1., 0., 0., 0., 1.));
    return new ProcessingResult(
        report,
        Map.of(
            "01-corrected.jpg",
            new byte[] {1, 2},
            "paper-01.jpg",
            new byte[] {3},
            "paper-01-compacted.jpg",
            new byte[] {6},
            "table-01-compacted.jpg",
            new byte[] {4},
            "05-row-decisions.jpg",
            new byte[] {5}));
  }
}
