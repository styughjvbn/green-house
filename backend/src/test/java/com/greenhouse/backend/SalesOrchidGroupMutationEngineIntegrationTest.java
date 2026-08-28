package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.greenhouse.backend.farm.application.orchid.mutation.ConsumeOrchidGroupReservationsMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationEngine;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationResult;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupStateChainMigrationService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupQuantityMutationItem;
import com.greenhouse.backend.farm.application.orchid.mutation.ReleaseOrchidGroupReservationsMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.RelatedOrchidGroupMutations;
import com.greenhouse.backend.farm.application.orchid.mutation.ReserveOrchidGroupsMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.RestoreOutboundOrchidGroupsMutationCommand;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntryRole;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSource;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSourceDomain;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationRelationType;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationType;
import com.greenhouse.backend.farm.domain.structure.BedZone;
import com.greenhouse.backend.farm.domain.structure.BedZoneSide;
import com.greenhouse.backend.farm.domain.structure.House;
import com.greenhouse.backend.farm.domain.structure.PhysicalBed;
import com.greenhouse.backend.farm.domain.variety.Variety;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationEntryRepository;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationRelationRepository;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class SalesOrchidGroupMutationEngineIntegrationTest extends AbstractBackendIntegrationTest {

	@Autowired private OrchidGroupStateChainMigrationService stateChainMigrationService;
	@Autowired private OrchidGroupMutationEngine mutationEngine;
	@Autowired private OrchidGroupMutationRepository mutationRepository;
	@Autowired private OrchidGroupMutationEntryRepository entryRepository;
	@Autowired private OrchidGroupMutationRelationRepository relationRepository;
	@Autowired private EntityManager entityManager;

	@Test
	void appliesTheSalesReservationLifecycleAsAtomicGroupLevelMutations() {
		Fixture fixture = createFixture();
		LocalDate businessDate = LocalDate.of(2026, 8, 20);
		long mutationCountBefore = mutationRepository.count();
		long entryCountBefore = entryRepository.count();
		UUID cutoverKey = UUID.randomUUID();
		OrchidGroupStateChainTestSupport.importCurrentGroups(
				stateChainMigrationService, orchidGroupRepository, cutoverKey, businessDate, "mutation-engine-test");

		var reserveCommand = new ReserveOrchidGroupsMutationCommand(
				salesSource("RESERVE:create"),
				List.of(
						new OrchidGroupQuantityMutationItem(fixture.second().getId(), 7),
						new OrchidGroupQuantityMutationItem(fixture.first().getId(), 5)),
				businessDate,
				"판매 전표 예약");
		var reserved = mutationEngine.reserve(reserveCommand);
		entityManager.flush();
		entityManager.clear();
		var replayed = mutationEngine.reserve(new ReserveOrchidGroupsMutationCommand(
				reserveCommand.source(),
				List.of(
						new OrchidGroupQuantityMutationItem(fixture.first().getId(), 5),
						new OrchidGroupQuantityMutationItem(fixture.second().getId(), 7)),
				businessDate,
				" 판매 전표 예약 "));

		assertThat(replayed.mutationId()).isEqualTo(reserved.mutationId());
		assertThat(reserved.mutationType()).isEqualTo(OrchidGroupMutationType.RESERVE);
		assertAffectedEntries(reserved.entries(), 0L, 1L);
		assertGroupState(fixture.first().getId(), 20, 5, 1L);
		assertGroupState(fixture.second().getId(), 30, 7, 1L);

		var released = mutationEngine.releaseReservation(
				new ReleaseOrchidGroupReservationsMutationCommand(
						salesSource("RELEASE:edit"),
						List.of(
								new OrchidGroupQuantityMutationItem(fixture.first().getId(), 2),
								new OrchidGroupQuantityMutationItem(fixture.second().getId(), 3)),
						businessDate,
						"판매 전표 수정 예약 해제"));
		assertThat(released.mutationType()).isEqualTo(OrchidGroupMutationType.RELEASE_RESERVATION);
		assertAffectedEntries(released.entries(), 1L, 2L);
		assertGroupState(fixture.first().getId(), 20, 3, 2L);
		assertGroupState(fixture.second().getId(), 30, 4, 2L);

		var consumed = mutationEngine.consumeReservation(
				new ConsumeOrchidGroupReservationsMutationCommand(
						salesSource("OUTBOUND:complete"),
						List.of(
								new OrchidGroupQuantityMutationItem(fixture.first().getId(), 3),
								new OrchidGroupQuantityMutationItem(fixture.second().getId(), 4)),
						businessDate,
						"판매 출고"));
		assertThat(consumed.mutationType()).isEqualTo(OrchidGroupMutationType.CONSUME_RESERVATION);
		assertAffectedEntries(consumed.entries(), 2L, 3L);
		assertGroupState(fixture.first().getId(), 17, 0, 3L);
		assertGroupState(fixture.second().getId(), 26, 0, 3L);

		var restored = mutationEngine.restoreOutbound(
				new RestoreOutboundOrchidGroupsMutationCommand(
						salesSource("RESTORE:cancel"),
						List.of(
								new OrchidGroupQuantityMutationItem(fixture.first().getId(), 3),
								new OrchidGroupQuantityMutationItem(fixture.second().getId(), 4)),
						RelatedOrchidGroupMutations.current(List.of(consumed.mutationId())),
						businessDate,
						"판매 출고 취소"));
		assertThat(restored.mutationType()).isEqualTo(OrchidGroupMutationType.RESTORE_OUTBOUND);
		assertAffectedEntries(restored.entries(), 3L, 4L);
		assertGroupState(fixture.first().getId(), 20, 0, 4L);
		assertGroupState(fixture.second().getId(), 30, 0, 4L);
		assertThat(relationRepository.findByMutationIdOrderByIdAsc(restored.mutationId()))
				.singleElement()
				.satisfies(relation -> {
					assertThat(relation.getRelatedMutation().getId()).isEqualTo(consumed.mutationId());
					assertThat(relation.getRelationType())
							.isEqualTo(OrchidGroupMutationRelationType.COMPENSATES);
				});

		assertThat(mutationRepository.count()).isEqualTo(mutationCountBefore + 5);
		assertThat(entryRepository.count()).isEqualTo(entryCountBefore + 10);
	}

	@Test
	void rejectsDuplicateGroupItemsBeforeAcquiringInventoryLocks() {
		assertThatThrownBy(() -> new ReserveOrchidGroupsMutationCommand(
				salesSource("RESERVE:duplicate"),
				List.of(
						new OrchidGroupQuantityMutationItem(1L, 2),
						new OrchidGroupQuantityMutationItem(1L, 3)),
				LocalDate.of(2026, 8, 20),
				null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("중복될 수 없습니다");
	}

	@Test
	void restoresLegacyOutboundWithoutInventingAPastCompensationRelation() {
		Fixture fixture = createFixture();
		LocalDate businessDate = LocalDate.of(2026, 8, 20);
		UUID cutoverKey = UUID.randomUUID();
		OrchidGroupStateChainTestSupport.importCurrentGroups(
				stateChainMigrationService, orchidGroupRepository, cutoverKey, businessDate, "mutation-engine-test");

		var restored = mutationEngine.restoreOutbound(
				new RestoreOutboundOrchidGroupsMutationCommand(
						salesSource("RESTORE:legacy-cancel"),
						List.of(new OrchidGroupQuantityMutationItem(fixture.first().getId(), 3)),
						RelatedOrchidGroupMutations.legacy(),
						businessDate,
						"전환 전 판매 출고 취소"));

		assertThat(restored.mutationType()).isEqualTo(OrchidGroupMutationType.RESTORE_OUTBOUND);
		assertThat(relationRepository.findByMutationIdOrderByIdAsc(restored.mutationId())).isEmpty();
		assertGroupState(fixture.first().getId(), 23, 0, 1L);
	}

	private Fixture createFixture() {
		House house = new House(907, "Sales Mutation 테스트동");
		PhysicalBed bed = new PhysicalBed(1, 1);
		bed.updatePositionUnits(new BigDecimal("20"), "칸");
		BedZone zone = new BedZone("판매 구역", BedZoneSide.LEFT, 1);
		bed.addBedZone(zone);
		house.addPhysicalBed(bed);
		houseRepository.save(house);
		Variety variety = varietyRepository.save(new Variety(
				"SALES-MUTATION", "Phalaenopsis", "Sales Mutation", null, "4치",
				true, true, null, null));
		OrchidGroup first = createGroup(zone, variety, 20, 1, "0", "2");
		OrchidGroup second = createGroup(zone, variety, 30, 2, "2", "4");
		return new Fixture(first, second);
	}

	private OrchidGroup createGroup(
			BedZone zone,
			Variety variety,
			int quantity,
			int sortOrder,
			String startPosition,
			String endPosition) {
		OrchidGroup group = new OrchidGroup(
				zone,
				variety.getGenus(),
				variety.getName(),
				quantity,
				"4치",
				2,
				"정상",
				sortOrder,
				new BigDecimal(startPosition),
				new BigDecimal(endPosition));
		group.assignVariety(variety);
		return orchidGroupRepository.save(group);
	}

	private OrchidGroupMutationSource salesSource(String operationKey) {
		return new OrchidGroupMutationSource(
				OrchidGroupMutationSourceDomain.SALES,
				"SALES_SLIP",
				"8101",
				operationKey,
				UUID.randomUUID());
	}

	private void assertAffectedEntries(
			List<OrchidGroupMutationResult.Entry> entries,
			long revisionBefore,
			long revisionAfter) {
		assertThat(entries).hasSize(2).allSatisfy(entry -> {
			assertThat(entry.role()).isEqualTo(OrchidGroupMutationEntryRole.AFFECTED);
			assertThat(entry.stateRevisionBefore()).isEqualTo(revisionBefore);
			assertThat(entry.stateRevisionAfter()).isEqualTo(revisionAfter);
		});
	}

	private void assertGroupState(Long groupId, int quantity, int reservedQuantity, long revision) {
		OrchidGroup group = orchidGroupRepository.findById(groupId).orElseThrow();
		assertThat(group.getQuantity()).isEqualTo(quantity);
		assertThat(group.getReservedQuantity()).isEqualTo(reservedQuantity);
		assertThat(group.getStateRevision()).isEqualTo(revision);
	}

	private record Fixture(OrchidGroup first, OrchidGroup second) {
	}
}
