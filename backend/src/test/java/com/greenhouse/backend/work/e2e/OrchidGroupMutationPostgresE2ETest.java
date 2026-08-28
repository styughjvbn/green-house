package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.greenhouse.backend.OrchidGroupStateChainTestSupport;
import com.greenhouse.backend.farm.application.orchid.mutation.CreateOrchidGroupMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.CreateInboundOrchidGroupsMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.CreateOrchidGroupMutationItem;
import com.greenhouse.backend.farm.application.orchid.mutation.CorrectOrchidGroupMutationItem;
import com.greenhouse.backend.farm.application.orchid.mutation.CorrectOrchidGroupsMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.ConsumeOrchidGroupReservationsMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.DiscardOrchidGroupMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerCutoverCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerCutoverService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerReconciliationService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationDetails;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationEngine;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupStateChainMigrationService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupQuantityMutationItem;
import com.greenhouse.backend.farm.application.orchid.mutation.RelatedOrchidGroupMutations;
import com.greenhouse.backend.farm.application.orchid.mutation.ReserveOrchidGroupsMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.RestoreOutboundOrchidGroupsMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.TransformOrchidGroupMutationResult;
import com.greenhouse.backend.farm.application.orchid.mutation.TransformOrchidGroupMutationSource;
import com.greenhouse.backend.farm.application.orchid.mutation.TransformOrchidGroupsMutationCommand;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSource;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSourceDomain;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("work-e2e")
class OrchidGroupMutationPostgresE2ETest extends WorkE2ETestBase {

	@Autowired private WorkTestDataSeeder seeder;
	@Autowired private OrchidGroupMutationEngine mutationEngine;
	@Autowired private OrchidGroupLedgerCutoverService ledgerCutoverService;
	@Autowired private OrchidGroupLedgerReconciliationService ledgerReconciliationService;
	@Autowired private OrchidGroupStateChainMigrationService stateChainMigrationService;
	@Autowired private OrchidGroupRepository orchidGroupRepository;
	@Autowired private PlatformTransactionManager transactionManager;
	@Autowired private JdbcTemplate jdbcTemplate;

	private WorkTestDataSeeder.ContractScenario scenario;
	private Long varietyId;

	@BeforeEach
	void setUp() {
		seeder.reset();
		scenario = seeder.seedContractScenario();
		varietyId = jdbcTemplate.queryForObject(
				"SELECT variety_id FROM orchid_groups WHERE id = ?",
				Long.class,
				scenario.orchidGroupId());
	}

