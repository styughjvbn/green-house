package com.greenhouse.backend.work.application.effect;

import com.greenhouse.backend.work.dto.effect.StructureChangeExecutionRequest;
import com.greenhouse.backend.work.dto.effect.StructureChangeSourceRequest;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MovementQuantityAllocator {

	private MovementQuantityAllocator() {
	}

	public static Map<Long, Integer> allocateMovedBySource(
			StructureChangeExecutionRequest request) {
		List<StructureChangeSourceRequest> sources =
				request.sources().stream()
						.sorted(Comparator.comparing(StructureChangeSourceRequest::sourceOrchidGroupId))
						.toList();
		Map<Long, Integer> inputBySourceId = new LinkedHashMap<>();
		for (var source : sources) {
			if (inputBySourceId.put(source.sourceOrchidGroupId(), source.inputQuantity()) != null) {
				throw new IllegalArgumentException("작업 원본 난 묶음은 중복될 수 없습니다.");
			}
		}
		int requestedMoved = request.results().stream()
				.mapToInt(result -> result.quantity())
				.sum();
		int totalInput = inputBySourceId.values().stream().mapToInt(Integer::intValue).sum();
		if (requestedMoved > totalInput) {
			throw new IllegalArgumentException("자리 이동 결과 수량은 투입 수량보다 클 수 없습니다.");
		}

		Map<Long, Integer> movedBySourceId = new LinkedHashMap<>();
		int remainingMoved = requestedMoved;
		for (var entry : inputBySourceId.entrySet()) {
			int moved = Math.min(entry.getValue(), remainingMoved);
			movedBySourceId.put(entry.getKey(), moved);
			remainingMoved -= moved;
		}
		return movedBySourceId;
	}
}
