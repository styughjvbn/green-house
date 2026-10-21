package com.greenhouse.backend.sales.document.web;

import com.greenhouse.backend.common.api.ErrorResponse;
import com.greenhouse.backend.sales.document.application.DocumentImagePreparationService.PreparationException;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@Order(-1)
@RestControllerAdvice(assignableTypes = DocumentImagePreviewController.class)
public class DocumentImagePreviewExceptionHandler {
  @ExceptionHandler(PreparationException.class)
  public ResponseEntity<ErrorResponse> preparation(PreparationException error) {
    return ResponseEntity.status(error.status())
        .body(ErrorResponse.of(error.code(), error.getMessage(), List.of()));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ErrorResponse> tooLarge() {
    return ResponseEntity.status(413)
        .body(ErrorResponse.of("DOCUMENT_IMAGE_TOO_LARGE", "사진 용량 제한을 초과했습니다.", List.of()));
  }

  @ExceptionHandler(MissingServletRequestPartException.class)
  public ResponseEntity<ErrorResponse> missing() {
    return ResponseEntity.badRequest()
        .body(ErrorResponse.of("DOCUMENT_IMAGE_INVALID_INPUT", "사진을 선택해 주세요.", List.of()));
  }
}
