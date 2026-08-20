package com.greenhouse.backend.farm.application.orchid.mutation;

import java.util.Comparator;
import java.util.List;

public record RelatedOrchidGroupMutations(
		boolean legacySource,
		List<Long> mutationIds) {

	public RelatedOrchidGroupMutations {
		if (mutationIds == null || mutationIds.stream().anyMatch(id -> id == null || id < 1)) {
			throw new IllegalArgumentException("관련 Mutation ID가 올바르지 않습니다.");
		}
		long distinctCount = mutationIds.stream().distinct().count();
		if (distinctCount != mutationIds.size()) {
			throw new IllegalArgumentException("관련 Mutation ID는 중복될 수 없습니다.");
		}
		if (legacySource && !mutationIds.isEmpty()) {
			throw new IllegalArgumentException("Legacy source에는 관련 Mutation을 지정할 수 없습니다.");
		}
		if (!legacySource && mutationIds.isEmpty()) {
			throw new IllegalArgumentException("현재 source에는 관련 Mutation이 필요합니다.");
		}
		mutationIds = mutationIds.stream().sorted(Comparator.naturalOrder()).toList();
	}

	public static RelatedOrchidGroupMutations legacy() {
		return new RelatedOrchidGroupMutations(true, List.of());
	}

	public static RelatedOrchidGroupMutations current(List<Long> mutationIds) {
		return new RelatedOrchidGroupMutations(false, mutationIds);
	}
}
