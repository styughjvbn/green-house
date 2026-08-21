package com.greenhouse.backend.farm.application.orchid.mutation;

import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSource;
import com.greenhouse.backend.farm.domain.transformation.OrchidGroupLineage;
import com.greenhouse.backend.farm.repository.transformation.OrchidGroupLineageRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrchidGroupHistoricalLineageLinkService {

	private static final int BATCH_SIZE = 500;

	private final OrchidGroupLineageRepository lineageRepository;

	@Transactional
	public int link(
			Instant sourceCutoff,
			List<OrchidGroupHistoryMigrationWorkLink> workLinks,
			Map<String, Long> mutationIdsBySource) {
		if (sourceCutoff == null || workLinks == null || mutationIdsBySource == null) {
			throw new IllegalArgumentException("Historical lineage 연결 입력이 필요합니다.");
		}
		List<OrchidGroupLineage> lineages = loadLineages(sourceCutoff);
		for (OrchidGroupLineage lineage : lineages) {
			List<OrchidGroupHistoryMigrationWorkLink> matches = workLinks.stream()
					.filter(link -> link.workOperationId().equals(lineage.getWorkOperationId()))
					.filter(link -> link.sourceOrchidGroupIds().contains(
							lineage.getSourceOrchidGroup().getId()))
					.filter(link -> link.resultOrchidGroupIds().contains(
							lineage.getResultOrchidGroup().getId()))
					.toList();
			if (matches.size() != 1) {
				throw new ConflictException("Lineage를 하나의 historical Work Mutation에 연결할 수 없습니다: "
						+ lineage.getId());
			}
			Long mutationId = mutationIdsBySource.get(sourceKey(matches.getFirst().source()));
			if (mutationId == null) {
				throw new ConflictException("Lineage 대상 historical Mutation ID가 없습니다: " + lineage.getId());
			}
			lineage.linkMutation(mutationId);
		}
		return lineages.size();
	}

	private List<OrchidGroupLineage> loadLineages(Instant sourceCutoff) {
		List<OrchidGroupLineage> result = new ArrayList<>();
		long afterId = 0;
		LocalDateTime cutoff = LocalDateTime.ofInstant(sourceCutoff, ZoneOffset.UTC);
		while (true) {
			List<OrchidGroupLineage> batch = lineageRepository.findHistoricalAfter(
					afterId, cutoff, PageRequest.of(0, BATCH_SIZE));
			if (batch.isEmpty()) {
				return result;
			}
			result.addAll(batch);
			afterId = batch.getLast().getId();
		}
	}

	static String sourceKey(OrchidGroupMutationSource source) {
		return source.domain() + ":" + source.type() + ":" + source.referenceId()
				+ ":" + source.operationKey();
	}
}
