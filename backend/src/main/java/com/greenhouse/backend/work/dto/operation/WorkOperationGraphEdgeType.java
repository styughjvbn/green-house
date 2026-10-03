package com.greenhouse.backend.work.dto.operation;

public enum WorkOperationGraphEdgeType {
  ORIGINATED,
  SAME_COMMAND,
  PRECEDES,
  EFFECT,
  STATE_INPUT,
  STATE_OUTPUT,
  MUTATION_RELATION
}
