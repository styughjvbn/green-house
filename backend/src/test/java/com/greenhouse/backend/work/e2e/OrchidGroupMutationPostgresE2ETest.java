package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.farm.application.orchid.mutation.CreateOrchidGroupMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.BaselineOrchidGroupsCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerPreparationService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationDetails;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationEngine;
import com.greenhouse.backend.farm.application.orchid.mutation.TransformOrchidGroupMutationResult;
import com.greenhouse.backend.farm.application.orchid.mutation.TransformOrchidGroupMutationSource;
import com.greenhouse.backend.farm.application.orchid.mutation.TransformOrchidGroupsMutationCommand;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSource;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSourceDomain;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("work-e2e")
class OrchidGroupMutationPostgresE2ETest extends WorkE2ETestBase {

	@Autowired private WorkTestDataSeeder seeder;
	@Autowired private OrchidGroupMutationEngine mutationEngine;
	@Autowired private OrchidGroupLedgerPreparationService ledgerPreparationService;
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
		ledgerPreparationService.prepare(cutoverKey, businessDate, "mutation-engine-e2e");
		ledgerPreparationService.start(cutoverKey);
		ledgerPreparationService.baselineBatch(new BaselineOrchidGroupsCommand(
				cutoverKey,
				"GROUPS-0001",
				List.of(scenario.orchidGroupId()),
				businessDate));
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

	private record Outcome(Long mutationId, RuntimeException failure) {
	}
}
