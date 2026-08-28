package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.farm.application.orchid.mutation.CreateOrchidGroupMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerCutoverCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerCutoverService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerPreparationService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerReconciliationService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerReconciliationStage;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationDetails;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationEngine;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupStateChainMigrationService;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupLedgerCoverageStatus;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSource;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSourceDomain;
import com.greenhouse.backend.farm.domain.structure.BedZone;
import com.greenhouse.backend.farm.domain.structure.BedZoneSide;
import com.greenhouse.backend.farm.domain.structure.House;
import com.greenhouse.backend.farm.domain.structure.PhysicalBed;
import com.greenhouse.backend.farm.domain.variety.Variety;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupLedgerCoverageRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class OrchidGroupLedgerReconciliationIntegrationTest extends AbstractBackendIntegrationTest {

	private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 8, 20);

	@Autowired private OrchidGroupLedgerReconciliationService reconciliationService;
	@Autowired private OrchidGroupLedgerPreparationService preparationService;
	@Autowired private OrchidGroupLedgerCutoverService cutoverService;
	@Autowired private OrchidGroupMutationEngine mutationEngine;
	@Autowired private OrchidGroupStateChainMigrationService stateChainMigrationService;
	@Autowired private OrchidGroupLedgerCoverageRepository coverageRepository;
	@Autowired private EntityManager entityManager;

	@Test
	void reportsAValidPreBaselineDatabaseWithoutWritingLedgerState() {
		OrchidGroup group = createOrchidGroup(951);

		var report = reconciliationService.reconcile();

		assertThat(report.stage()).isEqualTo(OrchidGroupLedgerReconciliationStage.PRE_BASELINE);
		assertThat(report.ready()).isTrue();
		assertThat(report.orchidGroupCount()).isEqualTo(1);
		assertThat(report.revisionedGroupCount()).isZero();
		assertThat(report.mutationCount()).isZero();
		assertThat(report.entryCount()).isZero();
		assertThat(report.currentStateFingerprint()).hasSize(64);
		assertThat(orchidGroupRepository.findById(group.getId()).orElseThrow().getStateRevision()).isNull();
		assertThat(coverageRepository.count()).isZero();
	}

	@Test
	void validatesImportedStateChainAndStoredActiveCoverageFingerprint() {
		OrchidGroup group = createOrchidGroup(952);
		UUID cutoverKey = UUID.randomUUID();
		OrchidGroupStateChainTestSupport.importCurrentGroups(
				stateChainMigrationService, orchidGroupRepository, cutoverKey, BUSINESS_DATE, "rehearsal-test");

		var preparingReport = reconciliationService.reconcile();

		assertThat(preparingReport.stage())
				.isEqualTo(OrchidGroupLedgerReconciliationStage.BASELINE_PREPARING);
		assertThat(preparingReport.ready()).isTrue();
		assertThat(preparingReport.baselineGroupCount()).isEqualTo(1);
		assertThat(preparingReport.baselineFingerprint()).hasSize(64);
		var coverage = coverageRepository.findByCutoverKey(cutoverKey).orElseThrow();
		coverage.activate(Instant.parse("2026-08-20T00:00:00Z"), 1, preparingReport.baselineFingerprint());
		entityManager.flush();
		entityManager.clear();

		var activeReport = reconciliationService.reconcile();

		assertThat(activeReport.stage()).isEqualTo(OrchidGroupLedgerReconciliationStage.ACTIVE);
		assertThat(activeReport.coverageStatus()).isEqualTo(OrchidGroupLedgerCoverageStatus.ACTIVE);
		assertThat(activeReport.issues()).isEmpty();
		assertThat(activeReport.ready()).isTrue();
		assertThat(activeReport.baselineFingerprint()).isEqualTo(preparingReport.baselineFingerprint());
	}

	@Test
	void detectsAStateChangeThatBypassedTheMutationLedger() {
		OrchidGroup group = createOrchidGroup(953);
		UUID cutoverKey = UUID.randomUUID();
		OrchidGroupStateChainTestSupport.importCurrentGroups(
				stateChainMigrationService, orchidGroupRepository, cutoverKey, BUSINESS_DATE, "rehearsal-test");
		group.reserve(1);
		entityManager.flush();
		entityManager.clear();

		var report = reconciliationService.reconcile();

		assertThat(report.ready()).isFalse();
		assertThat(report.issues()).extracting("code")
				.contains("CURRENT_SNAPSHOT_MISMATCH", "SALES_RESERVATION_MISMATCH");
	}

	@Test
	void rejectsACompetingPreparingCoverage() {
		preparationService.prepare(UUID.randomUUID(), BUSINESS_DATE, "rehearsal-test");

		assertThatThrownBy(() -> preparationService.prepare(
				UUID.randomUUID(), BUSINESS_DATE, "rehearsal-test"))
				.isInstanceOf(ConflictException.class)
				.hasMessageContaining("PREPARING");
	}

	@Test
	void replaysTheCompleteStateChainAndActivatesOnlyAfterReconciliation() {
		createOrchidGroup(954);
		createOrchidGroup(955);
		UUID cutoverKey = UUID.randomUUID();
		var first = OrchidGroupStateChainTestSupport.importCurrentGroups(
				stateChainMigrationService, orchidGroupRepository, cutoverKey, BUSINESS_DATE, "1.0.0");
		var replayed = OrchidGroupStateChainTestSupport.importCurrentGroups(
				stateChainMigrationService, orchidGroupRepository, cutoverKey, BUSINESS_DATE, "1.0.0");

		assertThat(first.importedMutationCount()).isEqualTo(1);
		assertThat(first.reconciliation().baselineGroupCount()).isEqualTo(2);
		assertThat(replayed.replayedMutationCount()).isEqualTo(1);
		assertThat(replayed.reconciliation().ready()).isTrue();
		assertThat(replayed.reconciliation().mutationCount()).isEqualTo(1);
		assertThat(replayed.reconciliation().entryCount()).isEqualTo(2);

		var activated = cutoverService.execute(new OrchidGroupLedgerCutoverCommand(
				cutoverKey, BUSINESS_DATE, "1.0.0", "1.1.0", true));

		assertThat(activated.activated()).isTrue();
		assertThat(activated.reconciliation().stage())
				.isEqualTo(OrchidGroupLedgerReconciliationStage.ACTIVE);
		var coverage = coverageRepository.findByCutoverKey(cutoverKey).orElseThrow();
		assertThat(coverage.getBaselineGroupCount()).isEqualTo(2);
		assertThat(coverage.getBaselineFingerprint()).hasSize(64);
	}

	@Test
	void activatesWithoutRebaseliningAnEngineCreatedPreparingGroup() {
		OrchidGroup baselineGroup = createOrchidGroup(956);
		UUID cutoverKey = UUID.randomUUID();
		OrchidGroupStateChainTestSupport.importCurrentGroups(
				stateChainMigrationService, orchidGroupRepository, cutoverKey, BUSINESS_DATE, "1.0.0");

		var created = mutationEngine.create(new CreateOrchidGroupMutationCommand(
				new OrchidGroupMutationSource(
						OrchidGroupMutationSourceDomain.FARM,
						"ORCHID_GROUP_COMMAND",
						"post-baseline-create",
						"CREATE",
						UUID.randomUUID()),
				baselineGroup.getBedZone().getId(),
				new OrchidGroupMutationDetails(
						baselineGroup.getVariety().getId(),
						10,
						"3.5치",
						2,
						"정상",
						null,
						null,
						false,
						new BigDecimal("2"),
						new BigDecimal("3"),
						null),
				BUSINESS_DATE,
				"PREPARING 이후 생성"));

		var activated = cutoverService.execute(new OrchidGroupLedgerCutoverCommand(
				cutoverKey, BUSINESS_DATE, "1.0.0", "1.1.0", true));

		assertThat(created.entries()).singleElement().satisfies(entry ->
				assertThat(entry.stateRevisionAfter()).isEqualTo(1L));
		assertThat(activated.activated()).isTrue();
		assertThat(activated.baselineGroupCount()).isEqualTo(1);
		assertThat(activated.reconciliation().orchidGroupCount()).isEqualTo(2);
		assertThat(activated.reconciliation().ready()).isTrue();
	}

	private OrchidGroup createOrchidGroup(int houseNumber) {
		House house = new House(houseNumber, "Ledger 대사 테스트동");
		PhysicalBed bed = new PhysicalBed(1, 1);
		bed.updatePositionUnits(new BigDecimal("20"), "칸");
		BedZone zone = new BedZone("Ledger 대사 구역", BedZoneSide.LEFT, 1);
		bed.addBedZone(zone);
		house.addPhysicalBed(bed);
		houseRepository.save(house);
		Variety variety = varietyRepository.save(new Variety(
				"LEDGER-RECONCILIATION-" + houseNumber,
				"Phalaenopsis",
				"Ledger Reconciliation " + houseNumber,
				null,
				"3.5치",
				true,
				true,
				null,
				null));
		OrchidGroup group = new OrchidGroup(
				zone,
				variety.getGenus(),
				variety.getName(),
				20,
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
