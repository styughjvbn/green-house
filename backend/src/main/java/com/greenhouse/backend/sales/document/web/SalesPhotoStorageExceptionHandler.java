package com.greenhouse.backend.sales.document.web;

import com.greenhouse.backend.common.api.ErrorResponse;
import com.greenhouse.backend.sales.document.application.PhotoStorageException;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = {SalesController.class, SalesSlipPhotoController.class})
public class SalesPhotoStorageExceptionHandler {
  @ExceptionHandler(PhotoStorageException.class)
  ResponseEntity<ErrorResponse> unavailable(PhotoStorageException failure) {
    return ResponseEntity.status(503)
        .body(
            ErrorResponse.of(
                "SALES_PHOTO_STORAGE_UNAVAILABLE",
                "전표 사진 저장소를 사용할 수 없습니다. 잠시 후 같은 요청으로 다시 시도해주세요.",
                List.of()));
  }
}
