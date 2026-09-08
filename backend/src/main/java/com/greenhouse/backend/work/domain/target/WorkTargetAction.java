package com.greenhouse.backend.work.domain.target;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "WorkTargetAction")
public enum WorkTargetAction {

	START, COMPLETE, EXECUTE, SKIP

}
