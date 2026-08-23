package com.greenhouse.backend.farm.application.orchid.mutation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.application.structure.OrchidPlacementPolicy;
import com.greenhouse.backend.farm.domain.inbound.InboundRecord;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroupStateSimulation;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntryRole;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutation;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSource;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationType;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupStateSnapshot;
import com.greenhouse.backend.farm.domain.structure.BedZone;
import com.greenhouse.backend.farm.domain.variety.Variety;
import com.greenhouse.backend.farm.repository.inbound.InboundRecordRepository;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationEntryRepository;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationRepository;
import com.greenhouse.backend.farm.repository.structure.BedZoneRepository;
import com.greenhouse.backend.farm.repository.variety.VarietyRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * ORCHID-CUTOVER: TRANSITION_ONLY — 실제 상태를 쓰지 않는 SHADOW plan을 계산한다.
 * Removal gate: 운영 ACTIVE 안정화 및 SHADOW 결과 승인.
 */
@Service
@RequiredArgsConstructor
public class OrchidGroupShadowPlanner {

	private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

	private final OrchidGroupRepository orchidGroupRepository;
	private final InboundRecordRepository inboundRecordRepository;
	private final BedZoneRepository bedZoneRepository;
	private final VarietyRepository varietyRepository;
	private final OrchidGroupMutationRepository mutationRepository;
	private final OrchidGroupMutationEntryRepository entryRepository;
	private final OrchidPlacementPolicy orchidPlacementPolicy;
	private final OrchidGroupMutationCommandFingerprint commandFingerprint;
	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	public OrchidGroupShadowPlan plan(Object command) {
		return switch (command) {
			case CreateOrchidGroupMutationCommand value -> planCreate(value);
			case CreateOrchidGroupsMutationCommand value -> planCreateMany(value);
			case CreateInboundOrchidGroupsMutationCommand value -> planCreateInbound(value);
			case TransformOrchidGroupsMutationCommand value -> planTransform(value);
			case UpdateOrchidGroupMutationCommand value -> planUpdate(value);
			case MoveOrchidGroupMutationCommand value -> planMove(value);
			case CancelOrchidGroupCreationMutationCommand value -> planCancel(value);
			case DiscardOrchidGroupMutationCommand value -> planDiscard(value);
			case ReserveOrchidGroupsMutationCommand value -> planQuantity(
					value, OrchidGroupMutationType.RESERVE, value.source(), value.items(),
					null, Set.of(),
					OrchidGroupStateSimulation::reserve);
			case ReleaseOrchidGroupReservationsMutationCommand value -> planQuantity(
					value, OrchidGroupMutationType.RELEASE_RESERVATION, value.source(), value.items(),
					null, Set.of(),
					OrchidGroupStateSimulation::releaseReserved);
			case ConsumeOrchidGroupReservationsMutationCommand value -> planQuantity(
					value, OrchidGroupMutationType.CONSUME_RESERVATION, value.source(), value.items(),
					null, Set.of(),
					OrchidGroupStateSimulation::outboundReserved);
			case RestoreOutboundOrchidGroupsMutationCommand value -> planQuantity(
					value, OrchidGroupMutationType.RESTORE_OUTBOUND, value.source(), value.items(),
					value.compensatedMutations(), Set.of(OrchidGroupMutationType.CONSUME_RESERVATION),
					OrchidGroupStateSimulation::restoreOutbound);
			case CorrectOrchidGroupsMutationCommand value -> planCorrection(value);
			default -> throw new IllegalArgumentException(
					"지원하지 않는 shadow command입니다: " + command.getClass().getName());
		};
	}

	public OrchidGroupMutationSource source(Object command) {
		return switch (command) {
			case CreateOrchidGroupMutationCommand value -> value.source();
			case CreateOrchidGroupsMutationCommand value -> value.source();
			case CreateInboundOrchidGroupsMutationCommand value -> value.source();
			case TransformOrchidGroupsMutationCommand value -> value.source();
			case UpdateOrchidGroupMutationCommand value -> value.source();
			case MoveOrchidGroupMutationCommand value -> value.source();
			case CancelOrchidGroupCreationMutationCommand value -> value.source();
			case DiscardOrchidGroupMutationCommand value -> value.source();
			case ReserveOrchidGroupsMutationCommand value -> value.source();
			case ReleaseOrchidGroupReservationsMutationCommand value -> value.source();
			case ConsumeOrchidGroupReservationsMutationCommand value -> value.source();
			case RestoreOutboundOrchidGroupsMutationCommand value -> value.source();
			case CorrectOrchidGroupsMutationCommand value -> value.source();
			default -> throw new IllegalArgumentException("지원하지 않는 shadow command입니다.");
		};
	}

