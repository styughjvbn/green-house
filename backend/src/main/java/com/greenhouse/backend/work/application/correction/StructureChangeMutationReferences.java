package com.greenhouse.backend.work.application.correction;

import java.util.List;

public record StructureChangeMutationReferences(
		boolean legacySource,
		List<Long> mutationIds) {

	public StructureChangeMutationReferences {
		mutationIds = mutationIds == null ? List.of() : List.copyOf(mutationIds);
	}

	public static StructureChangeMutationReferences legacy() {
		return new StructureChangeMutationReferences(true, List.of());
	}

	public static StructureChangeMutationReferences current(List<Long> mutationIds) {
		return new StructureChangeMutationReferences(false, mutationIds);
	}
}
