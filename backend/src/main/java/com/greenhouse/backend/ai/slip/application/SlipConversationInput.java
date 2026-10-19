package com.greenhouse.backend.ai.slip.application;

import java.util.List;
import kr.co.slip.api.DocumentForm;

public record SlipConversationInput(List<Photo> photos) {
  public record Photo(String photoId, String contentType, byte[] image, DocumentForm documentForm) {
    public Photo {
      documentForm = documentForm == null ? DocumentForm.UNKNOWN : documentForm;
    }

    public Photo(String photoId, String contentType, byte[] image) {
      this(photoId, contentType, image, DocumentForm.UNKNOWN);
    }
  }
}
