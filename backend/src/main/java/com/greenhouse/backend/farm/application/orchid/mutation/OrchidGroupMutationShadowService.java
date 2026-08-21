package com.greenhouse.backend.farm.application.orchid.mutation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupShadowComparisonStatus;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupStateSnapshot;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrchidGroupMutationShadowService {

	private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

	private final OrchidGroupMutationRoutingPolicy routingPolicy;
	private final OrchidGroupShadowPlanner planner;
	private final OrchidGroupMutationCommandFingerprint commandFingerprint;
	private final OrchidGroupRepository orchidGroupRepository;
	private final ApplicationEventPublisher eventPublisher;
	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	@Transactional(readOnly = true)
	public OrchidGroupShadowPlan prepare(Object command) {
		if (!routingPolicy.capturesShadowComparison()) {
			return null;
		}
		try {
			return planner.plan(command);
		} catch (RuntimeException exception) {
			log.warn("OrchidGroup shadow engine plan rejected: {}", exception.getMessage());
			try {
				return new OrchidGroupShadowPlan(
						planner.source(command),
						planner.mutationType(command),
						commandFingerprint.calculate(command),
						planner.payload(command),
						List.of(),
						describe(exception));
			} catch (RuntimeException metadataException) {
				log.error("OrchidGroup shadow rejection metadata 생성에 실패했습니다.", metadataException);
				return null;
			}
		}
	}

	public void complete(OrchidGroupShadowPlan plan) {
		complete(plan, List.of(), Set.of());
	}

	public void completeCreated(OrchidGroupShadowPlan plan, Collection<Long> createdOrchidGroupIds) {
		complete(plan, createdOrchidGroupIds, Set.of());
	}

	public void completeDeleted(OrchidGroupShadowPlan plan, Collection<Long> deletedOrchidGroupIds) {
		complete(plan, List.of(), new HashSet<>(deletedOrchidGroupIds));
	}

	private void complete(
			OrchidGroupShadowPlan plan,
			Collection<Long> createdOrchidGroupIds,
			Set<Long> deletedOrchidGroupIds) {
		if (plan == null) {
			return;
		}
		try {
			ShadowAssessment assessment = assess(plan, List.copyOf(createdOrchidGroupIds), deletedOrchidGroupIds);
			eventPublisher.publishEvent(new OrchidGroupShadowComparisonEvent(
					plan.source(),
					plan.mutationType(),
					plan.commandFingerprint(),
					assessment.status(),
					plan.commandPayload(),
					serializeEntries(plan.entries()),
					assessment.actualEntries(),
					assessment.mismatches(),
					plan.engineError()));
		} catch (RuntimeException exception) {
			log.error("OrchidGroup shadow 비교 생성에 실패했습니다. Legacy 결과는 유지됩니다.", exception);
		}
	}

	private ShadowAssessment assess(
			OrchidGroupShadowPlan plan,
			List<Long> createdOrchidGroupIds,
			Set<Long> deletedOrchidGroupIds) {
		Set<Long> expectedExistingIds = plan.entries().stream()
				.map(OrchidGroupShadowPlan.Entry::orchidGroupId)
				.filter(java.util.Objects::nonNull)
				.filter(id -> !deletedOrchidGroupIds.contains(id))
				.collect(Collectors.toSet());
		Set<Long> actualIds = new HashSet<>(expectedExistingIds);
		actualIds.addAll(createdOrchidGroupIds);
		Map<Long, OrchidGroup> actualGroups = orchidGroupRepository.findAllById(actualIds).stream()
				.collect(Collectors.toMap(OrchidGroup::getId, Function.identity()));

		List<Map<String, Object>> actualEntries = new ArrayList<>();
		List<Map<String, Object>> mismatches = new ArrayList<>();
		int createdIndex = 0;
		for (OrchidGroupShadowPlan.Entry expected : plan.entries()) {
			Long actualId = expected.orchidGroupId();
			if (actualId == null && createdIndex < createdOrchidGroupIds.size()) {
				actualId = createdOrchidGroupIds.get(createdIndex++);
			}
			OrchidGroup actualGroup = actualId == null || deletedOrchidGroupIds.contains(actualId)
					? null
					: actualGroups.get(actualId);
			OrchidGroupStateSnapshot actualState = actualGroup == null
					? null
					: OrchidGroupStateSnapshot.from(actualGroup);
			actualEntries.add(entryMap(expected.ordinal(), actualId, expected.role().name(), actualState));
			if (!java.util.Objects.equals(expected.afterState(), actualState)) {
				mismatches.add(mismatchMap(
						expected.ordinal(),
						actualId,
						expected.afterState(),
						actualState,
						deletedOrchidGroupIds.contains(actualId)
								? "LEGACY_PHYSICAL_DELETE"
								: "STATE_MISMATCH"));
			}
		}
		if (createdIndex < createdOrchidGroupIds.size()) {
			for (int index = createdIndex; index < createdOrchidGroupIds.size(); index++) {
				Long unexpectedId = createdOrchidGroupIds.get(index);
				OrchidGroup group = actualGroups.get(unexpectedId);
				mismatches.add(mismatchMap(
						plan.entries().size() + index,
						unexpectedId,
						null,
						group == null ? null : OrchidGroupStateSnapshot.from(group),
						"UNEXPECTED_RESULT"));
			}
		}
		long expectedCreatedCount = plan.entries().stream()
				.filter(entry -> entry.orchidGroupId() == null).count();
		if (expectedCreatedCount > createdOrchidGroupIds.size()) {
			mismatches.add(countMismatch(expectedCreatedCount, createdOrchidGroupIds.size()));
		}

		OrchidGroupShadowComparisonStatus status = !plan.acceptedByEnginePlan()
				? OrchidGroupShadowComparisonStatus.ENGINE_REJECTED
				: mismatches.isEmpty()
						? OrchidGroupShadowComparisonStatus.MATCHED
						: OrchidGroupShadowComparisonStatus.MISMATCHED;
		return new ShadowAssessment(status, actualEntries, mismatches);
	}

	private List<Map<String, Object>> serializeEntries(List<OrchidGroupShadowPlan.Entry> entries) {
		return entries.stream()
				.map(entry -> objectMapper.convertValue(entry, MAP_TYPE))
				.toList();
	}

	private Map<String, Object> entryMap(
			int ordinal,
			Long orchidGroupId,
			String role,
			OrchidGroupStateSnapshot afterState) {
		Map<String, Object> entry = new LinkedHashMap<>();
		entry.put("ordinal", ordinal);
		entry.put("orchidGroupId", orchidGroupId);
		entry.put("role", role);
		entry.put("afterState", afterState);
		return entry;
	}

	private Map<String, Object> mismatchMap(
			int ordinal,
			Long orchidGroupId,
			OrchidGroupStateSnapshot expected,
			OrchidGroupStateSnapshot actual,
			String code) {
		Map<String, Object> mismatch = new LinkedHashMap<>();
		mismatch.put("code", code);
		mismatch.put("ordinal", ordinal);
		mismatch.put("orchidGroupId", orchidGroupId);
		mismatch.put("expected", expected);
		mismatch.put("actual", actual);
		return mismatch;
	}

	private Map<String, Object> countMismatch(long expectedCount, long actualCount) {
		Map<String, Object> mismatch = new LinkedHashMap<>();
		mismatch.put("code", "MISSING_RESULT");
		mismatch.put("expectedCount", expectedCount);
		mismatch.put("actualCount", actualCount);
		return mismatch;
	}

	private String describe(RuntimeException exception) {
		String message = exception.getMessage();
		return exception.getClass().getSimpleName() + (message == null ? "" : ": " + message);
	}

	private record ShadowAssessment(
			OrchidGroupShadowComparisonStatus status,
			List<Map<String, Object>> actualEntries,
			List<Map<String, Object>> mismatches) {
	}
}