	public OrchidGroupMutationType mutationType(Object command) {
		return switch (command) {
			case CreateOrchidGroupMutationCommand ignored -> OrchidGroupMutationType.CREATE;
			case CreateOrchidGroupsMutationCommand ignored -> OrchidGroupMutationType.CREATE;
			case CreateInboundOrchidGroupsMutationCommand ignored -> OrchidGroupMutationType.CREATE;
			case TransformOrchidGroupsMutationCommand ignored -> OrchidGroupMutationType.TRANSFORM;
			case UpdateOrchidGroupMutationCommand ignored -> OrchidGroupMutationType.UPDATE_DETAILS;
			case MoveOrchidGroupMutationCommand ignored -> OrchidGroupMutationType.MOVE;
			case CancelOrchidGroupCreationMutationCommand ignored -> OrchidGroupMutationType.CANCEL_CREATION;
			case DiscardOrchidGroupMutationCommand ignored -> OrchidGroupMutationType.DISCARD;
			case ReserveOrchidGroupsMutationCommand ignored -> OrchidGroupMutationType.RESERVE;
			case ReleaseOrchidGroupReservationsMutationCommand ignored -> OrchidGroupMutationType.RELEASE_RESERVATION;
			case ConsumeOrchidGroupReservationsMutationCommand ignored -> OrchidGroupMutationType.CONSUME_RESERVATION;
			case RestoreOutboundOrchidGroupsMutationCommand ignored -> OrchidGroupMutationType.RESTORE_OUTBOUND;
			case CorrectOrchidGroupsMutationCommand ignored -> OrchidGroupMutationType.CORRECTION;
			default -> throw new IllegalArgumentException("지원하지 않는 shadow command입니다.");
		};
	}

	public Map<String, Object> payload(Object command) {
		return objectMapper.convertValue(command, MAP_TYPE);
	}

	private OrchidGroupShadowPlan planCreate(CreateOrchidGroupMutationCommand command) {
		BedZone zone = findZone(command.bedZoneId());
		Variety variety = findVariety(command.details().varietyId());
		requireActive(variety);
		validatePlacement(zone, command.details(), Set.of());
		var simulation = createSimulation(
				zone, variety, command.details(), orchidGroupRepository.findMaxSortOrderByBedZoneId(zone.getId()) + 1);
		return plan(command, OrchidGroupMutationType.CREATE, List.of(new OrchidGroupShadowPlan.Entry(
				0, null, OrchidGroupMutationEntryRole.RESULT, null, simulation.snapshot())));
	}

	private OrchidGroupShadowPlan planCreateMany(CreateOrchidGroupsMutationCommand command) {
		Map<Long, BedZone> zones = findZones(command.groups().stream()
				.map(CreateOrchidGroupMutationItem::bedZoneId).collect(Collectors.toSet()));
		Map<Long, Variety> varieties = findVarieties(command.groups().stream()
				.map(item -> item.details().varietyId()).collect(Collectors.toSet()));
		Map<Long, Integer> sortOrders = currentMaxSortOrders(zones.keySet());
		List<OrchidGroupShadowPlan.Entry> entries = new ArrayList<>();
		for (int index = 0; index < command.groups().size(); index++) {
			CreateOrchidGroupMutationItem item = command.groups().get(index);
			BedZone zone = zones.get(item.bedZoneId());
			Variety variety = varieties.get(item.details().varietyId());
			requireActive(variety);
			validatePlacement(zone, item.details(), Set.of());
			int sortOrder = sortOrders.compute(zone.getId(), (ignored, current) -> current + 1);
			entries.add(new OrchidGroupShadowPlan.Entry(
					index, null, OrchidGroupMutationEntryRole.RESULT, null,
					createSimulation(zone, variety, item.details(), sortOrder).snapshot()));
		}
		return plan(command, OrchidGroupMutationType.CREATE, entries);
	}

