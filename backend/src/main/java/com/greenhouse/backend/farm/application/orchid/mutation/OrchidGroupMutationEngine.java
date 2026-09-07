package com.greenhouse.backend.farm.application.orchid.mutation;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.application.structure.OrchidPlacementPolicy;
import com.greenhouse.backend.farm.domain.inbound.InboundRecord;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutation;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationRelationType;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSource;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationType;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupStateSnapshot;
import com.greenhouse.backend.farm.domain.structure.BedZone;
import com.greenhouse.backend.farm.domain.variety.Variety;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.inbound.InboundRecordRepository;
import com.greenhouse.backend.farm.repository.structure.BedZoneRepository;
import com.greenhouse.backend.farm.repository.variety.VarietyRepository;
import java.time.LocalDate;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * ORCHID-CUTOVER: TARGET — 전환 후 난 묶음 상태 변경의 단일 application writer다.
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class OrchidGroupMutationEngine {

	private final OrchidGroupRepository orchidGroupRepository;
	private final InboundRecordRepository inboundRecordRepository;
	private final BedZoneRepository bedZoneRepository;
	private final VarietyRepository varietyRepository;
	private final OrchidPlacementPolicy orchidPlacementPolicy;
	private final OrchidGroupMutationCommandFingerprint commandFingerprint;
	private final OrchidGroupMutationReplayResolver replayResolver;
	private final OrchidGroupMutationRecorder recorder;

	public OrchidGroupMutationResult create(CreateOrchidGroupMutationCommand command) {
		String fingerprint = commandFingerprint.calculate(command);
		var replay = replayResolver.findExisting(command.source(), fingerprint);
		if (replay.isPresent()) {
			return replay.get();
		}

		BedZone bedZone = findZoneForUpdate(command.bedZoneId());
		replay = replayResolver.findExisting(command.source(), fingerprint);
		if (replay.isPresent()) {
			return replay.get();
		}
		Variety variety = findVariety(command.details().varietyId());
		if (!variety.isActive()) {
			throw new IllegalArgumentException("비활성 품종으로 난 묶음을 생성할 수 없습니다.");
		}
		OrchidGroupMutationDetails details = command.details();
		orchidPlacementPolicy.validatePlacement(
				bedZone, details.startPosition(), details.endPosition(), null);
		int nextSortOrder = orchidGroupRepository.findMaxSortOrderByBedZoneId(bedZone.getId()) + 1;
		OrchidGroupMutation mutation = recorder.start(
				OrchidGroupMutationType.CREATE, command.source(), fingerprint,
				command.effectiveBusinessDate(), command.reason());
		OrchidGroup group = createGroup(bedZone, variety, details, nextSortOrder);

		return recorder.created(mutation, List.of(group));
	}

	public OrchidGroupMutationResult createMany(CreateOrchidGroupsMutationCommand command) {
		String fingerprint = commandFingerprint.calculate(command);
		var replay = replayResolver.findExisting(command.source(), fingerprint);
		if (replay.isPresent()) {
			return replay.get();
		}

		Map<Long, BedZone> zones = findZonesForUpdate(command.groups().stream()
				.map(CreateOrchidGroupMutationItem::bedZoneId)
				.collect(Collectors.toSet()));
		replay = replayResolver.findExisting(command.source(), fingerprint);
		if (replay.isPresent()) {
			return replay.get();
		}
		Map<Long, Variety> varieties = findVarieties(command.groups().stream()
				.map(item -> item.details().varietyId())
				.collect(Collectors.toSet()));
		Map<Long, Integer> nextSortOrderByZoneId = currentMaxSortOrders(zones.keySet());
		OrchidGroupMutation mutation = recorder.start(
				OrchidGroupMutationType.CREATE, command.source(), fingerprint,
				command.effectiveBusinessDate(), command.reason());
		List<OrchidGroup> groups = new ArrayList<>();
		for (CreateOrchidGroupMutationItem item : command.groups()) {
			BedZone zone = zones.get(item.bedZoneId());
			Variety variety = varieties.get(item.details().varietyId());
			requireActive(variety);
			orchidPlacementPolicy.validatePlacement(
					zone,
					item.details().startPosition(),
					item.details().endPosition(),
					null);
			int nextSortOrder = nextSortOrderByZoneId.compute(
					zone.getId(), (id, current) -> current + 1);
			groups.add(createGroup(zone, variety, item.details(), nextSortOrder));
		}

		return recorder.created(mutation, groups);
	}

	public OrchidGroupMutationResult createFromInbound(
			CreateInboundOrchidGroupsMutationCommand command) {
		String fingerprint = commandFingerprint.calculate(command);
		var replay = replayResolver.findExisting(command.source(), fingerprint);
		if (replay.isPresent()) {
			return replay.get();
		}

		InboundRecord inboundRecord = findInboundRecordForUpdate(command.inboundRecordId());
		replay = replayResolver.findExisting(command.source(), fingerprint);
		if (replay.isPresent()) {
			return replay.get();
		}
		if (inboundRecord.hasCreatedOrchidGroups()) {
			throw new IllegalStateException("이미 난 묶음이 생성된 입고 기록입니다.");
		}
		Long inboundVarietyId = inboundRecord.getVariety().getId();
		if (command.groups().stream()
				.anyMatch(item -> !inboundVarietyId.equals(item.details().varietyId()))) {
			throw new IllegalArgumentException("입고 생성 결과의 품종은 입고 기록의 품종이어야 합니다.");
		}
		Map<Long, BedZone> zones = findZonesForUpdate(command.groups().stream()
				.map(CreateOrchidGroupMutationItem::bedZoneId)
				.collect(Collectors.toSet()));
		Map<Long, Integer> nextSortOrderByZoneId = currentMaxSortOrders(zones.keySet());
		OrchidGroupMutation mutation = recorder.start(
				OrchidGroupMutationType.CREATE, command.source(), fingerprint,
				command.effectiveBusinessDate(), command.reason());
		List<OrchidGroup> groups = new ArrayList<>();
		for (CreateOrchidGroupMutationItem item : command.groups()) {
			BedZone zone = zones.get(item.bedZoneId());
			OrchidGroupMutationDetails details = resolveInboundPlacement(zone, item.details());
			int nextSortOrder = nextSortOrderByZoneId.compute(
					zone.getId(), (id, current) -> current + 1);
			OrchidGroup group = createGroup(
					zone, inboundRecord.getVariety(), details, nextSortOrder, inboundRecord);
			groups.add(group);
		}
		return recorder.created(mutation, groups);
	}

	public OrchidGroupMutationResult transform(TransformOrchidGroupsMutationCommand command) {
		String fingerprint = commandFingerprint.calculate(command);
		var replay = replayResolver.findExisting(command.source(), fingerprint);
		if (replay.isPresent()) {
			return replay.get();
		}

		List<Long> sourceIds = command.sources().stream()
				.map(TransformOrchidGroupMutationSource::orchidGroupId)
				.toList();
		Map<Long, OrchidGroup> sourceById = findGroupsForUpdate(sourceIds, "구조 변경 원본 난 묶음을 모두 찾을 수 없습니다.");
		replay = replayResolver.findExisting(command.source(), fingerprint);
		if (replay.isPresent()) {
			return replay.get();
		}
		sourceById.values().forEach(this::requireBaseline);
		if (sourceById.values().stream().anyMatch(group -> group.getVariety() == null)) {
			throw new IllegalArgumentException("품종이 연결되지 않은 난 묶음은 구조 변경할 수 없습니다.");
		}
		Set<Long> sourceVarietyIds = sourceById.values().stream()
				.map(group -> group.getVariety().getId())
				.collect(Collectors.toSet());
		if (command.results().stream()
				.map(result -> result.details().varietyId())
				.anyMatch(varietyId -> !sourceVarietyIds.contains(varietyId))) {
			throw new IllegalArgumentException("구조 변경 결과 품종은 원본 난 묶음의 품종이어야 합니다.");
		}

		Map<Long, BedZone> zones = findZonesForUpdate(command.results().stream()
				.map(TransformOrchidGroupMutationResult::bedZoneId)
				.collect(Collectors.toSet()));
		Map<Long, Variety> varieties = findVarieties(command.results().stream()
				.map(result -> result.details().varietyId())
				.collect(Collectors.toSet()));
		Map<Long, Integer> nextSortOrderByZoneId = currentMaxSortOrders(zones.keySet());
		OrchidGroupMutation mutation = recorder.start(
				OrchidGroupMutationType.TRANSFORM,
				command.source(),
				fingerprint,
				command.effectiveBusinessDate(),
				command.reason());
		List<OrchidGroupMutationRecorder.Change> sourceChanges = new ArrayList<>();
		for (TransformOrchidGroupMutationSource sourceCommand : command.sources()) {
			OrchidGroup sourceGroup = sourceById.get(sourceCommand.orchidGroupId());
			long revisionBefore = sourceGroup.getStateRevision();
			OrchidGroupStateSnapshot beforeState = OrchidGroupStateSnapshot.from(sourceGroup);
			sourceGroup.applyTransformation(
					sourceCommand.transformedQuantity(),
					sourceCommand.releasedStartPosition(),
					sourceCommand.releasedEndPosition());
			OrchidGroupStateSnapshot afterState = OrchidGroupStateSnapshot.from(sourceGroup);
			sourceGroup.advanceStateRevision();
			sourceChanges.add(new OrchidGroupMutationRecorder.Change(
					sourceGroup.getId(), revisionBefore, beforeState, afterState));
		}

		List<OrchidGroup> resultGroups = new ArrayList<>();
		for (TransformOrchidGroupMutationResult resultCommand : command.results()) {
			BedZone zone = zones.get(resultCommand.bedZoneId());
			Variety variety = varieties.get(resultCommand.details().varietyId());
			requireActive(variety);
			orchidPlacementPolicy.validatePlacementExcluding(
					zone,
					resultCommand.details().startPosition(),
					resultCommand.details().endPosition(),
					command.placementExclusionOrchidGroupIds());
			int nextSortOrder = nextSortOrderByZoneId.compute(
					zone.getId(), (id, current) -> current + 1);
			resultGroups.add(createGroup(
					zone, variety, resultCommand.details(), nextSortOrder));
		}

		return recorder.transformed(mutation, sourceChanges, resultGroups);
	}

	public OrchidGroupMutationResult updateDetails(UpdateOrchidGroupMutationCommand command) {
		String fingerprint = commandFingerprint.calculate(command);
		var replay = replayResolver.findExisting(command.source(), fingerprint);
		if (replay.isPresent()) {
			return replay.get();
		}

		OrchidGroup group = findGroupForUpdate(command.orchidGroupId());
		replay = replayResolver.findExisting(command.source(), fingerprint);
		if (replay.isPresent()) {
			return replay.get();
		}
		requireBaseline(group);
		findZoneForUpdate(group.getBedZone().getId());
		Variety variety = findVariety(command.details().varietyId());
		OrchidGroupMutationDetails details = command.details();
		orchidPlacementPolicy.validatePlacement(
				group.getBedZone(), details.startPosition(), details.endPosition(), group.getId());
		long revisionBefore = group.getStateRevision();
		OrchidGroupStateSnapshot beforeState = OrchidGroupStateSnapshot.from(group);
		group.updateDetails(
				variety.getGenus(),
				variety.getName(),
				details.quantity(),
				details.potSize(),
				details.ageYear(),
				details.status(),
				details.placementType(),
				details.trayCount(),
				details.splitPlacementAllowed(),
				details.startPosition(),
				details.endPosition(),
				details.memo());
		group.assignVariety(variety);
		OrchidGroupStateSnapshot afterState = OrchidGroupStateSnapshot.from(group);
		requireStateChange(beforeState, afterState);
		group.advanceStateRevision();

		return recordChanged(
				OrchidGroupMutationType.UPDATE_DETAILS,
				command.source(),
				fingerprint,
				command.effectiveBusinessDate(),
				command.reason(),
				group,
				revisionBefore,
				beforeState,
				afterState);
	}

	public OrchidGroupMutationResult move(MoveOrchidGroupMutationCommand command) {
		String fingerprint = commandFingerprint.calculate(command);
		var replay = replayResolver.findExisting(command.source(), fingerprint);
		if (replay.isPresent()) {
			return replay.get();
		}

		OrchidGroup group = findGroupForUpdate(command.orchidGroupId());
		replay = replayResolver.findExisting(command.source(), fingerprint);
		if (replay.isPresent()) {
			return replay.get();
		}
		requireBaseline(group);
		BedZone destination = findZoneForUpdate(command.toBedZoneId());
		orchidPlacementPolicy.validatePlacement(
				destination, command.startPosition(), command.endPosition(), group.getId());
		long revisionBefore = group.getStateRevision();
		OrchidGroupStateSnapshot beforeState = OrchidGroupStateSnapshot.from(group);
		int sortOrder = group.getBedZone().getId().equals(destination.getId())
				? group.getSortOrder()
				: orchidGroupRepository.findMaxSortOrderByBedZoneId(destination.getId()) + 1;
		group.moveTo(destination, sortOrder, command.startPosition(), command.endPosition());
		OrchidGroupStateSnapshot afterState = OrchidGroupStateSnapshot.from(group);
		requireStateChange(beforeState, afterState);
		group.advanceStateRevision();

		return recordChanged(
				OrchidGroupMutationType.MOVE,
				command.source(),
				fingerprint,
				command.effectiveBusinessDate(),
				command.reason(),
				group,
				revisionBefore,
				beforeState,
				afterState);
	}

	public OrchidGroupMutationResult cancelCreation(CancelOrchidGroupCreationMutationCommand command) {
		String fingerprint = commandFingerprint.calculate(command);
		var replay = replayResolver.findExisting(command.source(), fingerprint);
		if (replay.isPresent()) {
			return replay.get();
		}

		OrchidGroup group = findGroupForUpdate(command.orchidGroupId());
		replay = replayResolver.findExisting(command.source(), fingerprint);
		if (replay.isPresent()) {
			return replay.get();
		}
		requireBaseline(group);
		long revisionBefore = group.getStateRevision();
		OrchidGroupStateSnapshot beforeState = OrchidGroupStateSnapshot.from(group);
		group.cancelCreation();
		OrchidGroupStateSnapshot afterState = OrchidGroupStateSnapshot.from(group);
		requireStateChange(beforeState, afterState);
		group.advanceStateRevision();

		return recordChanged(
				OrchidGroupMutationType.CANCEL_CREATION,
				command.source(),
				fingerprint,
				command.effectiveBusinessDate(),
				command.reason(),
				group,
				revisionBefore,
				beforeState,
				afterState);
	}

	public OrchidGroupMutationResult discard(DiscardOrchidGroupMutationCommand command) {
		String fingerprint = commandFingerprint.calculate(command);
		var replay = replayResolver.findExisting(command.source(), fingerprint);
		if (replay.isPresent()) {
			return replay.get();
		}

		OrchidGroup group = findGroupForUpdate(command.orchidGroupId());
		replay = replayResolver.findExisting(command.source(), fingerprint);
		if (replay.isPresent()) {
			return replay.get();
		}
		requireBaseline(group);
		long revisionBefore = group.getStateRevision();
		OrchidGroupStateSnapshot beforeState = OrchidGroupStateSnapshot.from(group);
		group.discard(command.quantity());
		OrchidGroupStateSnapshot afterState = OrchidGroupStateSnapshot.from(group);
		group.advanceStateRevision();

		return recordChanged(
				OrchidGroupMutationType.DISCARD,
				command.source(),
				fingerprint,
				command.effectiveBusinessDate(),
				command.reason(),
				group,
				revisionBefore,
				beforeState,
				afterState);
	}

	public OrchidGroupMutationResult reserve(ReserveOrchidGroupsMutationCommand command) {
		return applyQuantityMutation(
				command,
				OrchidGroupMutationType.RESERVE,
				command.source(),
				command.items(),
				null,
				null,
				command.effectiveBusinessDate(),
				command.reason(),
				OrchidGroup::reserve);
	}

	public OrchidGroupMutationResult releaseReservation(
			ReleaseOrchidGroupReservationsMutationCommand command) {
		return applyQuantityMutation(
				command,
				OrchidGroupMutationType.RELEASE_RESERVATION,
				command.source(),
				command.items(),
				null,
				null,
				command.effectiveBusinessDate(),
				command.reason(),
				OrchidGroup::releaseReserved);
	}

	public OrchidGroupMutationResult consumeReservation(
			ConsumeOrchidGroupReservationsMutationCommand command) {
		return applyQuantityMutation(
				command,
				OrchidGroupMutationType.CONSUME_RESERVATION,
				command.source(),
				command.items(),
				null,
				null,
				command.effectiveBusinessDate(),
				command.reason(),
				OrchidGroup::outboundReserved);
	}

	public OrchidGroupMutationResult restoreOutbound(
			RestoreOutboundOrchidGroupsMutationCommand command) {
		return applyQuantityMutation(
				command,
				OrchidGroupMutationType.RESTORE_OUTBOUND,
				command.source(),
				command.items(),
				command.compensatedMutations(),
				OrchidGroupMutationRelationType.COMPENSATES,
				command.effectiveBusinessDate(),
				command.reason(),
				OrchidGroup::restoreOutbound);
	}

	private OrchidGroupMutationResult applyQuantityMutation(
			Object command,
			OrchidGroupMutationType mutationType,
			OrchidGroupMutationSource source,
			List<OrchidGroupQuantityMutationItem> items,
			RelatedOrchidGroupMutations relatedMutations,
			OrchidGroupMutationRelationType relationType,
			LocalDate effectiveBusinessDate,
			String reason,
			BiConsumer<OrchidGroup, Integer> mutationAction) {
		String fingerprint = commandFingerprint.calculate(command);
		var replay = replayResolver.findExisting(source, fingerprint);
		if (replay.isPresent()) {
			return replay.get();
		}

		List<Long> orchidGroupIds = items.stream()
				.map(OrchidGroupQuantityMutationItem::orchidGroupId)
				.toList();
		Map<Long, OrchidGroup> groupsById = findGroupsForUpdate(orchidGroupIds, "수량 Mutation 대상 난 묶음을 모두 찾을 수 없습니다.");
		replay = replayResolver.findExisting(source, fingerprint);
		if (replay.isPresent()) {
			return replay.get();
		}
		List<OrchidGroupMutation> relationTargets = recorder.findRelated(
				relatedMutations,
				new LinkedHashSet<>(orchidGroupIds),
				relationType == OrchidGroupMutationRelationType.COMPENSATES
						? Set.of(OrchidGroupMutationType.CONSUME_RESERVATION)
						: Set.of());

		List<OrchidGroupMutationRecorder.Change> changes = new ArrayList<>();
		for (OrchidGroupQuantityMutationItem item : items) {
			OrchidGroup group = groupsById.get(item.orchidGroupId());
			requireBaseline(group);
			long revisionBefore = group.getStateRevision();
			OrchidGroupStateSnapshot beforeState = OrchidGroupStateSnapshot.from(group);
			mutationAction.accept(group, item.quantity());
			OrchidGroupStateSnapshot afterState = OrchidGroupStateSnapshot.from(group);
			requireStateChange(beforeState, afterState);
			group.advanceStateRevision();
			changes.add(new OrchidGroupMutationRecorder.Change(group.getId(), revisionBefore, beforeState, afterState));
		}

		OrchidGroupMutation mutation = recorder.start(
				mutationType, source, fingerprint, effectiveBusinessDate, reason);
		OrchidGroupMutationResult result = recorder.changed(mutation, changes);
		recorder.relate(mutation, relationTargets, relationType);
		return result;
	}

	public OrchidGroupMutationResult correct(CorrectOrchidGroupsMutationCommand command) {
		String fingerprint = commandFingerprint.calculate(command);
		var replay = replayResolver.findExisting(command.source(), fingerprint);
		if (replay.isPresent()) {
			return replay.get();
		}

		List<Long> orchidGroupIds = command.items().stream()
				.map(CorrectOrchidGroupMutationItem::orchidGroupId)
				.toList();
		Map<Long, OrchidGroup> groupsById = findGroupsForUpdate(orchidGroupIds, "보정 Mutation 대상 난 묶음을 모두 찾을 수 없습니다.");
		replay = replayResolver.findExisting(command.source(), fingerprint);
		if (replay.isPresent()) {
			return replay.get();
		}

		Set<Long> changedGroupIds = command.items().stream()
				.filter(item -> {
					OrchidGroup group = groupsById.get(item.orchidGroupId());
					requireBaseline(group);
					return !group.getQuantity().equals(item.correctedQuantity())
							|| !group.getStatus().equals(item.correctedStatus());
				})
				.map(CorrectOrchidGroupMutationItem::orchidGroupId)
				.collect(Collectors.toCollection(LinkedHashSet::new));
		if (changedGroupIds.isEmpty()) {
			throw new IllegalArgumentException("보정 Mutation에는 현재 상태와 다른 값이 필요합니다.");
		}
		List<OrchidGroupMutation> relationTargets = recorder.findRelated(
				command.correctedMutations(),
				changedGroupIds,
				Set.of(OrchidGroupMutationType.CREATE, OrchidGroupMutationType.TRANSFORM));
		OrchidGroupMutation mutation = recorder.start(
				OrchidGroupMutationType.CORRECTION,
				command.source(),
				fingerprint,
				command.effectiveBusinessDate(),
				command.reason());

		List<OrchidGroupMutationRecorder.Change> changes = new ArrayList<>();
		for (CorrectOrchidGroupMutationItem item : command.items()) {
			if (!changedGroupIds.contains(item.orchidGroupId())) {
				continue;
			}
			OrchidGroup group = groupsById.get(item.orchidGroupId());
			long revisionBefore = group.getStateRevision();
			OrchidGroupStateSnapshot beforeState = OrchidGroupStateSnapshot.from(group);
			group.correctQuantityAndStatus(item.correctedQuantity(), item.correctedStatus());
			OrchidGroupStateSnapshot afterState = OrchidGroupStateSnapshot.from(group);
			group.advanceStateRevision();
			changes.add(new OrchidGroupMutationRecorder.Change(group.getId(), revisionBefore, beforeState, afterState));
		}

		OrchidGroupMutationResult result = recorder.changed(mutation, changes);
		recorder.relate(mutation, relationTargets, OrchidGroupMutationRelationType.CORRECTS);
		return result;
	}

	private OrchidGroupMutationResult recordChanged(
			OrchidGroupMutationType mutationType,
			OrchidGroupMutationSource source,
			String commandFingerprint,
			LocalDate effectiveBusinessDate,
			String reason,
			OrchidGroup group,
			long revisionBefore,
			OrchidGroupStateSnapshot beforeState,
			OrchidGroupStateSnapshot afterState) {
		OrchidGroupMutation mutation = recorder.start(
				mutationType, source, commandFingerprint, effectiveBusinessDate, reason);
		return recorder.changed(mutation, List.of(new OrchidGroupMutationRecorder.Change(
				group.getId(), revisionBefore, beforeState, afterState)));
	}

	private OrchidGroup findGroupForUpdate(Long orchidGroupId) {
		return findGroupsForUpdate(List.of(orchidGroupId), "난 묶음을 찾을 수 없습니다.").get(orchidGroupId);
	}

	private Map<Long, OrchidGroup> findGroupsForUpdate(List<Long> ids, String missingMessage) {
		Map<Long, OrchidGroup> groups = orchidGroupRepository.findAllForUpdateByIdIn(ids.stream().sorted().toList())
				.stream().collect(Collectors.toMap(OrchidGroup::getId, Function.identity()));
		if (groups.size() != ids.size()) {
			throw new NotFoundException(missingMessage);
		}
		return groups;
	}

	private BedZone findZoneForUpdate(Long bedZoneId) {
		return bedZoneRepository.findForUpdateById(bedZoneId)
				.orElseThrow(() -> new NotFoundException("논리 구역을 찾을 수 없습니다."));
	}

	private Variety findVariety(Long varietyId) {
		return varietyRepository.findById(varietyId)
				.orElseThrow(() -> new NotFoundException("품종을 찾을 수 없습니다."));
	}

	private InboundRecord findInboundRecordForUpdate(Long inboundRecordId) {
		return inboundRecordRepository.findAllForUpdateByIdIn(List.of(inboundRecordId))
				.stream()
				.findFirst()
				.orElseThrow(() -> new NotFoundException("입고 기록을 찾을 수 없습니다."));
	}

	private Map<Long, BedZone> findZonesForUpdate(Collection<Long> bedZoneIds) {
		List<Long> sortedIds = bedZoneIds.stream().sorted().toList();
		Map<Long, BedZone> zones = bedZoneRepository.findAllForUpdateByIdIn(sortedIds).stream()
				.collect(Collectors.toMap(BedZone::getId, Function.identity()));
		if (zones.size() != sortedIds.size()) {
			throw new NotFoundException("논리 구역을 모두 찾을 수 없습니다.");
		}
		return zones;
	}

	private Map<Long, Variety> findVarieties(Set<Long> varietyIds) {
		Map<Long, Variety> varieties = varietyRepository.findAllById(varietyIds).stream()
				.collect(Collectors.toMap(Variety::getId, Function.identity()));
		if (varieties.size() != varietyIds.size()) {
			throw new NotFoundException("품종을 모두 찾을 수 없습니다.");
		}
		return varieties;
	}

	private Map<Long, Integer> currentMaxSortOrders(Collection<Long> bedZoneIds) {
		Map<Long, Integer> maxSortOrders = new LinkedHashMap<>();
		bedZoneIds.stream().sorted().forEach(bedZoneId -> maxSortOrders.put(bedZoneId, 0));
		orchidGroupRepository.findMaxSortOrdersByBedZoneIdIn(bedZoneIds).forEach(row ->
				maxSortOrders.put(row.bedZoneId(), row.maxSortOrder()));
		return maxSortOrders;
	}

	private void requireActive(Variety variety) {
		if (!variety.isActive()) {
			throw new IllegalArgumentException("비활성 품종으로 난 묶음을 생성할 수 없습니다.");
		}
	}

	private OrchidGroup createGroup(
			BedZone bedZone,
			Variety variety,
			OrchidGroupMutationDetails details,
			int sortOrder) {
		return createGroup(bedZone, variety, details, sortOrder, null);
	}

	private OrchidGroup createGroup(
			BedZone bedZone,
			Variety variety,
			OrchidGroupMutationDetails details,
			int sortOrder,
			InboundRecord inboundRecord) {
		OrchidGroup group = new OrchidGroup(
				bedZone,
				variety.getGenus(),
				variety.getName(),
				details.quantity(),
				details.potSize(),
				details.ageYear(),
				details.status(),
				sortOrder,
				details.startPosition(),
				details.endPosition());
		group.updateDetails(
				variety.getGenus(),
				variety.getName(),
				details.quantity(),
				details.potSize(),
				details.ageYear(),
				details.status(),
				details.placementType(),
				details.trayCount(),
				details.splitPlacementAllowed(),
				details.startPosition(),
				details.endPosition(),
				details.memo());
		group.assignVariety(variety);
		group.assignInboundRecord(inboundRecord);
		group.establishCreationRevision();
		return orchidGroupRepository.save(group);
	}

	private OrchidGroupMutationDetails resolveInboundPlacement(
			BedZone bedZone,
			OrchidGroupMutationDetails details) {
		if (details.startPosition() == null && details.endPosition() == null) {
			OrchidPlacementPolicy.PlacementRange range =
					orchidPlacementPolicy.findFirstAvailableSingleSlot(bedZone);
			return details.withPlacement(range.startPosition(), range.endPosition());
		}
		orchidPlacementPolicy.validatePlacement(
				bedZone, details.startPosition(), details.endPosition(), null);
		return details;
	}

	private void requireBaseline(OrchidGroup group) {
		if (group.getStateRevision() == null) {
			throw new IllegalStateException("baseline이 없는 난 묶음은 Mutation Engine으로 변경할 수 없습니다.");
		}
	}

	private void requireStateChange(
			OrchidGroupStateSnapshot beforeState,
			OrchidGroupStateSnapshot afterState) {
		if (beforeState.equals(afterState)) {
			throw new IllegalArgumentException("변경 전후 난 묶음 상태가 같습니다.");
		}
	}

}
