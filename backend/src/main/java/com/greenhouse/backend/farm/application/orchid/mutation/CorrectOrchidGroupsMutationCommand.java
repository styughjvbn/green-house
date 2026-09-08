package com.greenhouse.backend.farm.application.orchid.mutation;

import static com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationCommandNormalizer.normalizeText;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSource;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

public record CorrectOrchidGroupsMutationCommand(OrchidGroupMutationSource source,
		List<CorrectOrchidGroupMutationItem> items, RelatedOrchidGroupMutations correctedMutations,
		LocalDate effectiveBusinessDate, String reason) implements OrchidGroupMutationCommand {

	public CorrectOrchidGroupsMutationCommand {
		if (source == null || correctedMutations == null || effectiveBusinessDate == null) {
			throw new IllegalArgumentException("보정 Mutation의 source, 원인과 업무일이 필요합니다.");
		}
		if (items == null || items.isEmpty() || items.stream().anyMatch(item -> item == null)) {
			throw new IllegalArgumentException("보정 Mutation 대상 난 묶음이 필요합니다.");
		}
		long distinctGroupCount = items.stream().map(CorrectOrchidGroupMutationItem::orchidGroupId).distinct().count();
		if (distinctGroupCount != items.size()) {
			throw new IllegalArgumentException("보정 Mutation 대상 난 묶음은 중복될 수 없습니다.");
		}
		items = items.stream().sorted(Comparator.comparing(CorrectOrchidGroupMutationItem::orchidGroupId)).toList();
		reason = normalizeText(reason);
	}
}