	private OrchidGroupShadowPlan planCreateInbound(CreateInboundOrchidGroupsMutationCommand command) {
		InboundRecord inbound = inboundRecordRepository.findById(command.inboundRecordId())
				.orElseThrow(() -> new NotFoundException("입고 기록을 찾을 수 없습니다."));
		if (inbound.hasCreatedOrchidGroups()) {
			throw new IllegalStateException("이미 난 묶음이 생성된 입고 기록입니다.");
		}
		Long varietyId = inbound.getVariety().getId();
		if (command.groups().stream().anyMatch(item -> !varietyId.equals(item.details().varietyId()))) {
			throw new IllegalArgumentException("입고 생성 결과의 품종은 입고 기록의 품종이어야 합니다.");
		}
		Map<Long, BedZone> zones = findZones(command.groups().stream()
				.map(CreateOrchidGroupMutationItem::bedZoneId).collect(Collectors.toSet()));
		Map<Long, Integer> sortOrders = currentMaxSortOrders(zones.keySet());
		List<OrchidGroupShadowPlan.Entry> entries = new ArrayList<>();
		for (int index = 0; index < command.groups().size(); index++) {
			CreateOrchidGroupMutationItem item = command.groups().get(index);
			BedZone zone = zones.get(item.bedZoneId());
			OrchidGroupMutationDetails details = resolveInboundPlacement(zone, item.details());
			int sortOrder = sortOrders.compute(zone.getId(), (ignored, current) -> current + 1);
			OrchidGroupStateSnapshot snapshot = withInboundRecord(
					createSimulation(zone, inbound.getVariety(), details, sortOrder).snapshot(), inbound.getId());
			entries.add(new OrchidGroupShadowPlan.Entry(
					index, null, OrchidGroupMutationEntryRole.RESULT, null, snapshot));
		}
		return plan(command, OrchidGroupMutationType.CREATE, entries);
	}

	private OrchidGroupShadowPlan planTransform(TransformOrchidGroupsMutationCommand command) {
		Map<Long, OrchidGroup> sources = findGroups(command.sources().stream()
				.map(TransformOrchidGroupMutationSource::orchidGroupId).toList());
		if (sources.values().stream().anyMatch(group -> group.getVariety() == null)) {
			throw new IllegalArgumentException("품종이 연결되지 않은 난 묶음은 구조 변경할 수 없습니다.");
		}
		Set<Long> sourceVarietyIds = sources.values().stream()
				.map(group -> group.getVariety().getId()).collect(Collectors.toSet());
		if (command.results().stream().map(result -> result.details().varietyId())
				.anyMatch(varietyId -> !sourceVarietyIds.contains(varietyId))) {
			throw new IllegalArgumentException("구조 변경 결과 품종은 원본 난 묶음의 품종이어야 합니다.");
		}
		Map<Long, BedZone> zones = findZones(command.results().stream()
				.map(TransformOrchidGroupMutationResult::bedZoneId).collect(Collectors.toSet()));
		Map<Long, Variety> varieties = findVarieties(command.results().stream()
				.map(result -> result.details().varietyId()).collect(Collectors.toSet()));
		Map<Long, Integer> sortOrders = currentMaxSortOrders(zones.keySet());
		List<OrchidGroupShadowPlan.Entry> entries = new ArrayList<>();
		int ordinal = 0;
		for (TransformOrchidGroupMutationSource source : command.sources()) {
			OrchidGroup group = sources.get(source.orchidGroupId());
			var simulation = OrchidGroupStateSimulation.from(group);
			OrchidGroupStateSnapshot before = simulation.snapshot();
			simulation.transform(
					source.transformedQuantity(), source.releasedStartPosition(), source.releasedEndPosition());
			entries.add(new OrchidGroupShadowPlan.Entry(
					ordinal++, group.getId(), OrchidGroupMutationEntryRole.SOURCE,
					before, simulation.snapshot()));
		}
		for (TransformOrchidGroupMutationResult result : command.results()) {
			BedZone zone = zones.get(result.bedZoneId());
			Variety variety = varieties.get(result.details().varietyId());
			requireActive(variety);
			validatePlacement(zone, result.details(), command.placementExclusionOrchidGroupIds());
			int sortOrder = sortOrders.compute(zone.getId(), (ignored, current) -> current + 1);
			entries.add(new OrchidGroupShadowPlan.Entry(
					ordinal++, null, OrchidGroupMutationEntryRole.RESULT, null,
					createSimulation(zone, variety, result.details(), sortOrder).snapshot()));
		}
		return plan(command, OrchidGroupMutationType.TRANSFORM, entries);
	}

