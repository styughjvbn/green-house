package com.greenhouse.backend.farm.dto.inbound;

import jakarta.validation.constraints.Size;

public record InboundRecordCancelRequest(
	@Size(max = 1000) String memo
) {
}
