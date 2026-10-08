package com.greenhouse.backend.work.api.target;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "WorkTargetAction")
public enum WorkTargetAction {
  START,
  COMPLETE,
  EXECUTE,
  SKIP
}