	private OrchidGroupShadowPlan planUpdate(UpdateOrchidGroupMutationCommand command) {
		OrchidGroup group = findGroup(command.orchidGroupId());
		Variety variety = findVariety(command.details().varietyId());
		validatePlacement(group.getBedZone(), command.details(), Set.of(group.getId()));
		var simulation = OrchidGroupStateSimulation.from(group);
		OrchidGroupStateSnapshot before = simulation.snapshot();
		OrchidGroupMutationDetails details = command.details();
		simulation.updateDetails(
				variety, details.quantity(), details.potSize(), details.ageYear(), details.status(),
				details.placementType(), details.trayCount(), details.splitPlacementAllowed(),
				details.startPosition(), details.endPosition(), details.memo());
		OrchidGroupStateSnapshot after = simulation.snapshot();
		requireStateChange(before, after);
		return plan(command, OrchidGroupMutationType.UPDATE_DETAILS, List.of(
				new OrchidGroupShadowPlan.Entry(
						0, group.getId(), OrchidGroupMutationEntryRole.AFFECTED, before, after)));
	}

	private OrchidGroupShadowPlan planMove(MoveOrchidGroupMutationCommand command) {
		OrchidGroup group = findGroup(command.orchidGroupId());
		BedZone destination = findZone(command.toBedZoneId());
		orchidPlacementPolicy.validatePlacement(
				destination, command.startPosition(), command.endPosition(), group.getId());
		var simulation = OrchidGroupStateSimulation.from(group);
		OrchidGroupStateSnapshot before = simulation.snapshot();
		int sortOrder = group.getBedZone().getId().equals(destination.getId())
				? group.getSortOrder()
				: orchidGroupRepository.findMaxSortOrderByBedZoneId(destination.getId()) + 1;
		simulation.moveTo(destination, sortOrder, command.startPosition(), command.endPosition());
		OrchidGroupStateSnapshot after = simulation.snapshot();
		requireStateChange(before, after);
		return plan(command, OrchidGroupMutationType.MOVE, List.of(
				new OrchidGroupShadowPlan.Entry(
						0, group.getId(), OrchidGroupMutationEntryRole.AFFECTED, before, after)));
	}

	private OrchidGroupShadowPlan planCancel(CancelOrchidGroupCreationMutationCommand command) {
		OrchidGroup group = findGroup(command.orchidGroupId());
		var simulation = OrchidGroupStateSimulation.from(group);
		OrchidGroupStateSnapshot before = simulation.snapshot();
		simulation.cancelCreation();
		OrchidGroupStateSnapshot after = simulation.snapshot();
		requireStateChange(before, after);
		return plan(command, OrchidGroupMutationType.CANCEL_CREATION, List.of(
				new OrchidGroupShadowPlan.Entry(
						0, group.getId(), OrchidGroupMutationEntryRole.AFFECTED, before, after)));
	}

	private OrchidGroupShadowPlan planDiscard(DiscardOrchidGroupMutationCommand command) {
		OrchidGroup group = findGroup(command.orchidGroupId());
		var simulation = OrchidGroupStateSimulation.from(group);
		OrchidGroupStateSnapshot before = simulation.snapshot();
		simulation.discard(command.quantity());
		return plan(command, OrchidGroupMutationType.DISCARD, List.of(
				new OrchidGroupShadowPlan.Entry(
						0, group.getId(), OrchidGroupMutationEntryRole.AFFECTED,
						before, simulation.snapshot())));
	}

	private OrchidGroupShadowPlan planQuantity(
			Object command,
			OrchidGroupMutationType mutationType,
			OrchidGroupMutationSource source,
			List<OrchidGroupQuantityMutationItem> items,
			RelatedOrchidGroupMutations relatedMutations,
			Set<OrchidGroupMutationType> allowedRelationTypes,
			BiConsumer<OrchidGroupStateSimulation, Integer> action) {
		Map<Long, OrchidGroup> groups = findGroups(items.stream()
				.map(OrchidGroupQuantityMutationItem::orchidGroupId).toList());
		validateRelationTargets(
				relatedMutations,
				new LinkedHashSet<>(groups.keySet()),
				allowedRelationTypes);
		List<OrchidGroupShadowPlan.Entry> entries = new ArrayList<>();
		for (int index = 0; index < items.size(); index++) {
			OrchidGroupQuantityMutationItem item = items.get(index);
			var simulation = OrchidGroupStateSimulation.from(groups.get(item.orchidGroupId()));
			OrchidGroupStateSnapshot before = simulation.snapshot();
			action.accept(simulation, item.quantity());
			OrchidGroupStateSnapshot after = simulation.snapshot();
			requireStateChange(before, after);
			entries.add(new OrchidGroupShadowPlan.Entry(
					index, item.orchidGroupId(), OrchidGroupMutationEntryRole.AFFECTED, before, after));
		}
		return new OrchidGroupShadowPlan(
				source, mutationType, commandFingerprint.calculate(command), payload(command), entries, null);
	}

