package com.greenhouse.backend.work.application.effect;

import java.time.LocalDateTime;
import java.util.Map;

public record WorkEffectCommand(
		LocalDateTime executedAt,
		String worker,
		Map<String, Object> resultDetails,
		Object payload) {

	public <T> T payloadAs(Class<T> payloadType) {
		if (!payloadType.isInstance(payload)) {
			throw new IllegalArgumentException("작업 효과 명령 형식이 올바르지 않습니다.");
		}
		return payloadType.cast(payload);
	}
}
