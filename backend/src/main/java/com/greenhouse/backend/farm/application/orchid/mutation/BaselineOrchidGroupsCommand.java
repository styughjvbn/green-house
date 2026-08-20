package com.greenhouse.backend.farm.application.orchid.mutation;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record BaselineOrchidGroupsCommand(
		UUID cutoverKey,
		String batchKey,
		List<Long> orchidGroupIds,
		LocalDate effectiveBusinessDate) {

	public BaselineOrchidGroupsCommand {
		if (cutoverKey == null || effectiveBusinessDate == null) {
			throw new IllegalArgumentException("Baseline cutover key와 업무일이 필요합니다.");
		}
		if (batchKey == null || batchKey.isBlank()) {
			throw new IllegalArgumentException("Baseline batch key가 필요합니다.");
		}
		if (batchKey.trim().length() > 200) {
			throw new IllegalArgumentException("Baseline batch key는 200자 이하여야 합니다.");
		}
		if (orchidGroupIds == null || orchidGroupIds.isEmpty() || orchidGroupIds.stream().anyMatch(id -> id == null)) {
			throw new IllegalArgumentException("Baseline 대상 난 묶음 ID가 필요합니다.");
		}
		List<Long> normalizedIds = orchidGroupIds.stream().distinct().sorted().toList();
		if (normalizedIds.size() != orchidGroupIds.size()) {
			throw new IllegalArgumentException("Baseline 대상 난 묶음 ID는 중복될 수 없습니다.");
		}
		batchKey = batchKey.trim();
		orchidGroupIds = normalizedIds;
	}
}
