package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.farm.application.orchid.mutation.BaselineOrchidGroupsCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.DiscardOrchidGroupMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerPreparationService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationEngine;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntryKind;
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

	@Autowired OrchidGroupLedgerPreparationService ledgerPreparationService;
	@Autowired OrchidGroupMutationEngine mutationEngine;
	@Autowired OrchidGroupMutationRepository mutationRepository;
	@Autowired OrchidGroupMutationEntryRepository entryRepository;
	@Autowired EntityManager entityManager;

	@Test
	void establishesBaselineAndAppliesDiscardExactlyOnce() {
		OrchidGroup group = createOrchidGroup(20);
		UUID cutoverKey = UUID.randomUUID();
		LocalDate businessDate = LocalDate.of(2026, 8, 20);
		ledgerPreparationService.prepare(cutoverKey, businessDate, "mutation-engine-test");
		ledgerPreparationService.start(cutoverKey);

		var baseline = ledgerPreparationService.baselineBatch(new BaselineOrchidGroupsCommand(
				cutoverKey, "GROUPS-0001", List.of(group.getId()), businessDate));

		assertThat(baseline.mutationType()).isEqualTo(OrchidGroupMutationType.BASELINE_IMPORT);
		assertThat(baseline.entries()).singleElement().satisfies(entry -> {
			assertThat(entry.entryKind()).isEqualTo(OrchidGroupMutationEntryKind.BASELINE);
			assertThat(entry.stateRevisionBefore()).isNull();
			assertThat(entry.stateRevisionAfter()).isZero();
			assertThat(entry.afterState().quantity()).isEqualTo(20);
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
		assertThat(mutationRepository.count()).isEqualTo(2);
		assertThat(entryRepository.count()).isEqualTo(2);
	}

	@Test
	void rejectsReusingMutationSourceForDifferentPayload() {
		OrchidGroup group = createOrchidGroup(20);
		UUID cutoverKey = UUID.randomUUID();
		LocalDate businessDate = LocalDate.of(2026, 8, 20);
		ledgerPreparationService.prepare(cutoverKey, businessDate, "mutation-engine-test");
		ledgerPreparationService.start(cutoverKey);
		ledgerPreparationService.baselineBatch(new BaselineOrchidGroupsCommand(
				cutoverKey, "GROUPS-0001", List.of(group.getId()), businessDate));
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

	private OrchidGroup createOrchidGroup(int quantity) {
		House house = new House(901, "Mutation 테스트동");
		PhysicalBed bed = new PhysicalBed(1, 1);
		BedZone zone = new BedZone("테스트 구역", BedZoneSide.LEFT, 1);
		bed.addBedZone(zone);
		house.addPhysicalBed(bed);
		houseRepository.save(house);
		Variety variety = varietyRepository.save(new Variety(
				"MUTATION-TEST", "Phalaenopsis", "Mutation Test", null, "3.5치",
				true, true, null, null));
		OrchidGroup group = new OrchidGroup(
				zone,
				variety.getGenus(),
				variety.getName(),
				quantity,
				"3.5치",
				2,
				"정상",
				1,
				BigDecimal.ZERO,
				BigDecimal.ONE);
		group.assignVariety(variety);
		return orchidGroupRepository.save(group);
	}
}
