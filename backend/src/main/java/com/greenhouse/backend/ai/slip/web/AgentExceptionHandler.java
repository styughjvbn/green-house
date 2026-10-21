package com.greenhouse.backend.ai.slip.web;

import com.greenhouse.backend.ai.api.AgentException;
import com.greenhouse.backend.common.api.ErrorResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice(basePackages = "com.greenhouse.backend.ai")
public class AgentExceptionHandler {
  @ExceptionHandler(AgentException.class)
  public ResponseEntity<ErrorResponse> handle(AgentException e) {
    return ResponseEntity.status(e.status())
        .body(ErrorResponse.of(e.code(), e.getMessage(), List.of()));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ErrorResponse> tooLarge() {
    return ResponseEntity.status(413)
        .body(ErrorResponse.of("AI_IMAGE_TOO_LARGE", "사진은 최대 8MB까지 등록할 수 있습니다.", List.of()));
  }

  @ExceptionHandler({
    MissingServletRequestPartException.class,
    HttpMessageNotReadableException.class
  })
  public ResponseEntity<ErrorResponse> invalidRequest() {
    return ResponseEntity.badRequest()
        .body(ErrorResponse.of("AI_INVALID_REQUEST", "사진 또는 요청 값을 확인해주세요.", List.of()));
  }
}
