package com.greenhouse.backend.auth.dto;

import java.time.LocalDate;

public record ApplicationContextResponse(
		LocalDate businessDate,
		String timeZone
) {
}