	private OrchidGroupShadowPlan planCorrection(CorrectOrchidGroupsMutationCommand command) {
		Map<Long, OrchidGroup> groups = findGroups(command.items().stream()
				.map(CorrectOrchidGroupMutationItem::orchidGroupId).toList());
		List<OrchidGroupShadowPlan.Entry> entries = new ArrayList<>();
		for (CorrectOrchidGroupMutationItem item : command.items()) {
			OrchidGroup group = groups.get(item.orchidGroupId());
			if (group.getQuantity().equals(item.correctedQuantity())
					&& group.getStatus().equals(item.correctedStatus())) {
				continue;
			}
			var simulation = OrchidGroupStateSimulation.from(group);
			OrchidGroupStateSnapshot before = simulation.snapshot();
			simulation.correctQuantityAndStatus(item.correctedQuantity(), item.correctedStatus());
			entries.add(new OrchidGroupShadowPlan.Entry(
					entries.size(), item.orchidGroupId(), OrchidGroupMutationEntryRole.AFFECTED,
					before, simulation.snapshot()));
		}
		if (entries.isEmpty()) {
			throw new IllegalArgumentException("보정 Mutation에는 현재 상태와 다른 값이 필요합니다.");
		}
		validateRelationTargets(
				command.correctedMutations(),
				entries.stream().map(OrchidGroupShadowPlan.Entry::orchidGroupId)
						.collect(Collectors.toCollection(LinkedHashSet::new)),
				Set.of(OrchidGroupMutationType.CREATE, OrchidGroupMutationType.TRANSFORM));
		return plan(command, OrchidGroupMutationType.CORRECTION, entries);
	}

	private void validateRelationTargets(
			RelatedOrchidGroupMutations relatedMutations,
			Set<Long> affectedGroupIds,
			Set<OrchidGroupMutationType> allowedMutationTypes) {
		if (relatedMutations == null || relatedMutations.legacySource()) {
			return;
		}
		Map<Long, OrchidGroupMutation> mutationsById = mutationRepository
				.findAllById(relatedMutations.mutationIds())
				.stream()
				.collect(Collectors.toMap(OrchidGroupMutation::getId, Function.identity()));
		if (mutationsById.size() != relatedMutations.mutationIds().size()) {
			throw new NotFoundException("관련 Mutation을 모두 찾을 수 없습니다.");
		}
		if (allowedMutationTypes.isEmpty() || mutationsById.values().stream()
				.anyMatch(mutation -> !allowedMutationTypes.contains(mutation.getMutationType()))) {
			throw new IllegalArgumentException("관련 Mutation 유형이 보정·보상 대상과 일치하지 않습니다.");
		}
		Map<Long, Set<Long>> groupIdsByMutationId = new LinkedHashMap<>();
		entryRepository.findByMutationIdInOrderByMutationIdAscIdAsc(relatedMutations.mutationIds())
				.forEach(entry -> groupIdsByMutationId
						.computeIfAbsent(entry.getMutation().getId(), ignored -> new LinkedHashSet<>())
						.add(entry.getOrchidGroupId()));
		for (Long mutationId : relatedMutations.mutationIds()) {
			Set<Long> relatedGroupIds = groupIdsByMutationId.getOrDefault(mutationId, Set.of());
			if (relatedGroupIds.stream().noneMatch(affectedGroupIds::contains)) {
				throw new IllegalArgumentException("관련 Mutation은 변경 대상 난 묶음과 연결되어야 합니다.");
			}
		}
		Set<Long> allRelatedGroupIds = groupIdsByMutationId.values().stream()
				.flatMap(Collection::stream)
				.collect(Collectors.toSet());
		if (!allRelatedGroupIds.containsAll(affectedGroupIds)) {
			throw new IllegalArgumentException("모든 변경 대상은 관련 Mutation에 포함되어야 합니다.");
		}
	}

	private OrchidGroupShadowPlan plan(
			Object command,
			OrchidGroupMutationType mutationType,
			List<OrchidGroupShadowPlan.Entry> entries) {
		return new OrchidGroupShadowPlan(
				source(command), mutationType, commandFingerprint.calculate(command),
				payload(command), entries, null);
	}

