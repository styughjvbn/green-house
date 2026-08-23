package com.greenhouse.backend.farm.application.orchid.mutation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.greenhouse.backend.audit.application.OrchidGroupAuditHistoryEvent;
import com.greenhouse.backend.audit.application.OrchidGroupAuditHistoryReader;
import com.greenhouse.backend.audit.domain.AuditAction;
import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntryRole;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSource;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationType;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupHistoricalStateRow;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.work.application.effect.HistoricalWorkEffect;
import com.greenhouse.backend.work.application.effect.HistoricalWorkEffectLink;
import com.greenhouse.backend.work.application.effect.WorkHistoricalEffectService;
import com.greenhouse.backend.work.domain.effect.WorkEffectOrchidGroupRelationType;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ORCHID-CUTOVER: TRANSITION_ONLY — legacy 사실을 Historical Mutation plan으로 변환한다.
 * Removal gate: 최종 historical catch-up과 검증 완료.
 */
@Service
@RequiredArgsConstructor
public class OrchidGroupHistoryMigrationPlanner {

	private static final int BATCH_SIZE = 500;
	private static final TypeReference<List<Map<String, Object>>> MAP_LIST_TYPE = new TypeReference<>() { };

	private final WorkHistoricalEffectService workEffectService;
	private final OrchidGroupAuditHistoryReader auditHistoryReader;
	private final OrchidGroupRepository orchidGroupRepository;
	private final OrchidGroupHistoricalSalesReferenceInspector salesReferenceInspector;
	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	@Transactional(readOnly = true)
	public OrchidGroupHistoryMigrationPlan build(
			Instant sourceCutoff,
			OrchidGroupHistoryMigrationManifest manifest) {
		if (sourceCutoff == null || manifest == null) {
			throw new IllegalArgumentException("Historical migration plan 입력이 필요합니다.");
		}
		List<HistoricalWorkEffect> workEffects = loadWorkEffects(sourceCutoff);
		List<OrchidGroupAuditHistoryEvent> auditEvents = loadAuditEvents(sourceCutoff);
		List<OrchidGroupHistoricalStateRow> groups = loadGroups();
		OrchidGroupHistoricalSalesReferenceCounts salesReferenceCounts = salesReferenceInspector.inspect();
		validateSalesReference(manifest.legacySalesReference(), salesReferenceCounts);

		List<OrchidGroupHistoricalMutationInput> candidates = new ArrayList<>();
		List<OrchidGroupHistoryMigrationWorkLink> workLinks = new ArrayList<>();
		workEffects.forEach(effect -> {
			OrchidGroupHistoricalMutationInput candidate = fromWorkEffect(effect);
			candidates.add(candidate);
			workLinks.add(new OrchidGroupHistoryMigrationWorkLink(
					effect.effectId(),
					effect.workOperationId(),
					candidate.source(),
					effect.links().stream()
							.filter(link -> link.relationType() == WorkEffectOrchidGroupRelationType.SOURCE)
							.map(HistoricalWorkEffectLink::orchidGroupId)
							.collect(java.util.stream.Collectors.toSet()),
					effect.links().stream()
							.filter(link -> link.relationType() != WorkEffectOrchidGroupRelationType.SOURCE)
							.map(HistoricalWorkEffectLink::orchidGroupId)
							.collect(java.util.stream.Collectors.toSet())));
		});
		auditEvents.stream().map(this::fromAuditEvent).forEach(candidates::add);
		manifest.attestedQuantityCorrections().stream()
				.map(this::fromAttestation)
				.forEach(candidates::add);

		ReplayState replay = replayState(candidates);
		Map<Long, OrchidGroupHistoricalStateRow> groupsById = new LinkedHashMap<>();
		groups.forEach(group -> groupsById.put(group.orchidGroupId(), group));
		validateReferencedGroups(candidates, groupsById);
		addSyntheticOrigins(candidates, replay, groups);
		validateReplay(candidates, groupsById);
		validateUniqueSources(candidates);

		candidates.sort(Comparator.comparing(OrchidGroupHistoricalMutationInput::occurredAt)
				.thenComparing(candidate -> sourceKey(candidate.source())));
		long entryCount = candidates.stream().mapToLong(candidate -> candidate.entries().size()).sum();
		long originCount = candidates.stream()
				.filter(candidate -> candidate.source().type().equals("LEGACY_ORIGIN"))
				.count();
		Map<String, Long> sourceCounts = orderedCounts(
				"ORCHID_GROUP", (long) groups.size(),
				"STATE_CHANGING_WORK_EFFECT", (long) workEffects.size(),
				"ORCHID_AUDIT_EVENT", (long) auditEvents.size(),
				"LEGACY_REFERENCE_SALES_SLIP", salesReferenceCounts.salesSlips(),
				"LEGACY_REFERENCE_SALES_ITEM", salesReferenceCounts.salesItems());
		Map<String, Long> plannedCounts = orderedCounts(
				OrchidGroupHistoryMigrationPlanCommand.MUTATIONS, (long) candidates.size(),
				OrchidGroupHistoryMigrationPlanCommand.ENTRIES, entryCount,
				"WORK_MUTATIONS", (long) workEffects.size(),
				"AUDIT_MUTATIONS", (long) auditEvents.size(),
				"SYNTHETIC_ORIGINS", originCount,
				"ATTESTED_CORRECTIONS", (long) manifest.attestedQuantityCorrections().size(),
				"UNCLASSIFIED_GAPS", 0L,
				"LEGACY_REFERENCE_SALES_SLIPS", manifest.legacySalesReference().expectedSlipCount(),
				"LEGACY_REFERENCE_SALES_ITEMS", manifest.legacySalesReference().expectedItemCount());
		return new OrchidGroupHistoryMigrationPlan(candidates, workLinks, sourceCounts, plannedCounts);
	}

