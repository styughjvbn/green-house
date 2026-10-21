package com.greenhouse.backend.ai.slip.application;

import java.util.List;

public record SlipConversationModelResult(List<Photo> photos) {
  public record Photo(String photoId, SlipModelResult result) {}
}
