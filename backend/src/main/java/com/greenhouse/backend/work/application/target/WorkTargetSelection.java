package com.greenhouse.backend.work.application.target;

import com.greenhouse.backend.work.domain.operation.WorkSourceScopeType;
import com.greenhouse.backend.work.domain.target.WorkTargetInclusionSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record WorkTargetSelection(
		WorkSourceScopeType sourceScopeType,
		Long sourceScopeId,
		String sourceDerivedGroupKey,
		List<Long> sourceOrchidGroupIds) {

	public WorkTargetSelection {
		if (sourceScopeType == null) {
			throw new IllegalArgumentException("작업 대상 범위 유형이 필요합니다.");
		}
		sourceDerivedGroupKey = normalize(sourceDerivedGroupKey);
		sourceOrchidGroupIds = sourceOrchidGroupIds == null
				? List.of()
				: sourceOrchidGroupIds.stream().filter(Objects::nonNull).distinct().toList();
		switch (sourceScopeType) {
			case FARM -> {
				if (sourceScopeId != null) {
					throw new IllegalArgumentException("전체 농장 작업에는 대상 범위 ID를 지정할 수 없습니다.");
				}
			}
			case HOUSE, PHYSICAL_BED, BED_ZONE, ORCHID_GROUP, USER_COLLECTION -> {
				if (sourceScopeId == null) {
					throw new IllegalArgumentException("선택한 작업 대상의 ID가 필요합니다.");
				}
			}
			case DERIVED_GROUP -> {
				if (sourceDerivedGroupKey == null) {
					throw new IllegalArgumentException("자동 그룹 키가 필요합니다.");
				}
			}
			case MANUAL_SELECTION -> {
				if (sourceOrchidGroupIds.isEmpty()) {
					throw new IllegalArgumentException("직접 선택한 난 묶음이 한 개 이상 필요합니다.");
				}
			}
			default -> throw new IllegalArgumentException("아직 지원하지 않는 작업 대상 유형입니다.");
		}
		if (sourceScopeType == WorkSourceScopeType.ORCHID_GROUP) {
			sourceOrchidGroupIds = List.of(sourceScopeId);
		}
	}

	public static WorkTargetSelection from(WorkTargetSelectionInput input) {
		return new WorkTargetSelection(
				input.sourceScopeType(),
				input.sourceScopeId(),
				input.sourceDerivedGroupKey(),
				input.sourceOrchidGroupIds());
	}

	public static WorkTargetSelection identifiedScope(
			WorkSourceScopeType sourceScopeType,
			Long sourceScopeId) {
		return new WorkTargetSelection(sourceScopeType, sourceScopeId, null, List.of());
	}

	public static WorkTargetSelection manualSelection(List<Long> orchidGroupIds) {
		return new WorkTargetSelection(
				WorkSourceScopeType.MANUAL_SELECTION, null, null, orchidGroupIds);
	}

	public static WorkTargetSelection orchidGroup(Long orchidGroupId) {
		return identifiedScope(WorkSourceScopeType.ORCHID_GROUP, orchidGroupId);
	}

	public Map<String, Object> conditionSnapshot() {
		Map<String, Object> snapshot = new LinkedHashMap<>();
		switch (sourceScopeType) {
			case FARM -> snapshot.put("farm", true);
			case HOUSE -> snapshot.put("houseId", sourceScopeId);
			case PHYSICAL_BED -> snapshot.put("physicalBedId", sourceScopeId);
			case BED_ZONE -> snapshot.put("bedZoneId", sourceScopeId);
			case ORCHID_GROUP -> snapshot.put("orchidGroupId", sourceScopeId);
			case USER_COLLECTION -> snapshot.put("collectionId", sourceScopeId);
			case DERIVED_GROUP -> snapshot.put("groupKey", sourceDerivedGroupKey);
			case MANUAL_SELECTION -> snapshot.put("orchidGroupIds", sourceOrchidGroupIds);
			default -> throw new IllegalArgumentException("아직 지원하지 않는 작업 대상 유형입니다.");
		}
		return snapshot;
	}

	public WorkTargetInclusionSource inclusionSource() {
		return switch (sourceScopeType) {
			case FARM -> WorkTargetInclusionSource.FARM;
			case HOUSE -> WorkTargetInclusionSource.HOUSE;
			case PHYSICAL_BED -> WorkTargetInclusionSource.PHYSICAL_BED;
			case BED_ZONE -> WorkTargetInclusionSource.BED_ZONE;
			case ORCHID_GROUP -> WorkTargetInclusionSource.DIRECT;
			case DERIVED_GROUP -> WorkTargetInclusionSource.DERIVED_GROUP;
			case USER_COLLECTION -> WorkTargetInclusionSource.USER_COLLECTION;
			case MANUAL_SELECTION -> WorkTargetInclusionSource.MANUAL_ADDITION;
			default -> throw new IllegalArgumentException("아직 지원하지 않는 작업 대상 유형입니다.");
		};
	}

	private static String normalize(String value) {
		if (value == null) {
			return null;
		}
		String normalized = value.trim();
		return normalized.isEmpty() ? null : normalized;
	}
}
