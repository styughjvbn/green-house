package com.greenhouse.backend.farm.application.orchid.mutation;

import static com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationCommandNormalizer.normalizeText;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSource;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public record TransformOrchidGroupsMutationCommand(OrchidGroupMutationSource source,
		List<TransformOrchidGroupMutationSource> sources, List<TransformOrchidGroupMutationResult> results,
		LocalDate effectiveBusinessDate, String reason,
		Set<Long> placementExclusionOrchidGroupIds) implements OrchidGroupMutationCommand {

	public TransformOrchidGroupsMutationCommand(OrchidGroupMutationSource source,
			List<TransformOrchidGroupMutationSource> sources, List<TransformOrchidGroupMutationResult> results,
			LocalDate effectiveBusinessDate, String reason) {
		this(source, sources, results, effectiveBusinessDate, reason, Set.of());
	}

	public TransformOrchidGroupsMutationCommand {
		if (source == null || effectiveBusinessDate == null) {
			throw new IllegalArgumentException("구조 변경 Mutation의 source와 업무일이 필요합니다.");
		}
		if (sources == null || sources.isEmpty() || sources.stream().anyMatch(item -> item == null)) {
			throw new IllegalArgumentException("구조 변경 원본이 필요합니다.");
		}
		if (results == null || results.isEmpty() || results.stream().anyMatch(item -> item == null)) {
			throw new IllegalArgumentException("구조 변경 결과가 필요합니다.");
		}
		long distinctSourceCount = sources.stream()
			.map(TransformOrchidGroupMutationSource::orchidGroupId)
			.distinct()
			.count();
		if (distinctSourceCount != sources.size()) {
			throw new IllegalArgumentException("구조 변경 원본 난 묶음은 중복될 수 없습니다.");
		}
		sources = sources.stream()
			.sorted(Comparator.comparing(TransformOrchidGroupMutationSource::orchidGroupId))
			.toList();
		results = List.copyOf(results);
		reason = normalizeText(reason);
		placementExclusionOrchidGroupIds = placementExclusionOrchidGroupIds == null ? Set.of()
				: Set.copyOf(placementExclusionOrchidGroupIds);
		if (placementExclusionOrchidGroupIds.stream().anyMatch(id -> id == null || id < 1)) {
			throw new IllegalArgumentException("배치 검사 제외 난 묶음 ID가 올바르지 않습니다.");
		}
	}
}