	private void validateSalesReference(
			OrchidGroupHistoryMigrationManifest.LegacySalesReference expected,
			OrchidGroupHistoricalSalesReferenceCounts actual) {
		if (actual.salesSlips() != expected.expectedSlipCount()
				|| actual.salesItems() != expected.expectedItemCount()) {
			throw new ConflictException("Legacy Sales 참고자료 건수가 manifest와 다릅니다: slips="
					+ actual.salesSlips() + ", items=" + actual.salesItems());
		}
		if (actual.groupAllocations() != 0 || actual.inventoryMovements() != 0
				|| actual.groupSnapshots() != 0) {
			throw new ConflictException("난 묶음 귀속 Sales 자료는 LEGACY_REFERENCE_ONLY로 제외할 수 없습니다.");
		}
	}

	private List<HistoricalWorkEffect> loadWorkEffects(Instant sourceCutoff) {
		List<HistoricalWorkEffect> result = new ArrayList<>();
		long afterId = 0;
		while (true) {
			List<HistoricalWorkEffect> batch = workEffectService.findAfter(afterId, sourceCutoff, BATCH_SIZE);
			if (batch.isEmpty()) {
				return List.copyOf(result);
			}
			result.addAll(batch);
			afterId = batch.getLast().effectId();
		}
	}

	private List<OrchidGroupAuditHistoryEvent> loadAuditEvents(Instant sourceCutoff) {
		List<OrchidGroupAuditHistoryEvent> result = new ArrayList<>();
		long afterId = 0;
		while (true) {
			List<OrchidGroupAuditHistoryEvent> batch = auditHistoryReader.findAfter(
					afterId, sourceCutoff, BATCH_SIZE);
			if (batch.isEmpty()) {
				return List.copyOf(result);
			}
			result.addAll(batch);
			afterId = batch.getLast().auditEventId();
		}
	}

	private List<OrchidGroupHistoricalStateRow> loadGroups() {
		List<OrchidGroupHistoricalStateRow> result = new ArrayList<>();
		long afterId = 0;
		while (true) {
			List<OrchidGroupHistoricalStateRow> batch = orchidGroupRepository
					.findHistoricalStateRowsAfter(afterId, PageRequest.of(0, BATCH_SIZE));
			if (batch.isEmpty()) {
				return List.copyOf(result);
			}
			result.addAll(batch);
			afterId = batch.getLast().orchidGroupId();
		}
	}

