package com.greenhouse.backend.farm.application.orchid.mutation;

import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSource;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationEntryRepository;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class OrchidGroupMutationReplayResolver {

	private final OrchidGroupMutationRepository mutationRepository;

	private final OrchidGroupMutationEntryRepository entryRepository;

	Optional<OrchidGroupMutationResult> findExisting(OrchidGroupMutationSource source, String commandFingerprint) {
		return mutationRepository
			.findBySourceDomainAndSourceTypeAndSourceReferenceIdAndSourceOperationKey(source.domain(), source.type(),
					source.referenceId(), source.operationKey())
			.map(existing -> {
				if (!existing.hasSameCommandFingerprint(commandFingerprint)) {
					throw new ConflictException("같은 Mutation source key를 다른 command에 재사용할 수 없습니다.");
				}
				return OrchidGroupMutationResult.from(existing,
						entryRepository.findByMutationIdOrderByIdAsc(existing.getId()));
			});
	}

}
