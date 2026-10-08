package com.greenhouse.backend.work.api.operation;

/** Single-operation value lookup used outside Work; repository access remains internal. */
public interface WorkOperationQueryApi {

  WorkOperationView get(Long operationId);
}