	private OrchidGroupHistoricalMutationInput fromWorkEffect(HistoricalWorkEffect effect) {
		OrchidGroupMutationType mutationType = switch (effect.handlerCode()) {
			case "DISCARD" -> OrchidGroupMutationType.DISCARD;
			case "MOVE" -> OrchidGroupMutationType.MOVE;
			case "POTTING" -> OrchidGroupMutationType.CREATE;
			case "DIVIDE", "MOVEMENT", "REPOT" -> OrchidGroupMutationType.TRANSFORM;
			default -> throw new IllegalArgumentException(
					"지원하지 않는 historical Work handler입니다: " + effect.handlerCode());
		};
		if (effect.links().isEmpty()) {
			throw new ConflictException("Historical Work 효과에 난 묶음 link가 없습니다: " + effect.effectId());
		}
		List<OrchidGroupHistoricalEntryInput> entries = effect.links().stream()
				.map(link -> fromWorkLink(effect, link))
				.toList();
		return new OrchidGroupHistoricalMutationInput(
				mutationType,
				OrchidGroupMutationSources.work(effect.workOperationId(), effect.effectKey()),
				effect.appliedAt(),
				businessDate(effect.appliedAt()),
				"Legacy Work effect " + effect.handlerCode(),
				entries,
				workPayload(effect));
	}

	private OrchidGroupHistoricalEntryInput fromWorkLink(
			HistoricalWorkEffect effect,
			HistoricalWorkEffectLink link) {
		return switch (effect.handlerCode()) {
			case "DISCARD" -> discardEntry(effect, link);
			case "MOVE" -> moveEntry(effect, link);
			case "DIVIDE", "MOVEMENT", "REPOT" -> transformEntry(effect, link);
			case "POTTING" -> pottingEntry(effect, link);
			default -> throw new IllegalArgumentException("지원하지 않는 Work historical Entry입니다.");
		};
	}

	private OrchidGroupHistoricalEntryInput discardEntry(
			HistoricalWorkEffect effect,
			HistoricalWorkEffectLink link) {
		Map<String, Object> result = effect.resultDetails();
		requireGroup(result, "orchidGroupId", link.orchidGroupId(), effect.effectId());
		int discarded = intValue(result.get("discardedQuantity"), "discardedQuantity");
		return historicalEntry(
				link.orchidGroupId(),
				OrchidGroupMutationEntryRole.AFFECTED,
				null,
				-discarded);
	}

	private OrchidGroupHistoricalEntryInput moveEntry(
			HistoricalWorkEffect effect,
			HistoricalWorkEffectLink link) {
		Map<String, Object> result = effect.resultDetails();
		requireGroup(result, "orchidGroupId", link.orchidGroupId(), effect.effectId());
		return historicalEntry(
				link.orchidGroupId(),
				OrchidGroupMutationEntryRole.AFFECTED,
				null,
				null);
	}

	private OrchidGroupHistoricalEntryInput transformEntry(
			HistoricalWorkEffect effect,
			HistoricalWorkEffectLink link) {
		if (link.relationType() == WorkEffectOrchidGroupRelationType.SOURCE) {
			Map<String, Object> sourceRow = findByGroupId(
					listOfMaps(effect.commandDetails().get("sources")),
					"sourceOrchidGroupId",
					link.orchidGroupId(),
					effect.effectId());
			int inputQuantity = intValue(sourceRow.get("inputQuantity"), "inputQuantity");
			return historicalEntry(
					link.orchidGroupId(),
					OrchidGroupMutationEntryRole.SOURCE,
					null,
					-inputQuantity);
		}
		Map<String, Object> resultRow = findByGroupId(
				listOfMaps(effect.resultDetails().get("results")),
				"orchidGroupId",
				link.orchidGroupId(),
				effect.effectId());
		List<Map<String, Object>> resultRows = listOfMaps(effect.resultDetails().get("results"));
		int resultIndex = resultRows.indexOf(resultRow);
		List<Map<String, Object>> commandRows = listOfMaps(effect.commandDetails().get("results"));
		if (resultIndex < 0 || resultIndex >= commandRows.size()) {
			throw new ConflictException("Work 결과 command/result 순서를 대응할 수 없습니다: " + effect.effectId());
		}
		Object quantity = resultRow.get("quantity") != null
				? resultRow.get("quantity")
				: commandRows.get(resultIndex).get("quantity");
		return historicalEntry(
				link.orchidGroupId(),
				OrchidGroupMutationEntryRole.RESULT,
				intValue(quantity, "result quantity"),
				null);
	}

