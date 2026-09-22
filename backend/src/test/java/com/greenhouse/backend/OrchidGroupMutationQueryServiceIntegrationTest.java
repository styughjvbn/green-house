package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationGraphQueryService;
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
import com.greenhouse.backend.farm.domain.structure.BedZone;
import com.greenhouse.backend.farm.domain.structure.BedZoneSide;
import com.greenhouse.backend.farm.domain.structure.House;
import com.greenhouse.backend.farm.domain.structure.PhysicalBed;
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
	OrchidGroupMutationGraphQueryService graphQueryService;

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

		var graph = graphQueryService.getGraph(orchidGroupId, 0, 20);
		assertThat(graph.truncated()).isFalse();
		assertThat(graph.nodes()).extracting("id")
			.containsExactlyInAnyOrder("mutation-" + created.getId(), "mutation-" + corrected.getId(),
					"group-" + orchidGroupId + "-revision-1", "group-" + orchidGroupId + "-revision-2");
		assertThat(graph.edges()).extracting("edgeType")
			.containsExactlyInAnyOrder(
					com.greenhouse.backend.farm.dto.orchid.OrchidGroupMutationGraphEdgeType.STATE_INPUT,
					com.greenhouse.backend.farm.dto.orchid.OrchidGroupMutationGraphEdgeType.STATE_OUTPUT,
					com.greenhouse.backend.farm.dto.orchid.OrchidGroupMutationGraphEdgeType.STATE_OUTPUT,
					com.greenhouse.backend.farm.dto.orchid.OrchidGroupMutationGraphEdgeType.MUTATION_RELATION);
	}

	@Test
	void expandsTransformResultsAndTheirMutationHistoryWithinDepth() {
		long sourceGroupId = 92_001L;
		long resultGroupId = 92_002L;
		var created = saveMutation(OrchidGroupMutationType.CREATE, "CREATE_SOURCE",
				Instant.parse("2026-09-18T01:00:00Z"));
		entryRepository.save(OrchidGroupMutationEntry.created(created, sourceGroupId,
				OrchidGroupMutationEntryRole.RESULT, snapshot(10, 0, "정상")));

		var transformed = saveMutation(OrchidGroupMutationType.TRANSFORM, "DIVIDE",
				Instant.parse("2026-09-18T02:00:00Z"));
		entryRepository.save(OrchidGroupMutationEntry.changed(transformed, sourceGroupId,
				OrchidGroupMutationEntryRole.SOURCE, 1L, snapshot(10, 0, "정상"), snapshot(4, 0, "정상")));
		entryRepository.save(OrchidGroupMutationEntry.created(transformed, resultGroupId,
				OrchidGroupMutationEntryRole.RESULT, snapshot(6, 0, "정상")));

		var moved = saveMutation(OrchidGroupMutationType.MOVE, "MOVE_RESULT", Instant.parse("2026-09-18T03:00:00Z"));
		entryRepository.save(OrchidGroupMutationEntry.changed(moved, resultGroupId,
				OrchidGroupMutationEntryRole.AFFECTED, 1L, snapshot(6, 0, "정상"), snapshot(6, 0, "이동")));

		var graph = graphQueryService.getGraph(sourceGroupId, 1, 40);

		assertThat(graph.nodes()).extracting("id")
			.contains("mutation-" + transformed.getId(), "mutation-" + moved.getId(),
					"group-" + sourceGroupId + "-revision-2", "group-" + resultGroupId + "-revision-1",
					"group-" + resultGroupId + "-revision-2");
		assertThat(graph.edges()).anySatisfy(edge -> {
			assertThat(edge.sourceNodeId()).isEqualTo("mutation-" + transformed.getId());
			assertThat(edge.targetNodeId()).isEqualTo("group-" + resultGroupId + "-revision-1");
			assertThat(edge.entryRole()).isEqualTo(OrchidGroupMutationEntryRole.RESULT);
		});
	}

	@Test
	void resolvesGraphStateLocationFromSnapshotBedZoneInOneStructuredResponse() {
		var house = new House(991, "그래프 위치 테스트동");
		var bed = new PhysicalBed(7, 1);
		var zone = new BedZone("오른쪽 구역", BedZoneSide.RIGHT, 1);
		bed.addBedZone(zone);
		house.addPhysicalBed(bed);
		houseRepository.saveAndFlush(house);

		long orchidGroupId = 93_001L;
		var created = saveMutation(OrchidGroupMutationType.CREATE, "CREATE_WITH_LOCATION",
				Instant.parse("2026-09-18T04:00:00Z"));
		entryRepository
			.save(OrchidGroupMutationEntry.created(created, orchidGroupId, OrchidGroupMutationEntryRole.RESULT,
					snapshotAt(10, 0, "정상", zone.getId(), new BigDecimal("2"), new BigDecimal("5"))));

		var graph = graphQueryService.getGraph(orchidGroupId, 0, 20);

		assertThat(graph.nodes()).filteredOn(node -> node.orchidGroupId() != null).singleElement().satisfies(node -> {
			assertThat(node.state().varietyName()).isEqualTo("테스트 품종");
			assertThat(node.location().houseNumber()).isEqualTo(991);
			assertThat(node.location().physicalBedNumber()).isEqualTo(7);
			assertThat(node.location().side()).isEqualTo(BedZoneSide.RIGHT);
			assertThat(node.location().startPosition()).isEqualByComparingTo("2");
			assertThat(node.location().endPosition()).isEqualByComparingTo("5");
		});
	}

	private OrchidGroupMutation saveMutation(OrchidGroupMutationType type, String operation, Instant occurredAt) {
		var source = new OrchidGroupMutationSource(OrchidGroupMutationSourceDomain.FARM, "TEST",
				UUID.randomUUID().toString(), operation, UUID.randomUUID());
		return mutationRepository.save(new OrchidGroupMutation(type, source, "a".repeat(64), occurredAt, occurredAt,
				LocalDate.of(2026, 9, 18), "조회 테스트", 1));
	}

	private OrchidGroupStateSnapshot snapshot(int quantity, int reservedQuantity, String status) {
		return snapshotAt(quantity, reservedQuantity, status, 10L, BigDecimal.ZERO, BigDecimal.ONE);
	}

	private OrchidGroupStateSnapshot snapshotAt(int quantity, int reservedQuantity, String status, Long bedZoneId,
			BigDecimal startPosition, BigDecimal endPosition) {
		return new OrchidGroupStateSnapshot(quantity, reservedQuantity, status, bedZoneId, 1, startPosition,
				endPosition, 20L, "Cymbidium", "테스트 품종", 2, "POT_4", "POT", null, false, null, null);
	}

}
