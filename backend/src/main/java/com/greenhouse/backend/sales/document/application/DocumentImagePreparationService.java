package com.greenhouse.backend.sales.document.application;

import com.greenhouse.backend.common.image.UploadImage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import kr.co.slip.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Temporary, actor-owned previews. No sales persistence or database transaction. */
@Service
public class DocumentImagePreparationService {
  private static final Logger log = LoggerFactory.getLogger(DocumentImagePreparationService.class);
  private static final long MAX_BYTES = 64L * 1024 * 1024;
  private static final Duration TTL = Duration.ofMinutes(5);
  private final SlipImageProcessor processor;
  private final Clock clock;
  private final ProcessingOptions options;
  private final Map<UUID, StoredPreview> previews = new HashMap<>();

  public DocumentImagePreparationService(
      SlipImageProcessor processor,
      Clock clock,
      @Value("${sales.document.image.max-dimension:2200}") int maxDimension) {
    this.processor = processor;
    this.clock = clock;
    this.options =
        ProcessingOptions.defaults()
            .withMaxDimension(maxDimension)
            .withOutputMode(OutputMode.PRODUCTION);
  }

  public Preview prepare(String actor, String contentType, byte[] original, int rotationDegrees) {
    ProcessingResult result;
    try {
      UploadImage.validate(contentType, original);
      result = processor.process(original, options.withRotation(rotationDegrees));
    } catch (IllegalArgumentException e) {
      throw new PreparationException(
          400, "DOCUMENT_IMAGE_INVALID_INPUT", "사진 형식·크기와 회전 값을 확인해 주세요.");
    } catch (ProcessingRejectedException e) {
      throw new PreparationException(
          429, "DOCUMENT_IMAGE_PROCESSING_BUSY", "다른 사진을 처리 중입니다. 잠시 후 다시 시도해 주세요.");
    } catch (ProcessingInterruptedException e) {
      throw new PreparationException(
          503, "DOCUMENT_IMAGE_PROCESSING_INTERRUPTED", "사진 처리 대기가 중단되었습니다.");
    } catch (RuntimeException e) {
      log.error("Sales document image preparation failed", e);
      throw new PreparationException(500, "DOCUMENT_IMAGE_PROCESSING_FAILED", "사진을 처리하지 못했습니다.");
    }
    UUID id = UUID.randomUUID();
    Map<UUID, byte[]> images = new HashMap<>();
    List<PreviewImage> metadata = new ArrayList<>();
    result.images().entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            entry -> {
              ImageKind kind = kind(entry.getKey());
              if (kind == null) return;
              UUID imageId = UUID.randomUUID();
              images.put(imageId, entry.getValue());
              metadata.add(new PreviewImage(imageId, kind));
            });
    var report = result.report();
    var rotation = report.rotation();
    Instant expiresAt = clock.instant().plus(TTL);
    Preview preview =
        new Preview(
            id,
            expiresAt,
            report.sourceWidth(),
            report.sourceHeight(),
            report.workingWidth(),
            report.workingHeight(),
            rotation.clockwiseDegrees(),
            rotation.deskewCounterClockwiseDegrees(),
            rotation.reviewRequired()
                || report.papers().stream().anyMatch(ProcessingResult.Paper::reviewRequired),
            List.copyOf(report.warnings()),
            report.papers().size(),
            report.tables().size(),
            report.tables().stream().mapToInt(ProcessingResult.Table::removedRows).sum(),
            List.copyOf(metadata));
    store(id, new StoredPreview(actor, expiresAt, Map.copyOf(images)));
    return preview;
  }

  private static ImageKind kind(String name) {
    if (name.equals("01-corrected.png")) return ImageKind.CORRECTED;
    if (name.matches("paper-\\d+-compacted\\.png")) return ImageKind.DOCUMENT_COMPACTED;
    if (name.matches("paper-\\d+\\.png")) return ImageKind.DOCUMENT;
    if (name.matches("table-\\d+-compacted\\.png")) return ImageKind.TABLE;
    return null;
  }

  private synchronized void store(UUID id, StoredPreview preview) {
    prune();
    long size = preview.size();
    long total = previews.values().stream().mapToLong(StoredPreview::size).sum();
    if (size > MAX_BYTES || previews.size() >= 16 || total + size > MAX_BYTES)
      throw new PreparationException(
          429, "DOCUMENT_IMAGE_PREVIEW_CAPACITY", "미리보기 보관 한도를 초과했습니다. 열린 미리보기를 닫고 다시 시도해 주세요.");
    previews.put(id, preview);
  }

  public synchronized byte[] image(String actor, UUID id, UUID imageId) {
    prune();
    StoredPreview preview = previews.get(id);
    if (preview == null || !preview.actor().equals(actor) || !preview.images().containsKey(imageId))
      throw new PreparationException(
          404, "DOCUMENT_IMAGE_PREVIEW_NOT_FOUND", "미리보기가 만료되었습니다. 다시 처리해 주세요.");
    return preview.images().get(imageId);
  }

  public synchronized void remove(String actor, UUID id) {
    prune();
    StoredPreview preview = previews.get(id);
    if (preview != null && preview.actor().equals(actor)) previews.remove(id);
  }

  private void prune() {
    Instant now = clock.instant();
    previews.values().removeIf(preview -> !preview.expiresAt().isAfter(now));
  }

  private record StoredPreview(String actor, Instant expiresAt, Map<UUID, byte[]> images) {
    long size() {
      return images.values().stream().mapToLong(bytes -> bytes.length).sum();
    }
  }

  public enum ImageKind {
    CORRECTED,
    DOCUMENT,
    DOCUMENT_COMPACTED,
    TABLE
  }

  @Schema(name = "SalesSlipPreviewImage")
  public record PreviewImage(UUID id, ImageKind kind) {}

  @Schema(name = "SalesSlipImagePreview")
  public record Preview(
      UUID id,
      Instant expiresAt,
      int sourceWidth,
      int sourceHeight,
      int workingWidth,
      int workingHeight,
      int clockwiseDegrees,
      double deskewDegrees,
      boolean reviewRequired,
      List<String> warnings,
      int documentCount,
      int tableCount,
      int removedRows,
      List<PreviewImage> images) {}

  public static class PreparationException extends RuntimeException {
    private final int status;
    private final String code;

    public PreparationException(int status, String code, String message) {
      super(message);
      this.status = status;
      this.code = code;
    }

    public int status() {
      return status;
    }

    public String code() {
      return code;
    }
  }
}