	private OrchidGroupHistoricalEntryInput pottingEntry(
			HistoricalWorkEffect effect,
			HistoricalWorkEffectLink link) {
		List<Object> groupIds = list(effect.resultDetails().get("createdOrchidGroupIds"));
		int index = -1;
		for (int candidateIndex = 0; candidateIndex < groupIds.size(); candidateIndex++) {
			if (longValue(groupIds.get(candidateIndex), "createdOrchidGroupId")
					.equals(link.orchidGroupId())) {
				index = candidateIndex;
				break;
			}
		}
		List<Map<String, Object>> resultCommands = listOfMaps(effect.commandDetails().get("results"));
		if (index < 0 || index >= resultCommands.size()) {
			throw new ConflictException("포트 Work 결과를 난 묶음과 대응할 수 없습니다: " + effect.effectId());
		}
		return historicalEntry(
				link.orchidGroupId(),
				OrchidGroupMutationEntryRole.RESULT,
				intValue(resultCommands.get(index).get("quantity"), "potting quantity"),
				null);
	}

	private OrchidGroupHistoricalMutationInput fromAuditEvent(OrchidGroupAuditHistoryEvent event) {
		OrchidGroupMutationType mutationType = switch (event.action()) {
			case CREATED -> OrchidGroupMutationType.CREATE;
			case UPDATED -> event.changedFields().equals(List.of("quantity"))
					? OrchidGroupMutationType.CORRECTION
					: OrchidGroupMutationType.UPDATE_DETAILS;
			case MOVED -> OrchidGroupMutationType.MOVE;
			case DIVIDED, MERGED -> OrchidGroupMutationType.TRANSFORM;
			default -> throw new ConflictException(
					"지원하지 않는 OrchidGroup Audit action입니다: " + event.action());
		};
		Integer creationQuantity = null;
		Integer quantityDelta = null;
		if (event.action() == AuditAction.CREATED && event.afterData().get("quantity") != null) {
			creationQuantity = intValue(event.afterData().get("quantity"), "created quantity");
		}
		if (event.beforeData().get("quantity") != null && event.afterData().get("quantity") != null) {
			quantityDelta = intValue(event.afterData().get("quantity"), "after quantity")
					- intValue(event.beforeData().get("quantity"), "before quantity");
		}
		Map<String, Object> sourcePayload = mapOf(
				"auditEventId", event.auditEventId(),
				"action", event.action().name(),
				"changedFields", event.changedFields(),
				"beforeData", event.beforeData(),
				"afterData", event.afterData(),
				"contextData", event.contextData());
		return new OrchidGroupHistoricalMutationInput(
				mutationType,
				OrchidGroupMutationSources.historicalAudit(event.auditEventId(), event.action().name()),
				event.occurredAt(),
				businessDate(event.occurredAt()),
				"Legacy OrchidGroup audit " + event.action(),
				List.of(historicalEntry(
						event.orchidGroupId(),
						event.action() == AuditAction.CREATED
								? OrchidGroupMutationEntryRole.RESULT
								: OrchidGroupMutationEntryRole.AFFECTED,
						creationQuantity,
						quantityDelta)),
				sourcePayload);
	}

	private OrchidGroupHistoricalMutationInput fromAttestation(
			OrchidGroupHistoryMigrationManifest.AttestedQuantityCorrection correction) {
		int delta = correction.afterQuantity() - correction.beforeQuantity();
		Map<String, Object> sourcePayload = mapOf(
				"attestedBy", correction.attestedBy(),
				"attestedOn", correction.attestedOn(),
				"reason", correction.reason(),
				"occurredAt", correction.occurredAt(),
				"beforeQuantity", correction.beforeQuantity(),
				"afterQuantity", correction.afterQuantity());
		return new OrchidGroupHistoricalMutationInput(
				OrchidGroupMutationType.CORRECTION,
				OrchidGroupMutationSources.migration(
						"OPERATOR_ATTESTATION",
						correction.orchidGroupId().toString(),
						"QUANTITY:" + correction.beforeQuantity() + "->" + correction.afterQuantity()),
				correction.occurredAt(),
				businessDate(correction.occurredAt()),
				correction.reason(),
				List.of(historicalEntry(
						correction.orchidGroupId(),
						OrchidGroupMutationEntryRole.AFFECTED,
						null,
						delta)),
				sourcePayload);
	}

