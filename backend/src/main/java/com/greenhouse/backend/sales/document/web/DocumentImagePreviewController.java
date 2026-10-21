package com.greenhouse.backend.sales.document.web;

import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.common.image.UploadImage;
import com.greenhouse.backend.sales.document.application.DocumentImagePreparationService;
import com.greenhouse.backend.sales.document.application.DocumentImagePreparationService.PreparationException;
import com.greenhouse.backend.sales.document.application.DocumentImagePreparationService.Preview;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/sales-slips/image-previews")
@Tag(name = "sales-controller")
@PreAuthorize("!hasRole('DEMO')")
@RequiredArgsConstructor
public class DocumentImagePreviewController {
  private final DocumentImagePreparationService service;

  @Operation(
      description =
          "원본은 보존하고 전표 사진의 보정·문서 분리·빈 행 제거 결과를 임시 미리보기로 생성합니다. 판매 저장과 AI 입력에는 반영하지 않습니다. 미리보기는 5분 후 만료됩니다.")
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ApiResponse<Preview> prepare(
      @RequestPart("image") MultipartFile image,
      @RequestParam(defaultValue = "-1") int rotationDegrees,
      @Parameter(
              description = "빈 행 판정 잉크 비율. 0.015는 1.5%이며, 이 값 이하인 행을 빈 행으로 판정합니다.",
              schema =
                  @Schema(
                      type = "number",
                      format = "double",
                      defaultValue = "0.015",
                      minimum = "0",
                      maximum = "0.1"))
          @RequestParam(defaultValue = "0.015")
          double minimumInkRatio,
      Principal principal)
      throws IOException {
    if (image.isEmpty() || image.getSize() > UploadImage.MAX_BYTES)
      throw new PreparationException(
          400, "DOCUMENT_IMAGE_INVALID_INPUT", "사진은 최대 8MB까지 등록할 수 있습니다.");
    return ApiResponse.ok(
        service.prepare(
            actor(principal),
            image.getContentType(),
            image.getBytes(),
            rotationDegrees,
            minimumInkRatio));
  }

  @GetMapping(value = "/{id}/images/{imageId}", produces = "image/webp")
  public ResponseEntity<byte[]> image(
      @PathVariable UUID id, @PathVariable UUID imageId, Principal principal) {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("image/webp"))
        .cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff")
        .body(service.image(actor(principal), id, imageId));
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Void> remove(@PathVariable UUID id, Principal principal) {
    service.remove(actor(principal), id);
    return ApiResponse.ok(null);
  }

  private static String actor(Principal principal) {
    return principal == null ? "local" : principal.getName();
  }
}
