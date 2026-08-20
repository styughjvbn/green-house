package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.farm.application.orchid.mutation.BaselineOrchidGroupsCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.CorrectOrchidGroupMutationItem;
import com.greenhouse.backend.farm.application.orchid.mutation.CorrectOrchidGroupsMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.CreateOrchidGroupMutationItem;
import com.greenhouse.backend.farm.application.orchid.mutation.CreateOrchidGroupsMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerPreparationService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationDetails;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationEngine;
import com.greenhouse.backend.farm.application.orchid.mutation.RelatedOrchidGroupMutations;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationRelationType;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSource;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSourceDomain;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationType;
import com.greenhouse.backend.farm.domain.structure.BedZone;
import com.greenhouse.backend.farm.domain.structure.BedZoneSide;
import com.greenhouse.backend.farm.domain.structure.House;
import com.greenhouse.backend.farm.domain.structure.PhysicalBed;
import com.greenhouse.backend.farm.domain.variety.Variety;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationRelationRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class CorrectionCompensationOrchidGroupMutationEngineIntegrationTest
		extends AbstractBackendIntegrationTest {

	@Autowired private OrchidGroupLedgerPreparationService ledgerPreparationService;
	@Autowired private OrchidGroupMutationEngine mutationEngine;
	@Autowired private OrchidGroupMutationRelationRepository relationRepository;
	@Autowired private EntityManager entityManager;

	@Test
	void correctsResultsFromMultipleMutationsAndRecordsManyToManyRelations() {
		Fixture fixture = createFixture();
		LocalDate businessDate = LocalDate.of(2026, 8, 20);
		var firstCreated = mutationEngine.createMany(new CreateOrchidGroupsMutationCommand(
				farmSource("create-first", "CREATE"),
				List.of(new CreateOrchidGroupMutationItem(
						fixture.zone().getId(), details(fixture.variety().getId(), 20, "0", "2"))),
				businessDate,
				"첫 번째 생성"));
		var secondCreated = mutationEngine.createMany(new CreateOrchidGroupsMutationCommand(
				farmSource("create-second", "CREATE"),
				List.of(new CreateOrchidGroupMutationItem(
						fixture.zone().getId(), details(fixture.variety().getId(), 30, "2", "4"))),
				businessDate,
				"두 번째 생성"));
		Long firstGroupId = firstCreated.entries().getFirst().orchidGroupId();
		Long secondGroupId = secondCreated.entries().getFirst().orchidGroupId();
		OrchidGroupMutationSource correctionSource = workSource("correction-current");

		var corrected = mutationEngine.correct(new CorrectOrchidGroupsMutationCommand(
				correctionSource,
				List.of(
						new CorrectOrchidGroupMutationItem(secondGroupId, 27, " 수량 보정 "),
						new CorrectOrchidGroupMutationItem(firstGroupId, 18, "수량 보정")),
				RelatedOrchidGroupMutations.current(List.of(
						secondCreated.mutationId(), firstCreated.mutationId())),
				businessDate,
				"결과 수량 확인"));
		entityManager.flush();
		entityManager.clear();
		var replayed = mutationEngine.correct(new CorrectOrchidGroupsMutationCommand(
				correctionSource,
				List.of(
						new CorrectOrchidGroupMutationItem(firstGroupId, 18, "수량 보정"),
						new CorrectOrchidGroupMutationItem(secondGroupId, 27, "수량 보정")),
				RelatedOrchidGroupMutations.current(List.of(
						firstCreated.mutationId(), secondCreated.mutationId())),
				businessDate,
				" 결과 수량 확인 "));

		assertThat(replayed.mutationId()).isEqualTo(corrected.mutationId());
		assertThat(corrected.mutationType()).isEqualTo(OrchidGroupMutationType.CORRECTION);
		assertThat(corrected.entries()).hasSize(2).allSatisfy(entry -> {
			assertThat(entry.stateRevisionBefore()).isEqualTo(1L);
			assertThat(entry.stateRevisionAfter()).isEqualTo(2L);
			assertThat(entry.afterState().status()).isEqualTo("수량 보정");
		});
		assertGroup(firstGroupId, 18, 2L);
		assertGroup(secondGroupId, 27, 2L);
		assertThat(relationRepository.findByMutationIdOrderByIdAsc(corrected.mutationId()))
				.hasSize(2)
				.allSatisfy(relation -> assertThat(relation.getRelationType())
						.isEqualTo(OrchidGroupMutationRelationType.CORRECTS))
				.extracting(relation -> relation.getRelatedMutation().getId())
				.containsExactlyInAnyOrder(firstCreated.mutationId(), secondCreated.mutationId());
	}

	@Test
	void correctsALegacyResultWithoutInventingAPastMutationRelation() {
		Fixture fixture = createFixture();
		OrchidGroup group = new OrchidGroup(
				fixture.zone(),
				fixture.variety().getGenus(),
				fixture.variety().getName(),
				20,
				"4치",
				2,
				"정상",
				1,
				new BigDecimal("0"),
				new BigDecimal("2"));
		group.assignVariety(fixture.variety());
		orchidGroupRepository.save(group);
		UUID cutoverKey = UUID.randomUUID();
		LocalDate businessDate = LocalDate.of(2026, 8, 20);
		ledgerPreparationService.prepare(cutoverKey, businessDate, "mutation-engine-test");
		ledgerPreparationService.start(cutoverKey);
		ledgerPreparationService.baselineBatch(new BaselineOrchidGroupsCommand(
				cutoverKey, "GROUPS-0001", List.of(group.getId()), businessDate));

		var corrected = mutationEngine.correct(new CorrectOrchidGroupsMutationCommand(
				workSource("correction-legacy"),
				List.of(new CorrectOrchidGroupMutationItem(group.getId(), 17, "수량 보정")),
				RelatedOrchidGroupMutations.legacy(),
				businessDate,
				"전환 전 작업 결과 보정"));

		assertThat(corrected.mutationType()).isEqualTo(OrchidGroupMutationType.CORRECTION);
		assertThat(corrected.entries()).singleElement().satisfies(entry -> {
			assertThat(entry.stateRevisionBefore()).isZero();
			assertThat(entry.stateRevisionAfter()).isEqualTo(1L);
		});
		assertThat(relationRepository.findByMutationIdOrderByIdAsc(corrected.mutationId())).isEmpty();
		assertGroup(group.getId(), 17, 1L);
	}

	private Fixture createFixture() {
		House house = new House(908, "Correction Mutation 테스트동");
		PhysicalBed bed = new PhysicalBed(1, 1);
		bed.updatePositionUnits(new BigDecimal("20"), "칸");
		BedZone zone = new BedZone("보정 구역", BedZoneSide.LEFT, 1);
		bed.addBedZone(zone);
		house.addPhysicalBed(bed);
		houseRepository.save(house);
		Variety variety = varietyRepository.save(new Variety(
				"CORRECTION-MUTATION", "Phalaenopsis", "Correction Mutation", null, "4치",
				true, true, null, null));
		return new Fixture(zone, variety);
	}

	private OrchidGroupMutationDetails details(
			Long varietyId,
			int quantity,
			String startPosition,
			String endPosition) {
		return new OrchidGroupMutationDetails(
				varietyId,
				quantity,
				"4치",
				2,
				"정상",
				"POT",
				null,
				false,
				new BigDecimal(startPosition),
				new BigDecimal(endPosition),
				null);
	}

	private OrchidGroupMutationSource farmSource(String referenceId, String operationKey) {
		return new OrchidGroupMutationSource(
				OrchidGroupMutationSourceDomain.FARM,
				"ORCHID_GROUP_COMMAND",
				referenceId,
				operationKey,
				UUID.randomUUID());
	}

	private OrchidGroupMutationSource workSource(String referenceId) {
		return new OrchidGroupMutationSource(
				OrchidGroupMutationSourceDomain.WORK,
				"WORK_EFFECT",
				referenceId,
				"OPERATION",
				UUID.randomUUID());
	}

	private void assertGroup(Long groupId, int quantity, long revision) {
		OrchidGroup group = orchidGroupRepository.findById(groupId).orElseThrow();
		assertThat(group.getQuantity()).isEqualTo(quantity);
		assertThat(group.getStatus()).isEqualTo("수량 보정");
		assertThat(group.getStateRevision()).isEqualTo(revision);
	}

	private record Fixture(BedZone zone, Variety variety) {
	}
}