	private void addSyntheticOrigins(
			List<OrchidGroupHistoricalMutationInput> candidates,
			ReplayState replay,
			List<OrchidGroupHistoricalStateRow> groups) {
		for (OrchidGroupHistoricalStateRow group : groups) {
			if (replay.creationQuantities().containsKey(group.orchidGroupId())) {
				continue;
			}
			int laterDelta = replay.quantityDeltas().getOrDefault(group.orchidGroupId(), 0);
			int originQuantity = group.quantity() - laterDelta;
			if (originQuantity < 0) {
				throw new ConflictException("합성 ORIGIN 수량을 역산할 수 없습니다: " + group.orchidGroupId());
			}
			Instant occurredAt = group.createdAt().toInstant(ZoneOffset.UTC);
			Map<String, Object> sourcePayload = mapOf(
					"orchidGroupId", group.orchidGroupId(),
					"currentQuantity", group.quantity(),
					"knownLaterQuantityDelta", laterDelta,
					"derivedOriginQuantity", originQuantity,
					"createdAt", occurredAt);
			candidates.add(new OrchidGroupHistoricalMutationInput(
					OrchidGroupMutationType.CREATE,
					OrchidGroupMutationSources.migration(
							"LEGACY_ORIGIN", group.orchidGroupId().toString(), "ORIGIN"),
					occurredAt,
					businessDate(occurredAt),
					"생성 근거가 없는 legacy 난 묶음 origin",
					List.of(historicalEntry(
							group.orchidGroupId(),
							OrchidGroupMutationEntryRole.RESULT,
							originQuantity,
							null)),
					sourcePayload));
		}
	}

	private ReplayState replayState(List<OrchidGroupHistoricalMutationInput> candidates) {
		Map<Long, Integer> creations = new HashMap<>();
		Map<Long, Integer> deltas = new HashMap<>();
		for (OrchidGroupHistoricalMutationInput candidate : candidates) {
			for (OrchidGroupHistoricalEntryInput entry : candidate.entries()) {
				if (entry.creationQuantity() != null) {
					Integer previous = creations.putIfAbsent(
							entry.orchidGroupId(), entry.creationQuantity());
					if (previous != null) {
						throw new ConflictException("난 묶음 생성 근거가 중복됩니다: " + entry.orchidGroupId());
					}
				}
				if (entry.quantityDelta() != null) {
					deltas.merge(entry.orchidGroupId(), entry.quantityDelta(), Integer::sum);
				}
			}
		}
		return new ReplayState(Map.copyOf(creations), Map.copyOf(deltas));
	}

	private void validateReferencedGroups(
			List<OrchidGroupHistoricalMutationInput> candidates,
			Map<Long, OrchidGroupHistoricalStateRow> groupsById) {
		Set<Long> referenced = new HashSet<>();
		candidates.forEach(candidate -> candidate.entries().forEach(entry ->
				referenced.add(entry.orchidGroupId())));
		referenced.removeAll(groupsById.keySet());
		if (!referenced.isEmpty()) {
			throw new ConflictException("Historical source가 없는 난 묶음을 참조합니다: " + referenced);
		}
	}

	private void validateReplay(
			List<OrchidGroupHistoricalMutationInput> candidates,
			Map<Long, OrchidGroupHistoricalStateRow> groupsById) {
		ReplayState replay = replayState(candidates);
		List<String> mismatches = new ArrayList<>();
		for (Map.Entry<Long, Integer> creation : replay.creationQuantities().entrySet()) {
			int replayQuantity = creation.getValue()
					+ replay.quantityDeltas().getOrDefault(creation.getKey(), 0);
			int currentQuantity = groupsById.get(creation.getKey()).quantity();
			if (replayQuantity != currentQuantity) {
				mismatches.add(creation.getKey() + " replay=" + replayQuantity + " current=" + currentQuantity);
			}
		}
		if (!mismatches.isEmpty()) {
			throw new ConflictException("미분류 historical quantity gap이 존재합니다: "
					+ String.join(", ", mismatches));
		}
	}

