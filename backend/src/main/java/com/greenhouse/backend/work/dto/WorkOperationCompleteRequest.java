package com.greenhouse.backend.work.dto;

import java.time.LocalDate;

public record WorkOperationCompleteRequest(
		LocalDate completedDate) {
}
