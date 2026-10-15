package com.greenhouse.backend.ai.slip.application;

import java.util.List;

public record SlipConversationResult(String sessionId, String requestId, List<Photo> photos) {
  @io.swagger.v3.oas.annotations.media.Schema(name = "AiSlipPhotoResult")
  public record Photo(String photoId, SlipExtractionResult result, String errorCode) {}

  @io.swagger.v3.oas.annotations.media.Schema(name = "AiSlipSession")
  public record Session(String sessionId) {}
}