	private void validateUniqueSources(List<OrchidGroupHistoricalMutationInput> candidates) {
		Set<String> sourceKeys = new HashSet<>();
		candidates.forEach(candidate -> {
			if (!sourceKeys.add(sourceKey(candidate.source()))) {
				throw new ConflictException("Historical Mutation source identity가 중복됩니다: "
						+ sourceKey(candidate.source()));
			}
		});
	}

	private OrchidGroupHistoricalEntryInput historicalEntry(
			Long groupId,
			OrchidGroupMutationEntryRole role,
			Integer creationQuantity,
			Integer quantityDelta) {
		return new OrchidGroupHistoricalEntryInput(groupId, role, creationQuantity, quantityDelta);
	}

	private Map<String, Object> workPayload(HistoricalWorkEffect effect) {
		return mapOf(
				"effectId", effect.effectId(),
				"workOperationId", effect.workOperationId(),
				"effectKey", effect.effectKey(),
				"handlerCode", effect.handlerCode(),
				"appliedAt", effect.appliedAt(),
				"commandDetails", effect.commandDetails(),
				"resultDetails", effect.resultDetails(),
				"links", effect.links());
	}

	private Map<String, Object> findByGroupId(
			List<Map<String, Object>> rows,
			String key,
			Long groupId,
			Long effectId) {
		return rows.stream()
				.filter(row -> groupId.equals(longValue(row.get(key), key)))
				.findFirst()
				.orElseThrow(() -> new ConflictException(
						"Work payload에서 난 묶음을 찾을 수 없습니다: effect=" + effectId
								+ ", group=" + groupId));
	}

	private void requireGroup(
			Map<String, Object> row,
			String key,
			Long expectedGroupId,
			Long effectId) {
		if (!expectedGroupId.equals(longValue(row.get(key), key))) {
			throw new ConflictException("Work link와 result 대상이 다릅니다: " + effectId);
		}
	}

	private List<Map<String, Object>> listOfMaps(Object value) {
		return value == null ? List.of() : objectMapper.convertValue(value, MAP_LIST_TYPE);
	}

	private List<Object> list(Object value) {
		if (value instanceof List<?> values) {
			return new ArrayList<>(values);
		}
		throw new ConflictException("Historical source의 배열 payload 형식이 올바르지 않습니다.");
	}

	private int intValue(Object value, String label) {
		if (value instanceof Number number) {
			return number.intValue();
		}
		if (value instanceof String string) {
			return Integer.parseInt(string);
		}
		throw new ConflictException(label + " 값이 숫자가 아닙니다.");
	}

	private Long longValue(Object value, String label) {
		if (value instanceof Number number) {
			return number.longValue();
		}
		if (value instanceof String string) {
			return Long.valueOf(string);
		}
		throw new ConflictException(label + " 값이 숫자가 아닙니다.");
	}

	private java.time.LocalDate businessDate(Instant occurredAt) {
		return occurredAt.atZone(TimeConfig.FARM_TIME_ZONE).toLocalDate();
	}

	private String sourceKey(OrchidGroupMutationSource source) {
		return source.domain() + ":" + source.type() + ":" + source.referenceId()
				+ ":" + source.operationKey();
	}

	private Map<String, Object> mapOf(Object... keysAndValues) {
		if (keysAndValues.length % 2 != 0) {
			throw new IllegalArgumentException("Map key/value 개수가 일치하지 않습니다.");
		}
		Map<String, Object> result = new LinkedHashMap<>();
		for (int index = 0; index < keysAndValues.length; index += 2) {
			result.put((String) keysAndValues[index], keysAndValues[index + 1]);
		}
		return Collections.unmodifiableMap(result);
	}

	private Map<String, Long> orderedCounts(Object... keysAndValues) {
		Map<String, Long> result = new LinkedHashMap<>();
		for (int index = 0; index < keysAndValues.length; index += 2) {
			result.put((String) keysAndValues[index], (Long) keysAndValues[index + 1]);
		}
		return Collections.unmodifiableMap(result);
	}

	private record ReplayState(
			Map<Long, Integer> creationQuantities,
			Map<Long, Integer> quantityDeltas) {
	}
}
