package com.greenhouse.backend.work.dto.operation;

import java.time.LocalDate;

public record WorkOperationCompleteRequest(
		LocalDate completedDate) {
}