	@Test
	void serializesConcurrentCreationIntoTheSamePlacement() throws Exception {
		var ready = new CountDownLatch(2);
		var start = new CountDownLatch(1);
		var executor = Executors.newFixedThreadPool(2);
		try {
			var futures = List.of("create-a", "create-b").stream()
					.map(referenceId -> executor.submit(() -> createConcurrently(referenceId, ready, start)))
					.toList();
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			var outcomes = futures.stream().map(future -> {
				try {
					return future.get(10, TimeUnit.SECONDS);
				} catch (Exception exception) {
					throw new AssertionError(exception);
				}
			}).toList();

			assertThat(outcomes).filteredOn(outcome -> outcome.mutationId() != null).hasSize(1);
			assertThat(outcomes).filteredOn(outcome -> outcome.failure() != null)
					.singleElement()
					.satisfies(outcome -> assertThat(outcome.failure())
							.isInstanceOf(IllegalArgumentException.class)
							.hasMessageContaining("겹칩니다"));
		} finally {
			start.countDown();
			executor.shutdownNow();
		}

		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM orchid_groups WHERE start_position = 6 AND end_position = 8",
				Long.class)).isEqualTo(1L);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM orchid_group_mutations",
				Long.class)).isEqualTo(1L);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM orchid_group_mutation_entries",
				Long.class)).isEqualTo(1L);
	}

	@Test
	void serializesConcurrentTransformsSharingTheSameSource() throws Exception {
		UUID cutoverKey = UUID.randomUUID();
		LocalDate businessDate = LocalDate.of(2026, 8, 20);
		OrchidGroupStateChainTestSupport.importCurrentGroups(
				stateChainMigrationService, orchidGroupRepository, cutoverKey, businessDate, "mutation-engine-e2e");
		var ready = new CountDownLatch(2);
		var start = new CountDownLatch(1);
		var executor = Executors.newFixedThreadPool(2);
		try {
			var futures = List.of(
					executor.submit(() -> transformConcurrently(
							"transform-a", "6", "8", ready, start)),
					executor.submit(() -> transformConcurrently(
							"transform-b", "8", "10", ready, start)));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			var outcomes = futures.stream().map(future -> {
				try {
					return future.get(10, TimeUnit.SECONDS);
				} catch (Exception exception) {
					throw new AssertionError(exception);
				}
			}).toList();

			assertThat(outcomes).filteredOn(outcome -> outcome.mutationId() != null).hasSize(1);
			assertThat(outcomes).filteredOn(outcome -> outcome.failure() != null)
					.singleElement()
					.satisfies(outcome -> assertThat(outcome.failure())
							.isInstanceOf(IllegalArgumentException.class)
							.hasMessageContaining("가용 수량"));
		} finally {
			start.countDown();
			executor.shutdownNow();
		}

		assertThat(jdbcTemplate.queryForObject(
				"SELECT quantity FROM orchid_groups WHERE id = ?",
				Integer.class,
				scenario.orchidGroupId())).isEqualTo(40);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM orchid_groups",
				Long.class)).isEqualTo(2L);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM orchid_group_mutations",
				Long.class)).isEqualTo(2L);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM orchid_group_mutation_entries",
				Long.class)).isEqualTo(3L);
	}

	@Test
	void serializesConcurrentSalesReservationAndDiscardForTheSameGroup() throws Exception {
		UUID cutoverKey = UUID.randomUUID();
		LocalDate businessDate = LocalDate.of(2026, 8, 20);
		OrchidGroupStateChainTestSupport.importCurrentGroups(
				stateChainMigrationService, orchidGroupRepository, cutoverKey, businessDate, "mutation-engine-e2e");
		var ready = new CountDownLatch(2);
		var start = new CountDownLatch(1);
		var executor = Executors.newFixedThreadPool(2);
		try {
			var futures = List.of(
					executor.submit(() -> reserveConcurrently("sales-slip-a", ready, start)),
					executor.submit(() -> discardConcurrently(ready, start)));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			var outcomes = futures.stream().map(future -> {
				try {
					return future.get(10, TimeUnit.SECONDS);
				} catch (Exception exception) {
					throw new AssertionError(exception);
				}
			}).toList();

			assertThat(outcomes).filteredOn(outcome -> outcome.mutationId() != null).hasSize(1);
			assertThat(outcomes).filteredOn(outcome -> outcome.failure() != null)
					.singleElement()
					.satisfies(outcome -> assertThat(outcome.failure())
							.isInstanceOf(IllegalArgumentException.class)
							.hasMessageContaining("가용 수량"));
		} finally {
			start.countDown();
			executor.shutdownNow();
		}

		Integer quantity = jdbcTemplate.queryForObject(
				"SELECT quantity FROM orchid_groups WHERE id = ?",
				Integer.class,
				scenario.orchidGroupId());
		Integer reservedQuantity = jdbcTemplate.queryForObject(
				"SELECT reserved_quantity FROM orchid_groups WHERE id = ?",
				Integer.class,
				scenario.orchidGroupId());
		assertThat(List.of(quantity, reservedQuantity))
				.isIn(List.of(100, 60), List.of(40, 0));
		assertThat(quantity - reservedQuantity).isEqualTo(40);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT state_revision FROM orchid_groups WHERE id = ?",
				Long.class,
				scenario.orchidGroupId())).isEqualTo(1L);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM orchid_group_mutations",
				Long.class)).isEqualTo(2L);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM orchid_group_mutation_entries",
				Long.class)).isEqualTo(2L);
	}

	@Test
	void serializesConcurrentCorrectionAndSalesReservationForTheSameGroup() throws Exception {
		UUID cutoverKey = UUID.randomUUID();
		LocalDate businessDate = LocalDate.of(2026, 8, 20);
		OrchidGroupStateChainTestSupport.importCurrentGroups(
				stateChainMigrationService, orchidGroupRepository, cutoverKey, businessDate, "mutation-engine-e2e");
		var ready = new CountDownLatch(2);
		var start = new CountDownLatch(1);
		var executor = Executors.newFixedThreadPool(2);
		try {
			var futures = List.of(
					executor.submit(() -> reserveConcurrently("sales-slip-correction", ready, start)),
					executor.submit(() -> correctConcurrently(ready, start)));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			var outcomes = futures.stream().map(future -> {
				try {
					return future.get(10, TimeUnit.SECONDS);
				} catch (Exception exception) {
					throw new AssertionError(exception);
				}
			}).toList();

			assertThat(outcomes).filteredOn(outcome -> outcome.mutationId() != null).hasSize(1);
			assertThat(outcomes).filteredOn(outcome -> outcome.failure() != null)
					.singleElement()
					.satisfies(outcome -> {
						assertThat(outcome.failure()).isInstanceOf(IllegalArgumentException.class);
						assertThat(outcome.failure().getMessage())
								.containsAnyOf("가용 수량", "예약 수량");
					});
		} finally {
			start.countDown();
			executor.shutdownNow();
		}

		Integer quantity = jdbcTemplate.queryForObject(
				"SELECT quantity FROM orchid_groups WHERE id = ?",
				Integer.class,
				scenario.orchidGroupId());
		Integer reservedQuantity = jdbcTemplate.queryForObject(
				"SELECT reserved_quantity FROM orchid_groups WHERE id = ?",
				Integer.class,
				scenario.orchidGroupId());
		String status = jdbcTemplate.queryForObject(
				"SELECT status FROM orchid_groups WHERE id = ?",
				String.class,
				scenario.orchidGroupId());
		assertThat(quantity == 50 && reservedQuantity == 0 && "수량 보정".equals(status)
				|| quantity == 100 && reservedQuantity == 60 && "정상".equals(status))
				.isTrue();
		assertThat(jdbcTemplate.queryForObject(
				"SELECT state_revision FROM orchid_groups WHERE id = ?",
				Long.class,
				scenario.orchidGroupId())).isEqualTo(1L);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM orchid_group_mutations",
				Long.class)).isEqualTo(2L);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM orchid_group_mutation_entries",
				Long.class)).isEqualTo(2L);
	}

	@Test
	void recordsACompensationRelationForCurrentSalesOutboundOnPostgres() {
		UUID cutoverKey = UUID.randomUUID();
		LocalDate businessDate = LocalDate.of(2026, 8, 20);
		OrchidGroupStateChainTestSupport.importCurrentGroups(
				stateChainMigrationService, orchidGroupRepository, cutoverKey, businessDate, "mutation-engine-e2e");
		UUID correlationId = UUID.randomUUID();
		MutationIds mutationIds = new TransactionTemplate(transactionManager).execute(status -> {
			mutationEngine.reserve(new ReserveOrchidGroupsMutationCommand(
					salesSource("RESERVE", correlationId),
					List.of(new OrchidGroupQuantityMutationItem(scenario.orchidGroupId(), 10)),
					businessDate,
					"판매 예약"));
			var consumed = mutationEngine.consumeReservation(
					new ConsumeOrchidGroupReservationsMutationCommand(
							salesSource("OUTBOUND", correlationId),
							List.of(new OrchidGroupQuantityMutationItem(scenario.orchidGroupId(), 10)),
							businessDate,
							"판매 출고"));
			var restored = mutationEngine.restoreOutbound(
					new RestoreOutboundOrchidGroupsMutationCommand(
							salesSource("RESTORE_OUTBOUND", correlationId),
							List.of(new OrchidGroupQuantityMutationItem(scenario.orchidGroupId(), 10)),
							RelatedOrchidGroupMutations.current(List.of(consumed.mutationId())),
							businessDate,
							"판매 출고 취소"));
			return new MutationIds(consumed.mutationId(), restored.mutationId());
		});

		assertThat(mutationIds).isNotNull();
		assertThat(jdbcTemplate.queryForObject(
				"SELECT quantity FROM orchid_groups WHERE id = ?",
				Integer.class,
				scenario.orchidGroupId())).isEqualTo(100);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT reserved_quantity FROM orchid_groups WHERE id = ?",
				Integer.class,
				scenario.orchidGroupId())).isZero();
		assertThat(jdbcTemplate.queryForObject(
				"SELECT state_revision FROM orchid_groups WHERE id = ?",
				Long.class,
				scenario.orchidGroupId())).isEqualTo(3L);
		assertThat(jdbcTemplate.queryForObject(
				"""
				SELECT COUNT(*)
				FROM orchid_group_mutation_relations
				WHERE mutation_id = ? AND related_mutation_id = ? AND relation_type = 'COMPENSATES'
				""",
				Long.class,
				mutationIds.restoredMutationId(),
				mutationIds.consumedMutationId())).isEqualTo(1L);
	}

	@Test
	void allowsOnlyOneCreationMutationForTheSameInboundRecord() throws Exception {
		Long inboundRecordId = jdbcTemplate.queryForObject("""
				INSERT INTO inbound_records (
				  created_at, updated_at, inbound_date, inbound_type, status,
				  bottle_count, estimated_quantity, temp_location, pot_size, variety_id
				) VALUES (
				  TIMESTAMP '2026-08-20 00:00:00', TIMESTAMP '2026-08-20 00:00:00',
				  DATE '2026-08-20', 'FLASK_SEEDLING', 'POTTING_PENDING',
				  10, 100, '배양실', '2"', ?
				)
				RETURNING id
				""", Long.class, varietyId);
		var ready = new CountDownLatch(2);
		var start = new CountDownLatch(1);
		var executor = Executors.newFixedThreadPool(2);
		try {
			var futures = List.of("inbound-a", "inbound-b").stream()
					.map(referenceId -> executor.submit(() -> createInboundConcurrently(
							referenceId, inboundRecordId, ready, start)))
					.toList();
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			var outcomes = futures.stream().map(future -> {
				try {
					return future.get(10, TimeUnit.SECONDS);
				} catch (Exception exception) {
					throw new AssertionError(exception);
				}
			}).toList();

			assertThat(outcomes).filteredOn(outcome -> outcome.mutationId() != null).hasSize(1);
			assertThat(outcomes).filteredOn(outcome -> outcome.failure() != null)
					.singleElement()
					.satisfies(outcome -> assertThat(outcome.failure())
							.isInstanceOf(IllegalStateException.class)
							.hasMessageContaining("이미 난 묶음이 생성"));
		} finally {
			start.countDown();
			executor.shutdownNow();
		}

		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM orchid_groups",
				Long.class)).isEqualTo(2L);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM orchid_groups WHERE inbound_record_id = ?",
				Long.class,
				inboundRecordId)).isEqualTo(1L);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM orchid_group_mutations",
				Long.class)).isEqualTo(1L);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM orchid_group_mutation_entries",
				Long.class)).isEqualTo(1L);
	}

	@Test
	void enforcesTheActiveLedgerWriteFenceAndRollsBackMutationContext() {
		UUID cutoverKey = UUID.randomUUID();
		LocalDate businessDate = LocalDate.of(2026, 8, 20);
		OrchidGroupStateChainTestSupport.importCurrentGroups(
				stateChainMigrationService,
				orchidGroupRepository,
				cutoverKey,
				businessDate,
				"1.0.0");
		var cutover = ledgerCutoverService.execute(new OrchidGroupLedgerCutoverCommand(
				cutoverKey, businessDate, "1.0.0", "1.0.0", true));

		assertThat(cutover.activated()).isTrue();
		assertThat(cutover.reconciliation().ready()).isTrue();
		assertThatThrownBy(() -> jdbcTemplate.update(
				"UPDATE orchid_groups SET state_revision = state_revision + 1 WHERE id = ?",
				scenario.orchidGroupId()))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("Mutation context");
		assertThatThrownBy(() -> jdbcTemplate.update("""
				INSERT INTO orchid_groups (
				  created_at, updated_at, age_year, genus, placement_type, pot_size, pot_size_code,
				  quantity, sort_order, status, variety_name, bed_zone_id, split_placement_allowed,
				  variety_id, start_position, end_position, reserved_quantity, state_revision
				)
				SELECT CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, age_year, genus, placement_type,
				       pot_size, pot_size_code, quantity, sort_order + 100, status, variety_name,
				       bed_zone_id, split_placement_allowed, variety_id, 11, 12, 0, 1
				FROM orchid_groups WHERE id = ?
				""", scenario.orchidGroupId()))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("Mutation context");
		assertThatThrownBy(() -> jdbcTemplate.update(
				"DELETE FROM orchid_groups WHERE id = ?", scenario.orchidGroupId()))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("물리 삭제");

		var created = new TransactionTemplate(transactionManager).execute(status ->
				mutationEngine.create(new CreateOrchidGroupMutationCommand(
						new OrchidGroupMutationSource(
								OrchidGroupMutationSourceDomain.FARM,
								"ORCHID_GROUP_COMMAND",
								"active-fence-create",
								"CREATE",
								UUID.randomUUID()),
						scenario.bedZoneId(),
						new OrchidGroupMutationDetails(
								varietyId,
								10,
								"4치",
								2,
								"정상",
								"POT",
								null,
								false,
								new BigDecimal("6"),
								new BigDecimal("8"),
								null),
						businessDate,
						"ACTIVE 생성 검증")));
		assertThat(created).isNotNull();
		Long createdGroupId = created.entries().getFirst().orchidGroupId();
		new TransactionTemplate(transactionManager).executeWithoutResult(status ->
				mutationEngine.discard(new DiscardOrchidGroupMutationCommand(
						new OrchidGroupMutationSource(
								OrchidGroupMutationSourceDomain.FARM,
								"ORCHID_GROUP_COMMAND",
								"active-fence-discard",
								"DISCARD",
								UUID.randomUUID()),
						scenario.orchidGroupId(),
						10,
						businessDate,
						"ACTIVE 수정 검증")));

		new TransactionTemplate(transactionManager).executeWithoutResult(status ->
				mutationEngine.correct(new CorrectOrchidGroupsMutationCommand(
						new OrchidGroupMutationSource(
								OrchidGroupMutationSourceDomain.WORK,
								"WORK_EFFECT",
								"active-fence-correction",
								"CORRECTION",
								UUID.randomUUID()),
						List.of(new CorrectOrchidGroupMutationItem(createdGroupId, 11, "수량 보정")),
						RelatedOrchidGroupMutations.current(List.of(created.mutationId())),
						businessDate,
						"ACTIVE 보정 flush 순서 검증")));
		new TransactionTemplate(transactionManager).executeWithoutResult(status ->
				mutationEngine.transform(new TransformOrchidGroupsMutationCommand(
						new OrchidGroupMutationSource(
								OrchidGroupMutationSourceDomain.WORK,
								"WORK_EFFECT",
								"active-fence-transform",
								"EXECUTION:round-1",
								UUID.randomUUID()),
						List.of(new TransformOrchidGroupMutationSource(
								scenario.orchidGroupId(), 10, null, null)),
						List.of(new TransformOrchidGroupMutationResult(
								scenario.bedZoneId(),
								new OrchidGroupMutationDetails(
										varietyId,
										10,
										"4치",
										2,
										"정상",
										"POT",
										null,
										false,
										new BigDecimal("9"),
										new BigDecimal("10"),
										null))),
						businessDate,
						"ACTIVE transform flush 순서 검증")));

		assertThat(jdbcTemplate.queryForObject(
				"SELECT state_revision FROM orchid_groups WHERE id = ?",
				Long.class,
				createdGroupId)).isEqualTo(2L);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT quantity FROM orchid_groups WHERE id = ?",
				Integer.class,
				scenario.orchidGroupId())).isEqualTo(80);

		assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			mutationEngine.discard(new DiscardOrchidGroupMutationCommand(
					new OrchidGroupMutationSource(
							OrchidGroupMutationSourceDomain.FARM,
							"ORCHID_GROUP_COMMAND",
							"active-fence-rollback",
							"DISCARD",
							UUID.randomUUID()),
					scenario.orchidGroupId(),
					5,
					businessDate,
					"ACTIVE rollback 검증"));
			throw new RollbackProbeException();
		})).isInstanceOf(RollbackProbeException.class);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT quantity FROM orchid_groups WHERE id = ?",
				Integer.class,
				scenario.orchidGroupId())).isEqualTo(80);
		assertThatThrownBy(() -> jdbcTemplate.update(
				"UPDATE orchid_groups SET state_revision = state_revision + 1 WHERE id = ?",
				scenario.orchidGroupId()))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("Mutation context");

		assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			jdbcTemplate.queryForObject(
					"SELECT set_config('greenhouse.orchid_group_mutation', 'MUTATION:999999', TRUE)",
					String.class);
			jdbcTemplate.update(
					"UPDATE orchid_groups SET state_revision = state_revision + 1 WHERE id = ?",
					scenario.orchidGroupId());
		}))
				.rootCause()
				.hasMessageContaining("MutationEntry");

		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM orchid_group_mutations", Long.class)).isEqualTo(5L);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM orchid_group_mutation_entries", Long.class)).isEqualTo(6L);
		assertThat(ledgerReconciliationService.reconcile().ready()).isTrue();
	}

	private Outcome createConcurrently(
			String referenceId,
			CountDownLatch ready,
			CountDownLatch start) throws Exception {
		ready.countDown();
		if (!start.await(5, TimeUnit.SECONDS)) {
			throw new IllegalStateException("동시 생성 요청 시작 신호를 기다리지 못했습니다.");
		}
		try {
			Long mutationId = new TransactionTemplate(transactionManager).execute(status -> mutationEngine.create(
					new CreateOrchidGroupMutationCommand(
							new OrchidGroupMutationSource(
									OrchidGroupMutationSourceDomain.FARM,
									"ORCHID_GROUP_COMMAND",
									referenceId,
									"CREATE",
									UUID.randomUUID()),
							scenario.bedZoneId(),
							new OrchidGroupMutationDetails(
									varietyId,
									10,
									"4치",
									2,
									"정상",
									"POT",
									null,
									false,
									new BigDecimal("6"),
									new BigDecimal("8"),
									null),
							LocalDate.of(2026, 8, 20),
							"동시 생성 검증"))
					.mutationId());
			return new Outcome(mutationId, null);
		} catch (RuntimeException exception) {
			return new Outcome(null, exception);
		}
	}

	private Outcome transformConcurrently(
			String referenceId,
			String startPosition,
			String endPosition,
			CountDownLatch ready,
			CountDownLatch start) throws Exception {
		ready.countDown();
		if (!start.await(5, TimeUnit.SECONDS)) {
			throw new IllegalStateException("동시 구조 변경 요청 시작 신호를 기다리지 못했습니다.");
		}
		try {
			Long mutationId = new TransactionTemplate(transactionManager).execute(status -> mutationEngine.transform(
					new TransformOrchidGroupsMutationCommand(
							new OrchidGroupMutationSource(
									OrchidGroupMutationSourceDomain.WORK,
									"WORK_EFFECT",
									referenceId,
									"EXECUTION:round-1",
									UUID.randomUUID()),
							List.of(new TransformOrchidGroupMutationSource(
									scenario.orchidGroupId(), 60, null, null)),
							List.of(new TransformOrchidGroupMutationResult(
									scenario.bedZoneId(),
									new OrchidGroupMutationDetails(
											varietyId,
											60,
											"4치",
											2,
											"정상",
											"POT",
											null,
											false,
											new BigDecimal(startPosition),
											new BigDecimal(endPosition),
											null))),
							LocalDate.of(2026, 8, 20),
							"동시 구조 변경 검증"))
					.mutationId());
			return new Outcome(mutationId, null);
		} catch (RuntimeException exception) {
			return new Outcome(null, exception);
		}
	}

	private Outcome reserveConcurrently(
			String referenceId,
			CountDownLatch ready,
			CountDownLatch start) throws Exception {
		ready.countDown();
		if (!start.await(5, TimeUnit.SECONDS)) {
			throw new IllegalStateException("동시 판매 예약 요청 시작 신호를 기다리지 못했습니다.");
		}
		try {
			Long mutationId = new TransactionTemplate(transactionManager).execute(status -> mutationEngine.reserve(
					new ReserveOrchidGroupsMutationCommand(
							new OrchidGroupMutationSource(
									OrchidGroupMutationSourceDomain.SALES,
									"SALES_SLIP",
									referenceId,
									"RESERVE",
									UUID.randomUUID()),
							List.of(new OrchidGroupQuantityMutationItem(
									scenario.orchidGroupId(), 60)),
							LocalDate.of(2026, 8, 20),
							"동시 판매 예약 검증"))
					.mutationId());
			return new Outcome(mutationId, null);
		} catch (RuntimeException exception) {
			return new Outcome(null, exception);
		}
	}

	private Outcome discardConcurrently(
			CountDownLatch ready,
			CountDownLatch start) throws Exception {
		ready.countDown();
		if (!start.await(5, TimeUnit.SECONDS)) {
			throw new IllegalStateException("동시 폐기 요청 시작 신호를 기다리지 못했습니다.");
		}
		try {
			Long mutationId = new TransactionTemplate(transactionManager).execute(status -> mutationEngine.discard(
					new DiscardOrchidGroupMutationCommand(
							new OrchidGroupMutationSource(
									OrchidGroupMutationSourceDomain.FARM,
									"ORCHID_GROUP_COMMAND",
									"concurrent-discard",
									"DISCARD",
									UUID.randomUUID()),
							scenario.orchidGroupId(),
							60,
							LocalDate.of(2026, 8, 20),
							"판매 예약과 동시 폐기 검증"))
					.mutationId());
			return new Outcome(mutationId, null);
		} catch (RuntimeException exception) {
			return new Outcome(null, exception);
		}
	}

	private Outcome correctConcurrently(
			CountDownLatch ready,
			CountDownLatch start) throws Exception {
		ready.countDown();
		if (!start.await(5, TimeUnit.SECONDS)) {
			throw new IllegalStateException("동시 보정 요청 시작 신호를 기다리지 못했습니다.");
		}
		try {
			Long mutationId = new TransactionTemplate(transactionManager).execute(status -> mutationEngine.correct(
					new CorrectOrchidGroupsMutationCommand(
							new OrchidGroupMutationSource(
									OrchidGroupMutationSourceDomain.WORK,
									"WORK_EFFECT",
									"concurrent-correction",
									"OPERATION",
									UUID.randomUUID()),
							List.of(new CorrectOrchidGroupMutationItem(
									scenario.orchidGroupId(), 50, "수량 보정")),
							RelatedOrchidGroupMutations.legacy(),
							LocalDate.of(2026, 8, 20),
							"판매 예약과 동시 보정 검증"))
					.mutationId());
			return new Outcome(mutationId, null);
		} catch (RuntimeException exception) {
			return new Outcome(null, exception);
		}
	}

	private OrchidGroupMutationSource salesSource(String operationKey, UUID correlationId) {
		return new OrchidGroupMutationSource(
				OrchidGroupMutationSourceDomain.SALES,
				"SALES_SLIP",
				"sales-slip-relation",
				operationKey,
				correlationId);
	}

	private Outcome createInboundConcurrently(
			String referenceId,
			Long inboundRecordId,
			CountDownLatch ready,
			CountDownLatch start) throws Exception {
		ready.countDown();
		if (!start.await(5, TimeUnit.SECONDS)) {
			throw new IllegalStateException("동시 입고 생성 요청 시작 신호를 기다리지 못했습니다.");
		}
		try {
			Long mutationId = new TransactionTemplate(transactionManager).execute(status ->
					mutationEngine.createFromInbound(new CreateInboundOrchidGroupsMutationCommand(
							new OrchidGroupMutationSource(
									OrchidGroupMutationSourceDomain.INBOUND,
									"INBOUND_RECORD",
									referenceId,
									"CREATE_GROUPS",
									UUID.randomUUID()),
							inboundRecordId,
							List.of(new CreateOrchidGroupMutationItem(
									scenario.bedZoneId(),
									new OrchidGroupMutationDetails(
											varietyId,
											100,
											"2치",
											1,
											"정상",
											"TRAY",
											1,
											false,
											null,
											null,
											null))),
							LocalDate.of(2026, 8, 20),
							"동시 입고 생성 검증"))
						.mutationId());
			return new Outcome(mutationId, null);
		} catch (RuntimeException exception) {
			return new Outcome(null, exception);
		}
	}

	private record Outcome(Long mutationId, RuntimeException failure) {
	}

	private record MutationIds(Long consumedMutationId, Long restoredMutationId) {
	}

	private static final class RollbackProbeException extends RuntimeException {
	}
}
