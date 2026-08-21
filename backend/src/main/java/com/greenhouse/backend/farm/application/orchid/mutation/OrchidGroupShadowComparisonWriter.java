package com.greenhouse.backend.farm.application.orchid.mutation;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupShadowComparison;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupShadowComparisonStatus;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupShadowComparisonRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrchidGroupShadowComparisonWriter {

	private final OrchidGroupShadowComparisonRepository repository;
	private final OrchidGroupLedgerWriterProperties writerProperties;
	private final Clock clock;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void save(OrchidGroupShadowComparisonEvent event) {
		var source = event.source();
		var existing = repository.findBySourceDomainAndSourceTypeAndSourceReferenceIdAndSourceOperationKey(
				source.domain(), source.type(), source.referenceId(), source.operationKey());
		if (existing.isPresent()) {
			if (!existing.get().hasSameCommandFingerprint(event.commandFingerprint())) {
				log.error(
						"동일 OrchidGroup shadow source에 다른 command가 감지되었습니다: {}/{}/{}/{}",
						source.domain(), source.type(), source.referenceId(), source.operationKey());
			}
			return;
		}
		repository.save(new OrchidGroupShadowComparison(
				source,
				event.mutationType(),
				event.commandFingerprint(),
				event.status(),
				writerProperties.writerVersion(),
				event.commandPayload(),
				event.expectedEntries(),
				event.actualEntries(),
				event.mismatches(),
				event.engineError(),
				Instant.now(clock)));
		if (event.status() != OrchidGroupShadowComparisonStatus.MATCHED) {
			log.warn(
					"OrchidGroup shadow comparison {}: {}/{}/{}/{}",
					event.status(), source.domain(), source.type(), source.referenceId(), source.operationKey());
		}
	}
}
