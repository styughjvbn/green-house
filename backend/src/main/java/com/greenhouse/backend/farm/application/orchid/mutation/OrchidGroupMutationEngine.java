package com.greenhouse.backend.farm.application.orchid.mutation;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.application.structure.OrchidPlacementPolicy;
import com.greenhouse.backend.farm.domain.inbound.InboundRecord;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutation;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntry;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntryRole;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSource;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationType;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupStateSnapshot;
import com.greenhouse.backend.farm.domain.structure.BedZone;
import com.greenhouse.backend.farm.domain.variety.Variety;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.inbound.InboundRecordRepository;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationEntryRepository;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationRepository;
import com.greenhouse.backend.farm.repository.structure.BedZoneRepository;
import com.greenhouse.backend.farm.repository.variety.VarietyRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class OrchidGroupMutationEngine {

	private static final int MUTATION_SCHEMA_VERSION = 1;

	private final OrchidGroupRepository orchidGroupRepository;
	private final InboundRecordRepository inboundRecordRepository;
	private final BedZoneRepository bedZoneRepository;
	private final VarietyRepository varietyRepository;
	private final OrchidGroupMutationRepository mutationRepository;
	private final OrchidGroupMutationEntryRepository entryRepository;
	private final OrchidPlacementPolicy orchidPlacementPolicy;
	private final OrchidGroupMutationFingerprint fingerprint;
	private final OrchidGroupMutationReplayResolver replayResolver;
	private final Clock clock;

	public OrchidGroupMutationResult create(CreateOrchidGroupMutationCommand command) {
		String commandFingerprint = fingerprint.calculate(new CreateFingerprintPayload(
				OrchidGroupMutationType.CREATE,
				command.bedZoneId(),
				command.details(),
				command.effectiveBusinessDate(),
				command.reason()));
		var replay = replayResolver.findExisting(command.source(), commandFingerprint);
		if (replay.isPresent()) {
			return replay.get();
		}

		BedZone bedZone = findZoneForUpdate(command.bedZoneId());
		replay = replayResolver.findExisting(command.source(), commandFingerprint);
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
		OrchidGroup group = createGroup(bedZone, variety, details, nextSortOrder);

		return recordCreated(
				command.source(),
				commandFingerprint,
				command.effectiveBusinessDate(),
				command.reason(),
				group);
	}

	public OrchidGroupMutationResult createMany(CreateOrchidGroupsMutationCommand command) {
		String commandFingerprint = fingerprint.calculate(new CreateManyFingerprintPayload(
				OrchidGroupMutationType.CREATE,
				command.groups(),
				command.effectiveBusinessDate(),
				command.reason()));
		var replay = replayResolver.findExisting(command.source(), commandFingerprint);
		if (replay.isPresent()) {
			return replay.get();
		}

		Map<Long, BedZone> zones = findZonesForUpdate(command.groups().stream()
				.map(CreateOrchidGroupMutationItem::bedZoneId)
				.collect(Collectors.toSet()));
		replay = replayResolver.findExisting(command.source(), commandFingerprint);
		if (replay.isPresent()) {
			return replay.get();
		}
		Map<Long, Variety> varieties = findVarieties(command.groups().stream()
				.map(item -> item.details().varietyId())
				.collect(Collectors.toSet()));
		Map<Long, Integer> nextSortOrderByZoneId = currentMaxSortOrders(zones.keySet());
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

		return recordCreated(
				command.source(),
				commandFingerprint,
				command.effectiveBusinessDate(),
				command.reason(),
				groups);
	}

	public OrchidGroupMutationResult createFromInbound(
			CreateInboundOrchidGroupsMutationCommand command) {
		String commandFingerprint = fingerprint.calculate(new CreateInboundFingerprintPayload(
				OrchidGroupMutationType.CREATE,
				command.inboundRecordId(),
				command.groups(),
				command.effectiveBusinessDate(),
				command.reason()));
		var replay = replayResolver.findExisting(command.source(), commandFingerprint);
		if (replay.isPresent()) {
			return replay.get();
		}

		InboundRecord inboundRecord = findInboundRecordForUpdate(command.inboundRecordId());
		replay = replayResolver.findExisting(command.source(), commandFingerprint);
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
		List<OrchidGroup> groups = new ArrayList<>();
		for (CreateOrchidGroupMutationItem item : command.groups()) {
			BedZone zone = zones.get(item.bedZoneId());
			OrchidGroupMutationDetails details = resolveInboundPlacement(zone, item.details());
			int nextSortOrder = nextSortOrderByZoneId.compute(
					zone.getId(), (id, current) -> current + 1);
			OrchidGroup group = createGroup(
					zone, inboundRecord.getVariety(), details, nextSortOrder);
			group.assignInboundRecord(inboundRecord);
			groups.add(group);
		}
		return recordCreated(
				command.source(),
				commandFingerprint,
				command.effectiveBusinessDate(),
				command.reason(),
				groups);
	}

	public OrchidGroupMutationResult transform(TransformOrchidGroupsMutationCommand command) {
		String commandFingerprint = fingerprint.calculate(new TransformFingerprintPayload(
				OrchidGroupMutationType.TRANSFORM,
				command.sources(),
				command.results(),
				command.effectiveBusinessDate(),
				command.reason()));
		var replay = replayResolver.findExisting(command.source(), commandFingerprint);
		if (replay.isPresent()) {
			return replay.get();
		}

		List<Long> sourceIds = command.sources().stream()
				.map(TransformOrchidGroupMutationSource::orchidGroupId)
				.toList();
		Map<Long, OrchidGroup> sourceById = orchidGroupRepository.findAllForUpdateByIdIn(sourceIds)
				.stream()
				.collect(Collectors.toMap(
						OrchidGroup::getId,
						Function.identity(),
						(left, right) -> left,
						LinkedHashMap::new));
		if (sourceById.size() != sourceIds.size()) {
			throw new NotFoundException("구조 변경 원본 난 묶음을 모두 찾을 수 없습니다.");
		}
		replay = replayResolver.findExisting(command.source(), commandFingerprint);
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
		List<PendingChange> sourceChanges = new ArrayList<>();
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
			sourceChanges.add(new PendingChange(
					sourceGroup, revisionBefore, beforeState, afterState));
		}

		Map<Long, Integer> nextSortOrderByZoneId = currentMaxSortOrders(zones.keySet());
		List<OrchidGroup> resultGroups = new ArrayList<>();
		for (TransformOrchidGroupMutationResult resultCommand : command.results()) {
			BedZone zone = zones.get(resultCommand.bedZoneId());
			Variety variety = varieties.get(resultCommand.details().varietyId());
			requireActive(variety);
			orchidPlacementPolicy.validatePlacement(
					zone,
					resultCommand.details().startPosition(),
					resultCommand.details().endPosition(),
					null);
			int nextSortOrder = nextSortOrderByZoneId.compute(
					zone.getId(), (id, current) -> current + 1);
			resultGroups.add(createGroup(
					zone, variety, resultCommand.details(), nextSortOrder));
		}

		OrchidGroupMutation mutation = saveMutation(
				OrchidGroupMutationType.TRANSFORM,
				command.source(),
				commandFingerprint,
				command.effectiveBusinessDate(),
				command.reason());
		List<OrchidGroupMutationEntry> entries = new ArrayList<>();
		sourceChanges.forEach(change -> entries.add(OrchidGroupMutationEntry.changed(
				mutation,
				change.group().getId(),
				OrchidGroupMutationEntryRole.SOURCE,
				change.revisionBefore(),
				change.beforeState(),
				change.afterState())));
		resultGroups.forEach(group -> entries.add(OrchidGroupMutationEntry.created(
				mutation,
				group.getId(),
				OrchidGroupMutationEntryRole.RESULT,
				OrchidGroupStateSnapshot.from(group))));
		entryRepository.saveAll(entries);
		return OrchidGroupMutationResult.from(mutation, entries);
	}

	public OrchidGroupMutationResult updateDetails(UpdateOrchidGroupMutationCommand command) {
		String commandFingerprint = fingerprint.calculate(new UpdateFingerprintPayload(
				OrchidGroupMutationType.UPDATE_DETAILS,
				command.orchidGroupId(),
				command.details(),
				command.effectiveBusinessDate(),
				command.reason()));
		var replay = replayResolver.findExisting(command.source(), commandFingerprint);
		if (replay.isPresent()) {
			return replay.get();
		}

		OrchidGroup group = findGroupForUpdate(command.orchidGroupId());
		replay = replayResolver.findExisting(command.source(), commandFingerprint);
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
				commandFingerprint,
				command.effectiveBusinessDate(),
				command.reason(),
				group,
				revisionBefore,
				beforeState,
				afterState);
	}

	public OrchidGroupMutationResult move(MoveOrchidGroupMutationCommand command) {
		String commandFingerprint = fingerprint.calculate(new MoveFingerprintPayload(
				OrchidGroupMutationType.MOVE,
				command.orchidGroupId(),
				command.toBedZoneId(),
				command.startPosition(),
				command.endPosition(),
				command.effectiveBusinessDate(),
				command.reason()));
		var replay = replayResolver.findExisting(command.source(), commandFingerprint);
		if (replay.isPresent()) {
			return replay.get();
		}

		OrchidGroup group = findGroupForUpdate(command.orchidGroupId());
		replay = replayResolver.findExisting(command.source(), commandFingerprint);
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
				commandFingerprint,
				command.effectiveBusinessDate(),
				command.reason(),
				group,
				revisionBefore,
				beforeState,
				afterState);
	}

	public OrchidGroupMutationResult cancelCreation(CancelOrchidGroupCreationMutationCommand command) {
		String commandFingerprint = fingerprint.calculate(new CancelCreationFingerprintPayload(
				OrchidGroupMutationType.CANCEL_CREATION,
				command.orchidGroupId(),
				command.effectiveBusinessDate(),
				command.reason()));
		var replay = replayResolver.findExisting(command.source(), commandFingerprint);
		if (replay.isPresent()) {
			return replay.get();
		}

		OrchidGroup group = findGroupForUpdate(command.orchidGroupId());
		replay = replayResolver.findExisting(command.source(), commandFingerprint);
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
				commandFingerprint,
				command.effectiveBusinessDate(),
				command.reason(),
				group,
				revisionBefore,
				beforeState,
				afterState);
	}

	public OrchidGroupMutationResult discard(DiscardOrchidGroupMutationCommand command) {
		String commandFingerprint = fingerprint.calculate(new DiscardFingerprintPayload(
				OrchidGroupMutationType.DISCARD,
				command.orchidGroupId(),
				command.quantity(),
				command.effectiveBusinessDate(),
				command.reason()));
		var replay = replayResolver.findExisting(command.source(), commandFingerprint);
		if (replay.isPresent()) {
			return replay.get();
		}

		OrchidGroup group = findGroupForUpdate(command.orchidGroupId());
		replay = replayResolver.findExisting(command.source(), commandFingerprint);
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
				commandFingerprint,
				command.effectiveBusinessDate(),
				command.reason(),
				group,
				revisionBefore,
				beforeState,
				afterState);
	}

	private OrchidGroupMutationResult recordCreated(
			OrchidGroupMutationSource source,
			String commandFingerprint,
			LocalDate effectiveBusinessDate,
			String reason,
			OrchidGroup group) {
		OrchidGroupMutation mutation = saveMutation(
				OrchidGroupMutationType.CREATE,
				source,
				commandFingerprint,
				effectiveBusinessDate,
				reason);
		OrchidGroupMutationEntry entry = entryRepository.save(OrchidGroupMutationEntry.created(
				mutation,
				group.getId(),
				OrchidGroupMutationEntryRole.RESULT,
				OrchidGroupStateSnapshot.from(group)));
		return OrchidGroupMutationResult.from(mutation, List.of(entry));
	}

	private OrchidGroupMutationResult recordCreated(
			OrchidGroupMutationSource source,
			String commandFingerprint,
			LocalDate effectiveBusinessDate,
			String reason,
			List<OrchidGroup> groups) {
		OrchidGroupMutation mutation = saveMutation(
				OrchidGroupMutationType.CREATE,
				source,
				commandFingerprint,
				effectiveBusinessDate,
				reason);
		List<OrchidGroupMutationEntry> entries = groups.stream()
				.map(group -> OrchidGroupMutationEntry.created(
						mutation,
						group.getId(),
						OrchidGroupMutationEntryRole.RESULT,
						OrchidGroupStateSnapshot.from(group)))
				.toList();
		entryRepository.saveAll(entries);
		return OrchidGroupMutationResult.from(mutation, entries);
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
		OrchidGroupMutation mutation = saveMutation(
				mutationType, source, commandFingerprint, effectiveBusinessDate, reason);
		OrchidGroupMutationEntry entry = entryRepository.save(OrchidGroupMutationEntry.changed(
				mutation,
				group.getId(),
				OrchidGroupMutationEntryRole.AFFECTED,
				revisionBefore,
				beforeState,
				afterState));
		return OrchidGroupMutationResult.from(mutation, List.of(entry));
	}

	private OrchidGroupMutation saveMutation(
			OrchidGroupMutationType mutationType,
			OrchidGroupMutationSource source,
			String commandFingerprint,
			LocalDate effectiveBusinessDate,
			String reason) {
		return mutationRepository.save(new OrchidGroupMutation(
				mutationType,
				source,
				commandFingerprint,
				Instant.now(clock),
				effectiveBusinessDate,
				reason,
				MUTATION_SCHEMA_VERSION));
	}

	private OrchidGroup findGroupForUpdate(Long orchidGroupId) {
		return orchidGroupRepository.findAllForUpdateByIdIn(List.of(orchidGroupId))
				.stream()
				.findFirst()
				.orElseThrow(() -> new NotFoundException("난 묶음을 찾을 수 없습니다."));
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

	private record CreateFingerprintPayload(
			OrchidGroupMutationType mutationType,
			Long bedZoneId,
			OrchidGroupMutationDetails details,
			LocalDate effectiveBusinessDate,
			String reason) {
	}

	private record CreateManyFingerprintPayload(
			OrchidGroupMutationType mutationType,
			List<CreateOrchidGroupMutationItem> groups,
			LocalDate effectiveBusinessDate,
			String reason) {
	}

	private record CreateInboundFingerprintPayload(
			OrchidGroupMutationType mutationType,
			Long inboundRecordId,
			List<CreateOrchidGroupMutationItem> groups,
			LocalDate effectiveBusinessDate,
			String reason) {
	}

	private record TransformFingerprintPayload(
			OrchidGroupMutationType mutationType,
			List<TransformOrchidGroupMutationSource> sources,
			List<TransformOrchidGroupMutationResult> results,
			LocalDate effectiveBusinessDate,
			String reason) {
	}

	private record UpdateFingerprintPayload(
			OrchidGroupMutationType mutationType,
			Long orchidGroupId,
			OrchidGroupMutationDetails details,
			LocalDate effectiveBusinessDate,
			String reason) {
	}

	private record MoveFingerprintPayload(
			OrchidGroupMutationType mutationType,
			Long orchidGroupId,
			Long toBedZoneId,
			BigDecimal startPosition,
			BigDecimal endPosition,
			LocalDate effectiveBusinessDate,
			String reason) {
	}

	private record CancelCreationFingerprintPayload(
			OrchidGroupMutationType mutationType,
			Long orchidGroupId,
			LocalDate effectiveBusinessDate,
			String reason) {
	}

	private record DiscardFingerprintPayload(
			OrchidGroupMutationType mutationType,
			Long orchidGroupId,
			Integer quantity,
			LocalDate effectiveBusinessDate,
			String reason) {
	}

	private record PendingChange(
			OrchidGroup group,
			long revisionBefore,
			OrchidGroupStateSnapshot beforeState,
			OrchidGroupStateSnapshot afterState) {
	}
}
