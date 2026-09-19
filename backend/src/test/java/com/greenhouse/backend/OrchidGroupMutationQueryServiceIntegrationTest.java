package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationQueryService;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutation;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntry;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntryRole;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationRelation;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationRelationType;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSource;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSourceDomain;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationType;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupStateSnapshot;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationEntryRepository;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationRelationRepository;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class OrchidGroupMutationQueryServiceIntegrationTest extends AbstractBackendIntegrationTest {

	@Autowired
	OrchidGroupMutationQueryService queryService;

	@Autowired
	OrchidGroupMutationRepository mutationRepository;

	@Autowired
	OrchidGroupMutationEntryRepository entryRepository;

	@Autowired
	OrchidGroupMutationRelationRepository relationRepository;

	@Test
	void returnsFilteredMutationEntriesAndRelationsInLatestFirstOrder() {
		long orchidGroupId = 91_001L;
		var created = saveMutation(OrchidGroupMutationType.CREATE, "CREATE", Instant.parse("2026-09-18T01:00:00Z"));
		entryRepository.save(OrchidGroupMutationEntry.created(created, orchidGroupId,
				OrchidGroupMutationEntryRole.RESULT, snapshot(10, 0, "정상")));

		var corrected = saveMutation(OrchidGroupMutationType.CORRECTION, "CORRECT",
				Instant.parse("2026-09-18T02:00:00Z"));
		entryRepository.save(OrchidGroupMutationEntry.changed(corrected, orchidGroupId,
				OrchidGroupMutationEntryRole.AFFECTED, 1L, snapshot(10, 0, "정상"), snapshot(8, 0, "관리")));
		relationRepository
			.save(new OrchidGroupMutationRelation(corrected, created, OrchidGroupMutationRelationType.CORRECTS));

		var page = queryService.getMutations(orchidGroupId, null, OrchidGroupMutationSourceDomain.FARM, 0, 20);

		assertThat(page.content()).extracting("id").containsExactly(corrected.getId(), created.getId());
		assertThat(page.content().getFirst().entries()).singleElement().satisfies(entry -> {
			assertThat(entry.orchidGroupId()).isEqualTo(orchidGroupId);
			assertThat(entry.beforeState().quantity()).isEqualTo(10);
			assertThat(entry.afterState().quantity()).isEqualTo(8);
			assertThat(entry.afterState().status()).isEqualTo("관리");
		});
		assertThat(page.content().getFirst().relations()).singleElement().satisfies(relation -> {
			assertThat(relation.mutationId()).isEqualTo(corrected.getId());
			assertThat(relation.relatedMutationId()).isEqualTo(created.getId());
			assertThat(relation.relationType()).isEqualTo(OrchidGroupMutationRelationType.CORRECTS);
		});
		assertThat(page.content().get(1).relations()).hasSize(1);

		var filtered = queryService.getMutations(orchidGroupId, OrchidGroupMutationType.CORRECTION, null, 0, 20);
		assertThat(filtered.content()).singleElement().extracting("id").isEqualTo(corrected.getId());
	}

	private OrchidGroupMutation saveMutation(OrchidGroupMutationType type, String operation, Instant occurredAt) {
		var source = new OrchidGroupMutationSource(OrchidGroupMutationSourceDomain.FARM, "TEST",
				UUID.randomUUID().toString(), operation, UUID.randomUUID());
		return mutationRepository.save(new OrchidGroupMutation(type, source, "a".repeat(64), occurredAt, occurredAt,
				LocalDate.of(2026, 9, 18), "조회 테스트", 1));
	}

	private OrchidGroupStateSnapshot snapshot(int quantity, int reservedQuantity, String status) {
		return new OrchidGroupStateSnapshot(quantity, reservedQuantity, status, 10L, 1, BigDecimal.ZERO, BigDecimal.ONE,
				20L, "Cymbidium", "테스트 품종", 2, "POT_4", "POT", null, false, null, null);
	}

}
