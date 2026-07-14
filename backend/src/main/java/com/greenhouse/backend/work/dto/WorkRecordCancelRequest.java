package com.greenhouse.backend.work.dto;

import jakarta.validation.constraints.Size;

public record WorkRecordCancelRequest(
		@Size(max = 1000) String cancelReason) {
}
