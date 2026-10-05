package com.greenhouse.backend.common.exception;

import com.greenhouse.backend.common.api.ErrorResponse;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(QueryLimitExceededException.class)
  ResponseEntity<ErrorResponse> handleQueryLimit(QueryLimitExceededException exception) {
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
        .body(ErrorResponse.of("QUERY_LIMIT_EXCEEDED", exception.getMessage(), List.of()));
  }

  @ExceptionHandler({
    ObjectOptimisticLockingFailureException.class,
    PessimisticLockingFailureException.class
  })
  ResponseEntity<ErrorResponse> handleConcurrentChange(Exception exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            ErrorResponse.of(
                "CONCURRENT_MODIFICATION", "다른 요청이 먼저 처리되었습니다. 최신 상태를 확인하고 다시 요청해주세요.", List.of()));
  }

  @ExceptionHandler(CapacityConflictException.class)
  ResponseEntity<ErrorResponse> handleCapacityConflict(CapacityConflictException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ErrorResponse.of("CAPACITY_CONFLICT", exception.getMessage(), List.of()));
  }

  @ExceptionHandler(ConflictException.class)
  ResponseEntity<ErrorResponse> handleConflict(ConflictException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ErrorResponse.of(exception.getCode(), exception.getMessage(), List.of()));
  }

  @ExceptionHandler(NotFoundException.class)
  ResponseEntity<ErrorResponse> handleNotFound(NotFoundException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ErrorResponse.of("NOT_FOUND", exception.getMessage(), List.of()));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
      DataIntegrityViolationException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            ErrorResponse.of("DATA_INTEGRITY_CONFLICT", "데이터 정합성 제약으로 요청을 처리할 수 없습니다.", List.of()));
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    MethodArgumentNotValidException.class,
    MissingServletRequestParameterException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<ErrorResponse> handleValidation(Exception exception) {
    return ResponseEntity.badRequest()
        .body(
            ErrorResponse.of(
                "VALIDATION_ERROR", "요청 값이 올바르지 않습니다.", List.of(exception.getMessage())));
  }
}
