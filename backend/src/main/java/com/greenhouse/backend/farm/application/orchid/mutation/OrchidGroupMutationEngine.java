package com.greenhouse.backend.farm.application.orchid.mutation;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.application.structure.OrchidPlacementPolicy;
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
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationEntryRepository;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationRepository;
import com.greenhouse.backend.farm.repository.structure.BedZoneRepository;
import com.greenhouse.backend.farm.repository.variety.VarietyRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
		OrchidGroup group = new OrchidGroup(
				bedZone,
				variety.getGenus(),
				variety.getName(),
				details.quantity(),
				details.potSize(),
				details.ageYear(),
				details.status(),
				nextSortOrder,
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
		orchidGroupRepository.save(group);

		return recordCreated(
				command.source(),
				commandFingerprint,
				command.effectiveBusinessDate(),
				command.reason(),
				group);
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
}
