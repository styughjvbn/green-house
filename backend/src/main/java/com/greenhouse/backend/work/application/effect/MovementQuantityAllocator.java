package com.greenhouse.backend.work.application.effect;

import com.greenhouse.backend.work.dto.effect.StructureChangeExecutionRequest;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MovementQuantityAllocator {

	private MovementQuantityAllocator() {
	}

	public static Map<Long, Integer> allocateMovedBySource(
			StructureChangeExecutionRequest request) {
		List<Long> sourceIds = request.sources().stream()
				.map(source -> source.sourceOrchidGroupId())
				.toList();
		Map<Long, Integer> sourceIndexById = new LinkedHashMap<>();
		for (int index = 0; index < sourceIds.size(); index++) {
			if (sourceIndexById.put(sourceIds.get(index), index) != null) {
				throw new IllegalArgumentException("작업 원본 난 묶음은 중복될 수 없습니다.");
			}
		}

		int sourceCount = sourceIds.size();
		int resultCount = request.results().size();
		int sink = sourceCount + resultCount + 1;
		int[][] capacity = new int[sink + 1][sink + 1];
		for (int sourceIndex = 0; sourceIndex < sourceCount; sourceIndex++) {
			capacity[0][sourceIndex + 1] = request.sources().get(sourceIndex).inputQuantity();
		}
		for (int resultIndex = 0; resultIndex < resultCount; resultIndex++) {
			var result = request.results().get(resultIndex);
			if (result.sourceOrchidGroupIds() == null || result.sourceOrchidGroupIds().isEmpty()) {
				throw new IllegalArgumentException("자리 이동 결과에는 원본 난 묶음이 한 개 이상 필요합니다.");
			}
			int resultNode = sourceCount + resultIndex + 1;
			for (Long sourceId : result.sourceOrchidGroupIds()) {
				Integer sourceIndex = sourceIndexById.get(sourceId);
				if (sourceIndex == null) {
					throw new IllegalArgumentException("이동 결과의 원본은 이번 실행 대상이어야 합니다.");
				}
				capacity[sourceIndex + 1][resultNode] = result.quantity();
			}
			capacity[resultNode][sink] = result.quantity();
		}

		int[][] residual = copy(capacity);
		int totalMoved = maxFlow(residual, sink);
		int requestedMoved = request.results().stream()
				.mapToInt(result -> result.quantity())
				.sum();
		if (totalMoved != requestedMoved) {
			throw new IllegalArgumentException(
					"이동 결과 수량을 선택한 원본 난 묶음의 작업 수량으로 구성할 수 없습니다.");
		}

		Map<Long, Integer> movedBySourceId = new LinkedHashMap<>();
		for (int sourceIndex = 0; sourceIndex < sourceCount; sourceIndex++) {
			int inputQuantity = capacity[0][sourceIndex + 1];
			movedBySourceId.put(
					sourceIds.get(sourceIndex),
					inputQuantity - residual[0][sourceIndex + 1]);
		}
		return movedBySourceId;
	}

	private static int maxFlow(int[][] residual, int sink) {
		int total = 0;
		int[] parent = new int[residual.length];
		while (findPath(residual, sink, parent)) {
			int flow = Integer.MAX_VALUE;
			for (int node = sink; node != 0; node = parent[node]) {
				flow = Math.min(flow, residual[parent[node]][node]);
			}
			for (int node = sink; node != 0; node = parent[node]) {
				residual[parent[node]][node] -= flow;
				residual[node][parent[node]] += flow;
			}
			total += flow;
		}
		return total;
	}

	private static boolean findPath(int[][] residual, int sink, int[] parent) {
		java.util.Arrays.fill(parent, -1);
		parent[0] = 0;
		var queue = new ArrayDeque<Integer>();
		queue.add(0);
		while (!queue.isEmpty()) {
			int current = queue.removeFirst();
			for (int next = 0; next < residual.length; next++) {
				if (parent[next] == -1 && residual[current][next] > 0) {
					parent[next] = current;
					if (next == sink) {
						return true;
					}
					queue.addLast(next);
				}
			}
		}
		return false;
	}

	private static int[][] copy(int[][] source) {
		int[][] copy = new int[source.length][];
		for (int index = 0; index < source.length; index++) {
			copy[index] = source[index].clone();
		}
		return copy;
	}
}
