package com.greenhouse.backend.ai.slip.application;

import java.util.List;

public record SlipConversationInput(List<Photo> photos) {
  public record Photo(String photoId, String contentType, byte[] image, String feedback) {}
}
