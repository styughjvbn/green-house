package com.greenhouse.backend.work.application.effect;

import java.time.LocalDateTime;
import java.util.Map;

public record WorkEffectCommand(
		LocalDateTime executedAt,
		String worker,
		Map<String, Object> resultDetails) {
}
