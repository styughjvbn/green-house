package com.greenhouse.backend.farm.application.orchid.mutation;

import java.util.Comparator;
import java.util.List;

final class OrchidGroupQuantityMutationCommandNormalizer {

	private OrchidGroupQuantityMutationCommandNormalizer() {
	}

	static List<OrchidGroupQuantityMutationItem> normalizeItems(List<OrchidGroupQuantityMutationItem> items,
			String commandLabel) {
		if (items == null || items.isEmpty() || items.stream().anyMatch(item -> item == null)) {
			throw new IllegalArgumentException(commandLabel + " 대상 난 묶음이 필요합니다.");
		}
		long distinctGroupCount = items.stream().map(OrchidGroupQuantityMutationItem::orchidGroupId).distinct().count();
		if (distinctGroupCount != items.size()) {
			throw new IllegalArgumentException(commandLabel + " 대상 난 묶음은 중복될 수 없습니다.");
		}
		return items.stream().sorted(Comparator.comparing(OrchidGroupQuantityMutationItem::orchidGroupId)).toList();
	}

}
