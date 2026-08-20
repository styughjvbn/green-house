package com.greenhouse.backend.work.application.effect;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

public record WorkEffectCommand(
		LocalDateTime executedAt,
		String worker,
		Map<String, Object> resultDetails,
		Object payload,
		Set<Long> placementExclusionOrchidGroupIds,
		String effectKey) {

	public WorkEffectCommand(
			LocalDateTime executedAt,
			String worker,
			Map<String, Object> resultDetails,
			Object payload) {
		this(executedAt, worker, resultDetails, payload, Set.of(), null);
	}

	public WorkEffectCommand(
			LocalDateTime executedAt,
			String worker,
			Map<String, Object> resultDetails,
			Object payload,
			Set<Long> placementExclusionOrchidGroupIds) {
		this(executedAt, worker, resultDetails, payload, placementExclusionOrchidGroupIds, null);
	}

	public WorkEffectCommand {
		placementExclusionOrchidGroupIds = placementExclusionOrchidGroupIds == null
				? Set.of()
				: Set.copyOf(placementExclusionOrchidGroupIds);
		if (effectKey != null) {
			effectKey = effectKey.trim();
			if (effectKey.isEmpty()) {
				throw new IllegalArgumentException("작업 효과 키는 비워둘 수 없습니다.");
			}
		}
	}

	public WorkEffectCommand withEffectKey(String effectKey) {
		return new WorkEffectCommand(
				executedAt,
				worker,
				resultDetails,
				payload,
				placementExclusionOrchidGroupIds,
				effectKey);
	}

	public <T> T payloadAs(Class<T> payloadType) {
		if (!payloadType.isInstance(payload)) {
			throw new IllegalArgumentException("작업 효과 명령 형식이 올바르지 않습니다.");
		}
		return payloadType.cast(payload);
	}
}
