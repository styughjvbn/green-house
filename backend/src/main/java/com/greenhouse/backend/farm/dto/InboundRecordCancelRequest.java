package com.greenhouse.backend.farm.dto;

import jakarta.validation.constraints.Size;

public record InboundRecordCancelRequest(
	@Size(max = 1000) String memo
) {
}
