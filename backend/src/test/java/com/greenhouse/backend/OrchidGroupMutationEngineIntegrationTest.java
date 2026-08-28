package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.farm.application.orchid.mutation.CancelOrchidGroupCreationMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.CreateOrchidGroupMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.CreateOrchidGroupMutationItem;
import com.greenhouse.backend.farm.application.orchid.mutation.CreateOrchidGroupsMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.DiscardOrchidGroupMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.MoveOrchidGroupMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationEngine;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationDetails;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupStateChainMigrationService;
import com.greenhouse.backend.farm.application.orchid.mutation.TransformOrchidGroupMutationResult;
import com.greenhouse.backend.farm.application.orchid.mutation.TransformOrchidGroupMutationSource;
import com.greenhouse.backend.farm.application.orchid.mutation.TransformOrchidGroupsMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.UpdateOrchidGroupMutationCommand;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntryKind;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntryRole;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSource;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSourceDomain;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationType;
import com.greenhouse.backend.farm.domain.structure.BedZone;
import com.greenhouse.backend.farm.domain.structure.BedZoneSide;
import com.greenhouse.backend.farm.domain.structure.House;
import com.greenhouse.backend.farm.domain.structure.PhysicalBed;
import com.greenhouse.backend.farm.domain.variety.Variety;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationEntryRepository;
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
class OrchidGroupMutationEngineIntegrationTest extends AbstractBackendIntegrationTest {

	@Autowired OrchidGroupStateChainMigrationService stateChainMigrationService;
	@Autowired OrchidGroupMutationEngine mutationEngine;
	@Autowired OrchidGroupMutationRepository mutationRepository;
	@Autowired OrchidGroupMutationEntryRepository entryRepository;
	@Autowired EntityManager entityManager;

	@Test
	void createsANewRevisionChainAndReturnsTheSameGroupOnReplay() {
		FarmFixture fixture = createFarmFixture(902);
		long groupCountBefore = orchidGroupRepository.count();
		long mutationCountBefore = mutationRepository.count();
		long entryCountBefore = entryRepository.count();
		LocalDate businessDate = LocalDate.of(2026, 8, 20);
		var source = farmSource("create-1", "CREATE");
		var command = new CreateOrchidGroupMutationCommand(
				source,
				fixture.sourceZone().getId(),
				new OrchidGroupMutationDetails(
						fixture.variety().getId(),
						15,
						"4치",
						3,
						"정상",
						" POT ",
						2,
						false,
						new BigDecimal("2"),
						new BigDecimal("4"),
						" 생성 메모 "),
				businessDate,
				"직접 생성");

		var created = mutationEngine.create(command);
		entityManager.flush();
		entityManager.clear();
		var replayed = mutationEngine.create(new CreateOrchidGroupMutationCommand(
				source,
				fixture.sourceZone().getId(),
				new OrchidGroupMutationDetails(
						fixture.variety().getId(),
						15,
						"4\"",
						3,
						" 정상 ",
						"POT",
						2,
						false,
						new BigDecimal("2.0"),
						new BigDecimal("4.00"),
						"생성 메모"),
				businessDate,
				" 직접 생성 "));

		assertThat(replayed.mutationId()).isEqualTo(created.mutationId());
		assertThat(created.mutationType()).isEqualTo(OrchidGroupMutationType.CREATE);
		assertThat(created.entries()).singleElement().satisfies(entry -> {
			assertThat(entry.entryKind()).isEqualTo(OrchidGroupMutationEntryKind.CREATE);
			assertThat(entry.role()).isEqualTo(OrchidGroupMutationEntryRole.RESULT);
			assertThat(entry.stateRevisionBefore()).isNull();
			assertThat(entry.stateRevisionAfter()).isEqualTo(1L);
			assertThat(entry.afterState().quantity()).isEqualTo(15);
			assertThat(entry.afterState().potSizeCode()).isEqualTo("POT_4");
			assertThat(entry.afterState().placementType()).isEqualTo("POT");
			assertThat(entry.afterState().memo()).isEqualTo("생성 메모");
		});
		Long createdGroupId = created.entries().getFirst().orchidGroupId();
		OrchidGroup group = orchidGroupRepository.findById(createdGroupId).orElseThrow();
		assertThat(group.getStateRevision()).isEqualTo(1L);
		assertThat(group.getStartPosition()).isEqualByComparingTo("2.00");
		assertThat(group.getEndPosition()).isEqualByComparingTo("4.00");
		assertThat(orchidGroupRepository.count()).isEqualTo(groupCountBefore + 1);
		assertThat(mutationRepository.count()).isEqualTo(mutationCountBefore + 1);
		assertThat(entryRepository.count()).isEqualTo(entryCountBefore + 1);
	}

