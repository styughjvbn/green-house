package com.greenhouse.backend.sales.document.web;

import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.sales.document.application.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sales-slips")
@Tag(name = "sales-controller")
@RequiredArgsConstructor
public class SalesSlipPhotoController {
  private final SalesSlipPhotoService photos;

  @Operation(description = "전표 첨부 사진의 메타데이터. 사진이 없으면 data=null. 전표 취소 후에도 보존합니다.")
  @GetMapping("/{id}/source-photo/info")
  public ApiResponse<SalesSlipPhotoService.PhotoInfo> getPhotoInfo(@PathVariable Long id) {
    return ApiResponse.ok(photos.info(id));
  }

  @Operation(description = "인증된 전표 사진 조회. JPEG/PNG/WebP 최대 8MB. 저장된 사진의 Base64 데이터를 반환합니다.")
  @GetMapping("/{id}/source-photo")
  public ApiResponse<SalesSlipPhotoService.PhotoContent> getPhoto(@PathVariable Long id) {
    return ApiResponse.ok(photos.content(id));
  }
}
