package com.greenhouse.backend.ai.slip.web;

import com.greenhouse.backend.ai.slip.application.SlipExtractionResult;
import com.greenhouse.backend.ai.slip.application.SlipExtractionService;
import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.common.image.UploadImage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ai/slips")
@Tag(name = "ai-slip-controller")
@RequiredArgsConstructor
public class SlipExtractionController {
  private final SlipExtractionService service;

  @org.springframework.security.access.prepost.PreAuthorize("!hasRole('DEMO')")
  @Operation(description = "전표 사진을 읽고 조회 도구로 거래처·품종 후보를 찾습니다. 전표·재고를 저장하지 않습니다. JPEG/PNG 최대 8MB.")
  @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ApiResponse<SlipExtractionResult> extract(
      @RequestPart("image") MultipartFile image, Principal principal) throws IOException {
    if (image.isEmpty() || image.getSize() > UploadImage.MAX_BYTES)
      throw new IllegalArgumentException("사진은 최대 8MB까지 등록할 수 있습니다.");
    return ApiResponse.ok(
        service.extract(
            principal == null ? "local" : principal.getName(),
            image.getContentType(),
            image.getBytes()));
  }
}
