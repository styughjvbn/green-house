package com.greenhouse.backend.common.exception;

public class QueryLimitExceededException extends RuntimeException {
  public QueryLimitExceededException() {
    super("조회 범위가 너무 큽니다. 조회 조건을 좁히거나 페이지 조회를 이용해주세요.");
  }
}