	@Test
	void updatesMovesAndCancelsCreationWhileKeepingAContinuousRevisionChain() {
		FarmFixture fixture = createFarmFixture(903);
		OrchidGroup group = createOrchidGroup(fixture, 20);
		Variety nextVariety = varietyRepository.save(new Variety(
				"MUTATION-NEXT", "Cattleya", "Mutation Next", null, "4치",
				true, true, null, null));
		UUID cutoverKey = UUID.randomUUID();
		LocalDate businessDate = LocalDate.of(2026, 8, 20);
		long mutationCountBefore = mutationRepository.count();
		long entryCountBefore = entryRepository.count();
		OrchidGroupStateChainTestSupport.importCurrentGroups(
				stateChainMigrationService, orchidGroupRepository, cutoverKey, businessDate, "mutation-engine-test");

		var updated = mutationEngine.updateDetails(new UpdateOrchidGroupMutationCommand(
				farmSource("update-1", "UPDATE_DETAILS"),
				group.getId(),
				new OrchidGroupMutationDetails(
						nextVariety.getId(), 12, "4치", 4, "관리",
						"TRAY", 3, true,
						new BigDecimal("1"), new BigDecimal("3"), "상태 수정"),
				businessDate,
				"운영 보정"));
		var moved = mutationEngine.move(new MoveOrchidGroupMutationCommand(
				farmSource("move-1", "MOVE"),
				group.getId(),
				fixture.destinationZone().getId(),
				new BigDecimal("3"),
				new BigDecimal("5"),
				businessDate,
				"배치 이동"));
		var canceled = mutationEngine.cancelCreation(new CancelOrchidGroupCreationMutationCommand(
				farmSource("cancel-1", "CANCEL_CREATION"),
				group.getId(),
				businessDate,
				"오생성 취소"));

		assertThat(updated.entries()).singleElement().satisfies(entry -> {
			assertThat(entry.stateRevisionBefore()).isZero();
			assertThat(entry.stateRevisionAfter()).isEqualTo(1L);
			assertThat(entry.beforeState().quantity()).isEqualTo(20);
			assertThat(entry.afterState().quantity()).isEqualTo(12);
			assertThat(entry.afterState().varietyId()).isEqualTo(nextVariety.getId());
		});
		assertThat(moved.entries()).singleElement().satisfies(entry -> {
			assertThat(entry.stateRevisionBefore()).isEqualTo(1L);
			assertThat(entry.stateRevisionAfter()).isEqualTo(2L);
			assertThat(entry.beforeState().bedZoneId()).isEqualTo(fixture.sourceZone().getId());
			assertThat(entry.afterState().bedZoneId()).isEqualTo(fixture.destinationZone().getId());
		});
		assertThat(canceled.entries()).singleElement().satisfies(entry -> {
			assertThat(entry.stateRevisionBefore()).isEqualTo(2L);
			assertThat(entry.stateRevisionAfter()).isEqualTo(3L);
			assertThat(entry.afterState().quantity()).isZero();
			assertThat(entry.afterState().status()).isEqualTo("생성 취소");
		});
		assertThat(group.getStateRevision()).isEqualTo(3L);
		assertThat(group.getQuantity()).isZero();
		assertThat(group.getStatus()).isEqualTo("생성 취소");
		assertThat(group.getBedZone().getId()).isEqualTo(fixture.destinationZone().getId());
		assertThat(mutationRepository.count()).isEqualTo(mutationCountBefore + 4);
		assertThat(entryRepository.count()).isEqualTo(entryCountBefore + 4);
		assertThat(entryRepository.findByMutationIdOrderByIdAsc(canceled.mutationId())).hasSize(1);

		assertThatThrownBy(() -> mutationEngine.cancelCreation(new CancelOrchidGroupCreationMutationCommand(
				farmSource("cancel-2", "CANCEL_CREATION"),
				group.getId(),
				businessDate,
				"다른 취소 요청")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("변경 전후");
		assertThat(mutationRepository.count()).isEqualTo(mutationCountBefore + 4);
	}

	@Test
	void establishesBaselineAndAppliesDiscardExactlyOnce() {
		OrchidGroup group = createOrchidGroup(20);
		long mutationCountBefore = mutationRepository.count();
		long entryCountBefore = entryRepository.count();
		UUID cutoverKey = UUID.randomUUID();
		LocalDate businessDate = LocalDate.of(2026, 8, 20);
		var imported = OrchidGroupStateChainTestSupport.importCurrentGroups(
				stateChainMigrationService, orchidGroupRepository, cutoverKey, businessDate, "mutation-engine-test");

		assertThat(imported.importedMutationCount()).isEqualTo(1);
		assertThat(entryRepository.findStateChainByOrchidGroupIdIn(List.of(group.getId())))
				.singleElement()
				.satisfies(entry -> {
					assertThat(entry.getMutation().getMutationType())
							.isEqualTo(OrchidGroupMutationType.BASELINE_IMPORT);
					assertThat(entry.getEntryKind()).isEqualTo(OrchidGroupMutationEntryKind.BASELINE);
					assertThat(entry.getStateRevisionBefore()).isNull();
					assertThat(entry.getStateRevisionAfter()).isZero();
					assertThat(entry.getAfterState().quantity()).isEqualTo(20);
				});
		assertThat(group.getStateRevision()).isZero();

		UUID correlationId = UUID.randomUUID();
		var source = new OrchidGroupMutationSource(
				OrchidGroupMutationSourceDomain.WORK,
				"WORK_EFFECT",
				"9101",
				"TARGET:8101",
				correlationId);
		var command = new DiscardOrchidGroupMutationCommand(
				source, group.getId(), 4, businessDate, "병해 폐기");

		var first = mutationEngine.discard(command);
		entityManager.flush();
		entityManager.clear();
		var replayed = mutationEngine.discard(command);
		OrchidGroup reloaded = orchidGroupRepository.findById(group.getId()).orElseThrow();

		assertThat(replayed.mutationId()).isEqualTo(first.mutationId());
		assertThat(reloaded.getQuantity()).isEqualTo(16);
		assertThat(reloaded.getStateRevision()).isEqualTo(1L);
		assertThat(first.entries()).singleElement().satisfies(entry -> {
			assertThat(entry.entryKind()).isEqualTo(OrchidGroupMutationEntryKind.CHANGE);
			assertThat(entry.stateRevisionBefore()).isZero();
			assertThat(entry.stateRevisionAfter()).isEqualTo(1L);
			assertThat(entry.beforeState().quantity()).isEqualTo(20);
			assertThat(entry.afterState().quantity()).isEqualTo(16);
		});
		assertThat(mutationRepository.count()).isEqualTo(mutationCountBefore + 2);
		assertThat(entryRepository.count()).isEqualTo(entryCountBefore + 2);
	}

	@Test
	void rejectsReusingMutationSourceForDifferentPayload() {
		OrchidGroup group = createOrchidGroup(20);
		UUID cutoverKey = UUID.randomUUID();
		LocalDate businessDate = LocalDate.of(2026, 8, 20);
		OrchidGroupStateChainTestSupport.importCurrentGroups(
				stateChainMigrationService, orchidGroupRepository, cutoverKey, businessDate, "mutation-engine-test");
		var source = new OrchidGroupMutationSource(
				OrchidGroupMutationSourceDomain.WORK,
				"WORK_EFFECT",
				"9102",
				"TARGET:8102",
				UUID.randomUUID());
		mutationEngine.discard(new DiscardOrchidGroupMutationCommand(
				source, group.getId(), 4, businessDate, "병해 폐기"));

		assertThatThrownBy(() -> mutationEngine.discard(new DiscardOrchidGroupMutationCommand(
				source, group.getId(), 5, businessDate, "병해 폐기")))
				.isInstanceOf(ConflictException.class)
				.hasMessageContaining("다른 command");
		assertThat(group.getQuantity()).isEqualTo(16);
		assertThat(group.getStateRevision()).isEqualTo(1L);
	}

	@Test
	void createsMultipleGroupsAsOneMutationAndReplaysTheOrderedResults() {
		FarmFixture fixture = createFarmFixture(904);
		long groupCountBefore = orchidGroupRepository.count();
		long mutationCountBefore = mutationRepository.count();
		long entryCountBefore = entryRepository.count();
		LocalDate businessDate = LocalDate.of(2026, 8, 20);
		var source = farmSource("create-many-1", "CREATE_MANY");
		var command = new CreateOrchidGroupsMutationCommand(
				source,
				List.of(
						new CreateOrchidGroupMutationItem(
								fixture.sourceZone().getId(),
								details(fixture.variety().getId(), 11, "3.5치", "0", "2")),
						new CreateOrchidGroupMutationItem(
								fixture.sourceZone().getId(),
								details(fixture.variety().getId(), 13, "4치", "2", "4"))),
				businessDate,
				"초기 다중 생성");

		var created = mutationEngine.createMany(command);
		entityManager.flush();
		entityManager.clear();
		var replayed = mutationEngine.createMany(new CreateOrchidGroupsMutationCommand(
				source,
				List.of(
						new CreateOrchidGroupMutationItem(
								fixture.sourceZone().getId(),
								details(fixture.variety().getId(), 11, "3.5\"", "0.00", "2.0")),
						new CreateOrchidGroupMutationItem(
								fixture.sourceZone().getId(),
								details(fixture.variety().getId(), 13, "4\"", "2.00", "4.0"))),
				businessDate,
				" 초기 다중 생성 "));

		assertThat(replayed.mutationId()).isEqualTo(created.mutationId());
		assertThat(created.mutationType()).isEqualTo(OrchidGroupMutationType.CREATE);
		assertThat(created.entries())
				.hasSize(2)
				.allSatisfy(entry -> {
					assertThat(entry.entryKind()).isEqualTo(OrchidGroupMutationEntryKind.CREATE);
					assertThat(entry.role()).isEqualTo(OrchidGroupMutationEntryRole.RESULT);
					assertThat(entry.stateRevisionAfter()).isEqualTo(1L);
				});
		assertThat(created.entries())
				.extracting(entry -> entry.afterState().quantity())
				.containsExactly(11, 13);
		assertThat(orchidGroupRepository.count()).isEqualTo(groupCountBefore + 2);
		assertThat(mutationRepository.count()).isEqualTo(mutationCountBefore + 1);
		assertThat(entryRepository.count()).isEqualTo(entryCountBefore + 2);
	}

	@Test
	void transformsMultipleSourcesAndResultsAsOneRevisionedMutation() {
		FarmFixture fixture = createFarmFixture(905);
		OrchidGroup firstSource = createOrchidGroup(
				fixture, 20, BigDecimal.ZERO, new BigDecimal("2"), 1);
		OrchidGroup secondSource = createOrchidGroup(
				fixture, 30, new BigDecimal("2"), new BigDecimal("4"), 2);
		UUID cutoverKey = UUID.randomUUID();
		LocalDate businessDate = LocalDate.of(2026, 8, 20);
		OrchidGroupStateChainTestSupport.importCurrentGroups(
				stateChainMigrationService, orchidGroupRepository, cutoverKey, businessDate, "mutation-engine-test");
		long groupCountBefore = orchidGroupRepository.count();
		long mutationCountBefore = mutationRepository.count();
		long entryCountBefore = entryRepository.count();
		var mutationSource = new OrchidGroupMutationSource(
				OrchidGroupMutationSourceDomain.WORK,
				"WORK_EFFECT",
				"9201",
				"EXECUTION:round-1",
				UUID.randomUUID());
		var command = new TransformOrchidGroupsMutationCommand(
				mutationSource,
				List.of(
						new TransformOrchidGroupMutationSource(secondSource.getId(), 7, null, null),
						new TransformOrchidGroupMutationSource(firstSource.getId(), 5, null, null)),
				List.of(
						new TransformOrchidGroupMutationResult(
								fixture.destinationZone().getId(),
								details(fixture.variety().getId(), 6, "4치", "0", "2")),
						new TransformOrchidGroupMutationResult(
								fixture.destinationZone().getId(),
								details(fixture.variety().getId(), 6, "4치", "2", "4"))),
				businessDate,
				"N:M 분갈이");

		var transformed = mutationEngine.transform(command);
		entityManager.flush();
		entityManager.clear();
		var replayed = mutationEngine.transform(new TransformOrchidGroupsMutationCommand(
				mutationSource,
				List.of(
						new TransformOrchidGroupMutationSource(firstSource.getId(), 5, null, null),
						new TransformOrchidGroupMutationSource(secondSource.getId(), 7, null, null)),
				command.results(),
				businessDate,
				" N:M 분갈이 "));

		assertThat(replayed.mutationId()).isEqualTo(transformed.mutationId());
		assertThat(transformed.mutationType()).isEqualTo(OrchidGroupMutationType.TRANSFORM);
		assertThat(transformed.entries()).hasSize(4);
		assertThat(transformed.entries().subList(0, 2)).allSatisfy(entry -> {
			assertThat(entry.entryKind()).isEqualTo(OrchidGroupMutationEntryKind.CHANGE);
			assertThat(entry.role()).isEqualTo(OrchidGroupMutationEntryRole.SOURCE);
			assertThat(entry.stateRevisionBefore()).isZero();
			assertThat(entry.stateRevisionAfter()).isEqualTo(1L);
		});
		assertThat(transformed.entries().subList(2, 4)).allSatisfy(entry -> {
			assertThat(entry.entryKind()).isEqualTo(OrchidGroupMutationEntryKind.CREATE);
			assertThat(entry.role()).isEqualTo(OrchidGroupMutationEntryRole.RESULT);
			assertThat(entry.stateRevisionAfter()).isEqualTo(1L);
		});
		assertThat(orchidGroupRepository.findById(firstSource.getId()).orElseThrow().getQuantity())
				.isEqualTo(15);
		assertThat(orchidGroupRepository.findById(secondSource.getId()).orElseThrow().getQuantity())
				.isEqualTo(23);
		assertThat(orchidGroupRepository.count()).isEqualTo(groupCountBefore + 2);
		assertThat(mutationRepository.count()).isEqualTo(mutationCountBefore + 1);
		assertThat(entryRepository.count()).isEqualTo(entryCountBefore + 4);
	}

	private OrchidGroup createOrchidGroup(int quantity) {
		return createOrchidGroup(createFarmFixture(901), quantity);
	}

	private OrchidGroup createOrchidGroup(FarmFixture fixture, int quantity) {
		return createOrchidGroup(fixture, quantity, BigDecimal.ZERO, BigDecimal.ONE, 1);
	}

	private OrchidGroup createOrchidGroup(
			FarmFixture fixture,
			int quantity,
			BigDecimal startPosition,
			BigDecimal endPosition,
			int sortOrder) {
		Variety variety = fixture.variety();
		OrchidGroup group = new OrchidGroup(
				fixture.sourceZone(),
				variety.getGenus(),
				variety.getName(),
				quantity,
				"3.5치",
				2,
				"정상",
				sortOrder,
				startPosition,
				endPosition);
		group.assignVariety(variety);
		return orchidGroupRepository.save(group);
	}

	private OrchidGroupMutationDetails details(
			Long varietyId,
			int quantity,
			String potSize,
			String startPosition,
			String endPosition) {
		return new OrchidGroupMutationDetails(
				varietyId,
				quantity,
				potSize,
				2,
				"정상",
				"POT",
				null,
				false,
				new BigDecimal(startPosition),
				new BigDecimal(endPosition),
				null);
	}

	private FarmFixture createFarmFixture(int houseNumber) {
		House house = new House(houseNumber, "Mutation 테스트동");
		PhysicalBed bed = new PhysicalBed(1, 1);
		bed.updatePositionUnits(new BigDecimal("20"), "칸");
		BedZone sourceZone = new BedZone("원본 구역", BedZoneSide.LEFT, 1);
		BedZone destinationZone = new BedZone("목적 구역", BedZoneSide.RIGHT, 2);
		bed.addBedZone(sourceZone);
		bed.addBedZone(destinationZone);
		house.addPhysicalBed(bed);
		houseRepository.save(house);
		Variety variety = varietyRepository.save(new Variety(
				"MUTATION-TEST-" + houseNumber, "Phalaenopsis", "Mutation Test", null, "3.5치",
				true, true, null, null));
		return new FarmFixture(sourceZone, destinationZone, variety);
	}

	private OrchidGroupMutationSource farmSource(String referenceId, String operationKey) {
		return new OrchidGroupMutationSource(
				OrchidGroupMutationSourceDomain.FARM,
				"ORCHID_GROUP_COMMAND",
				referenceId,
				operationKey,
				UUID.randomUUID());
	}

	private record FarmFixture(BedZone sourceZone, BedZone destinationZone, Variety variety) {
	}
}
