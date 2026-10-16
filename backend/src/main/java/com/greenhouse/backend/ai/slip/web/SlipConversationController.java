package com.greenhouse.backend.ai.slip.web;

import com.greenhouse.backend.ai.api.AgentException;
import com.greenhouse.backend.ai.slip.application.*;
import com.greenhouse.backend.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ai/slips/sessions")
@Tag(name = "ai-slip-controller")
@PreAuthorize("!hasRole('DEMO')")
@RequiredArgsConstructor
public class SlipConversationController {
  private final SlipConversationService service;

  @PostMapping
  @Operation(description = "사진 등록 대화를 시작합니다. 대화는 사용자에게 귀속되며 30분간 사용하지 않으면 만료됩니다.")
  public ApiResponse<SlipConversationResult.Session> open(Principal principal) {
    return ApiResponse.ok(service.open(actor(principal)));
  }

  @DeleteMapping("/{sessionId}")
  public ApiResponse<Void> close(@PathVariable String sessionId, Principal principal) {
    service.close(actor(principal), sessionId);
    return ApiResponse.ok(null);
  }

  @DeleteMapping("/{sessionId}/photos/{photoId}")
  public ApiResponse<Void> removePhoto(
      @PathVariable String sessionId, @PathVariable String photoId, Principal principal) {
    service.removePhoto(actor(principal), sessionId, photoId);
    return ApiResponse.ok(null);
  }

  @PostMapping(value = "/{sessionId}/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(
      description =
          "선택 사진 1~10장을 동일 대화의 한 요청으로 추출합니다. metadata 순서와 photos 파일 순서는 일치해야 합니다. 사진별 최대 8MB. 결과는 사진 ID로 연결합니다.")
  public ApiResponse<SlipConversationResult> extract(
      @PathVariable String sessionId,
      @Valid @RequestPart("metadata") Metadata metadata,
      @RequestPart("photos") List<MultipartFile> photos,
      Principal principal)
      throws IOException {
    if (photos.size() != metadata.photos().size() || photos.size() > 10)
      throw new AgentException(400, "AI_INVALID_REQUEST", "사진과 ID를 확인해주세요.");
    var inputs = new ArrayList<SlipConversationInput.Photo>();
    for (int i = 0; i < photos.size(); i++) {
      var file = photos.get(i);
      var photo = metadata.photos().get(i);
      if (file.isEmpty() || file.getSize() > 8 * 1024 * 1024)
        throw new AgentException(413, "AI_IMAGE_TOO_LARGE", "사진별 최대 크기는 8MB입니다.");
      inputs.add(
          new SlipConversationInput.Photo(photo.photoId(), file.getContentType(), file.getBytes()));
    }
    return ApiResponse.ok(
        service.extract(
            actor(principal), sessionId, new SlipConversationInput(List.copyOf(inputs))));
  }

  @io.swagger.v3.oas.annotations.media.Schema(name = "AiSlipConversationMetadata")
  public record Metadata(@NotEmpty @Size(max = 10) List<@NotNull @Valid Photo> photos) {}

  @io.swagger.v3.oas.annotations.media.Schema(name = "AiSlipPhotoMetadata")
  public record Photo(@NotBlank @Pattern(regexp = "[-a-zA-Z0-9]{1,64}") String photoId) {}

  private String actor(Principal principal) {
    return principal == null ? "local" : principal.getName();
  }
}
