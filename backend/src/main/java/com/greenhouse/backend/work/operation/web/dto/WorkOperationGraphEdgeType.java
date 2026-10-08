package com.greenhouse.backend.work.operation.web.dto;

public enum WorkOperationGraphEdgeType {
  ORIGINATED,
  SAME_COMMAND,
  PRECEDES,
  EFFECT,
  STATE_INPUT,
  STATE_OUTPUT,
  MUTATION_RELATION
}
