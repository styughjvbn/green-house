package com.greenhouse.backend.farm.application.orchid.mutation;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutation;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntry;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntryRole;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationType;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupStateSnapshot;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationEntryRepository;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationRepository;
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
public class OrchidGroupMutationEngine {

	private static final int MUTATION_SCHEMA_VERSION = 1;

	private final OrchidGroupRepository orchidGroupRepository;
	private final OrchidGroupMutationRepository mutationRepository;
	private final OrchidGroupMutationEntryRepository entryRepository;
	private final OrchidGroupMutationFingerprint fingerprint;
	private final OrchidGroupMutationReplayResolver replayResolver;
	private final Clock clock;

	@Transactional(propagation = Propagation.MANDATORY)
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

		OrchidGroup group = orchidGroupRepository
				.findAllForUpdateByIdIn(List.of(command.orchidGroupId()))
				.stream()
				.findFirst()
				.orElseThrow(() -> new NotFoundException("폐기할 난 묶음을 찾을 수 없습니다."));
		replay = replayResolver.findExisting(command.source(), commandFingerprint);
		if (replay.isPresent()) {
			return replay.get();
		}
		Long revisionBefore = group.getStateRevision();
		if (revisionBefore == null) {
			throw new IllegalStateException("baseline이 없는 난 묶음은 Mutation Engine으로 변경할 수 없습니다.");
		}
		OrchidGroupStateSnapshot beforeState = OrchidGroupStateSnapshot.from(group);
		group.discard(command.quantity());
		group.advanceStateRevision();
		OrchidGroupStateSnapshot afterState = OrchidGroupStateSnapshot.from(group);

		OrchidGroupMutation mutation = mutationRepository.save(new OrchidGroupMutation(
				OrchidGroupMutationType.DISCARD,
				command.source(),
				commandFingerprint,
				Instant.now(clock),
				command.effectiveBusinessDate(),
				command.reason(),
				MUTATION_SCHEMA_VERSION));
		OrchidGroupMutationEntry entry = entryRepository.save(OrchidGroupMutationEntry.changed(
				mutation,
				group.getId(),
				OrchidGroupMutationEntryRole.AFFECTED,
				revisionBefore,
				beforeState,
				afterState));
		return OrchidGroupMutationResult.from(mutation, List.of(entry));
	}

	private record DiscardFingerprintPayload(
			OrchidGroupMutationType mutationType,
			Long orchidGroupId,
			Integer quantity,
			LocalDate effectiveBusinessDate,
			String reason) {
	}
}