	private OrchidGroupStateSimulation createSimulation(
			BedZone zone,
			Variety variety,
			OrchidGroupMutationDetails details,
			int sortOrder) {
		return OrchidGroupStateSimulation.created(
				zone, variety, details.quantity(), details.potSize(), details.ageYear(), details.status(),
				sortOrder, details.startPosition(), details.endPosition(), details.placementType(),
				details.trayCount(), details.splitPlacementAllowed(), details.memo());
	}

	private OrchidGroup findGroup(Long id) {
		return orchidGroupRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("난 묶음을 찾을 수 없습니다."));
	}

	private Map<Long, OrchidGroup> findGroups(Collection<Long> ids) {
		Map<Long, OrchidGroup> groups = orchidGroupRepository.findAllById(ids).stream()
				.collect(Collectors.toMap(OrchidGroup::getId, Function.identity()));
		if (groups.size() != new LinkedHashSet<>(ids).size()) {
			throw new NotFoundException("Mutation 대상 난 묶음을 모두 찾을 수 없습니다.");
		}
		return groups;
	}

	private BedZone findZone(Long id) {
		return bedZoneRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("논리 구역을 찾을 수 없습니다."));
	}

	private Map<Long, BedZone> findZones(Set<Long> ids) {
		Map<Long, BedZone> zones = bedZoneRepository.findAllById(ids).stream()
				.collect(Collectors.toMap(BedZone::getId, Function.identity()));
		if (zones.size() != ids.size()) {
			throw new NotFoundException("논리 구역을 모두 찾을 수 없습니다.");
		}
		return zones;
	}

	private Variety findVariety(Long id) {
		return varietyRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("품종을 찾을 수 없습니다."));
	}

	private Map<Long, Variety> findVarieties(Set<Long> ids) {
		Map<Long, Variety> varieties = varietyRepository.findAllById(ids).stream()
				.collect(Collectors.toMap(Variety::getId, Function.identity()));
		if (varieties.size() != ids.size()) {
			throw new NotFoundException("품종을 모두 찾을 수 없습니다.");
		}
		return varieties;
	}

	private Map<Long, Integer> currentMaxSortOrders(Collection<Long> zoneIds) {
		Map<Long, Integer> result = new LinkedHashMap<>();
		zoneIds.stream().sorted().forEach(id -> result.put(id, 0));
		orchidGroupRepository.findMaxSortOrdersByBedZoneIdIn(zoneIds)
				.forEach(row -> result.put(row.bedZoneId(), row.maxSortOrder()));
		return result;
	}

	private void validatePlacement(
			BedZone zone,
			OrchidGroupMutationDetails details,
			Set<Long> exclusionIds) {
		orchidPlacementPolicy.validatePlacementExcluding(
				zone, details.startPosition(), details.endPosition(), exclusionIds);
	}

	private OrchidGroupMutationDetails resolveInboundPlacement(
			BedZone zone,
			OrchidGroupMutationDetails details) {
		if (details.startPosition() == null && details.endPosition() == null) {
			var range = orchidPlacementPolicy.findFirstAvailableSingleSlot(zone);
			return details.withPlacement(range.startPosition(), range.endPosition());
		}
		orchidPlacementPolicy.validatePlacement(
				zone, details.startPosition(), details.endPosition(), null);
		return details;
	}

	private void requireActive(Variety variety) {
		if (!variety.isActive()) {
			throw new IllegalArgumentException("비활성 품종으로 난 묶음을 생성할 수 없습니다.");
		}
	}

	private void requireStateChange(OrchidGroupStateSnapshot before, OrchidGroupStateSnapshot after) {
		if (before.equals(after)) {
			throw new IllegalArgumentException("변경 전후 난 묶음 상태가 같습니다.");
		}
	}

	private OrchidGroupStateSnapshot withInboundRecord(OrchidGroupStateSnapshot state, Long inboundRecordId) {
		return new OrchidGroupStateSnapshot(
				state.quantity(), state.reservedQuantity(), state.status(), state.bedZoneId(),
				state.sortOrder(), state.startPosition(), state.endPosition(), state.varietyId(),
				state.genus(), state.varietyName(), state.ageYear(), state.potSizeCode(),
				state.placementType(), state.trayCount(), state.splitPlacementAllowed(),
				inboundRecordId, state.memo());
	}
}
