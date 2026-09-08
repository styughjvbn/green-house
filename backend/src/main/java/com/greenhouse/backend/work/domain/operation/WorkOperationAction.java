package com.greenhouse.backend.work.domain.operation;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(name = "WorkOperationAction")
public enum WorkOperationAction {
	START,
	PAUSE,
	RESUME,
	COMPLETE,
	CANCEL
}
