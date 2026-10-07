package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.application.orchid.OrchidGroupReader;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerReconciliationService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationDetails;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationEngine;
import com.greenhouse.backend.farm.application.orchid.mutation.ReleaseOrchidGroupReservationsMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.TransformOrchidGroupMutationResult;
import com.greenhouse.backend.farm.application.orchid.mutation.TransformOrchidGroupMutationSource;
import com.greenhouse.backend.farm.application.orchid.mutation.TransformOrchidGroupsMutationCommand;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSource;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSourceDomain;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.sales.application.document.SalesQueryService;
import com.greenhouse.backend.sales.application.document.SalesSlipCreationService;
import com.greenhouse.backend.sales.application.document.SalesSlipDocument;
import com.greenhouse.backend.sales.application.document.SalesSlipStatusService;
import com.greenhouse.backend.sales.application.document.SalesSlipUpdateService;
import com.greenhouse.backend.sales.application.document.command.SalesSlipAllocationInput;
import com.greenhouse.backend.sales.application.document.command.SalesSlipCommand;
import com.greenhouse.backend.sales.application.document.command.SalesSlipItemInput;
import com.greenhouse.backend.sales.domain.document.SalesSlip;
import com.greenhouse.backend.sales.domain.document.SalesType;
import com.greenhouse.backend.sales.domain.partner.BusinessPartner;
import com.greenhouse.backend.sales.domain.partner.PartnerType;
import com.greenhouse.backend.sales.dto.document.SalesSlipStatusUpdateRequest;
import com.greenhouse.backend.sales.repository.partner.BusinessPartnerRepository;
import com.greenhouse.backend.support.OrchidGroupLedgerTestFixture;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("work-e2e")
@TestPropertySource(properties = "app.orchid-ledger.writer-version=1.1.0")
class SalesAllocationLockOrderPostgresE2ETest extends WorkE2ETestBase {
  private static final LocalDate DATE = LocalDate.of(2045, 1, 1);
  @Autowired private WorkTestDataSeeder seeder;
  @Autowired private OrchidGroupRepository groups;
  @Autowired private BusinessPartnerRepository partners;
  @Autowired private SalesSlipCreationService creation;
  @Autowired private SalesSlipUpdateService updates;
  @Autowired private SalesSlipStatusService statuses;
  @Autowired private SalesQueryService queries;
  @Autowired private OrchidGroupReader reader;
  @Autowired private OrchidGroupLedgerTestFixture ledgerFixture;
  @Autowired private OrchidGroupLedgerReconciliationService reconciliation;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private EntityManager entityManager;
  @Autowired private JdbcTemplate jdbc;
  @MockitoSpyBean private OrchidGroupMutationEngine engine;
  private Long lowId;
  private Long highId;
  private SalesSlipDocument lowSlip;
  private SalesSlipDocument highSlip;
  private Long lowPartner;
  private Long highPartner;

  @BeforeEach
  void seed() {
    jdbc.execute("TRUNCATE sales_slips CONTINUE IDENTITY CASCADE");
    seeder.resetKeepingSequences();
    lowId = seeder.seedContractScenario().orchidGroupId();
    highId =
        jdbc.queryForObject(
            """
        INSERT INTO orchid_groups (created_at, updated_at, age_year, genus, placement_type,
            pot_size, pot_size_code, quantity, sort_order, status, variety_name, bed_zone_id,
            split_placement_allowed, variety_id, start_position, end_position, reserved_quantity)
        SELECT created_at, updated_at, age_year, genus, placement_type,
            pot_size, pot_size_code, 100, 2, status, variety_name, bed_zone_id,
            split_placement_allowed, variety_id, 5, 10, 0 FROM orchid_groups WHERE id = ? RETURNING id
        """,
            Long.class,
            lowId);
    assertThat(highId).isGreaterThan(lowId);
    var key = UUID.randomUUID();
    ledgerFixture.seedBaseline(key, DATE, "1.0.0");
    ledgerFixture.activate(key);
    lowPartner = partner();
    highPartner = partner();
    lowSlip = creation.create(request(lowPartner, lowId, 10));
    highSlip = creation.create(request(highPartner, highId, 20));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void differentPartnersCanSwapTheirAllocationGroupsWithoutADeadlock(boolean highWins)
      throws Exception {
    var winner = highWins ? highSlip : lowSlip;
    var loser = highWins ? lowSlip : highSlip;
    var winnerRequest =
        highWins ? request(highPartner, lowId, 20) : request(lowPartner, highId, 10);
    var loserRequest = highWins ? request(lowPartner, highId, 10) : request(highPartner, lowId, 20);
    var race =
        crossAfterRelease(
            () -> updates.update(winner.id(), winnerRequest),
            () -> updates.update(loser.id(), loserRequest));
    assertThat(race.winnerFailure()).isNull();
    assertThat(race.loserFailure()).isNull();
    assertThat(race.loserReleasedBeforeWaiting())
        .as("loser waits before changing its old reservation")
        .isFalse();
    assertThat(reader.getStates(List.of(lowId)).get(lowId).reservedQuantity()).isEqualTo(20);
    assertThat(reader.getStates(List.of(highId)).get(highId).reservedQuantity()).isEqualTo(10);
    assertThat(
            queries
                .getSalesSlip(lowSlip.id())
                .items()
                .getFirst()
                .allocations()
                .getFirst()
                .orchidGroupId())
        .isEqualTo(highId);
    assertThat(
            queries
                .getSalesSlip(highSlip.id())
                .items()
                .getFirst()
                .allocations()
                .getFirst()
                .orchidGroupId())
        .isEqualTo(lowId);
    var winnerLine = queries.getSalesSlip(winner.id()).items().getFirst().allocations().getFirst();
    var loserLine = queries.getSalesSlip(loser.id()).items().getFirst().allocations().getFirst();
    assertThat(winnerLine.creationSnapshot().quantity()).isEqualTo(100);
    assertThat(winnerLine.creationSnapshot().reservedQuantity()).isEqualTo(highWins ? 10 : 20);
    assertThat(loserLine.creationSnapshot().reservedQuantity()).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM sales_inventory_movements", Integer.class))
        .isEqualTo(6);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM orchid_group_mutations WHERE mutation_type IN ('RESERVE', 'RELEASE_RESERVATION')",
                Integer.class))
        .isEqualTo(6);
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void reallocationAndOtherSlipCancellationCommitInBothLockOrders(boolean cancelWins)
      throws Exception {
    Runnable edit = () -> updates.update(highSlip.id(), request(highPartner, lowId, 20));
    Runnable cancel =
        () ->
            statuses.updateStatus(
                lowSlip.id(), new SalesSlipStatusUpdateRequest(SalesSlip.STATUS_CANCELED, null));
    var race = crossAfterRelease(cancelWins ? cancel : edit, cancelWins ? edit : cancel);
    assertThat(race.winnerFailure()).isNull();
    assertThat(race.loserFailure()).isNull();
    assertThat(race.loserReleasedBeforeWaiting()).isFalse();
    assertStock(lowId, 100, 20);
    assertStock(highId, 100, 0);
    assertThat(queries.getSalesSlip(lowSlip.id()).salesStatus())
        .isEqualTo(SalesSlip.STATUS_CANCELED);
    var snapshot =
        queries
            .getSalesSlip(highSlip.id())
            .items()
            .getFirst()
            .allocations()
            .getFirst()
            .creationSnapshot();
    assertThat(snapshot.reservedQuantity()).isEqualTo(cancelWins ? 0 : 10);
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void sameSlipUpdateAndCancellationUseTheLatestRootState(boolean cancelWins) throws Exception {
    var afterWinner = new AtomicReference<Map<String, List<String>>>();
    Runnable edit = () -> updates.update(highSlip.id(), request(highPartner, lowId, 20));
    Runnable cancel =
        () -> {
          statuses.updateStatus(
              highSlip.id(), new SalesSlipStatusUpdateRequest(SalesSlip.STATUS_CANCELED, null));
          entityManager.flush();
          afterWinner.set(snapshot());
        };
    var race = crossAfterRelease(cancelWins ? cancel : edit, cancelWins ? edit : cancel);
    assertThat(race.winnerFailure()).isNull();
    if (cancelWins) {
      assertThat(race.loserFailure()).isInstanceOf(IllegalArgumentException.class);
      assertThat(snapshot()).isEqualTo(afterWinner.get());
    } else {
      assertThat(race.loserFailure()).isNull();
    }
    assertStock(lowId, 100, 10);
    assertStock(highId, 100, 0);
    assertThat(queries.getSalesSlip(highSlip.id()).salesStatus())
        .isEqualTo(SalesSlip.STATUS_CANCELED);
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void reallocationAndEngineStructureChangeCommitWithFreshSnapshots(boolean transformWins)
      throws Exception {
    Runnable edit = () -> updates.update(highSlip.id(), request(highPartner, lowId, 20));
    var race =
        compete(
            transformWins ? this::transform : edit,
            transformWins ? edit : this::transform,
            !transformWins);
    assertThat(race.winnerFailure()).isNull();
    assertThat(race.loserFailure()).isNull();
    assertThat(race.loserReleasedBeforeWaiting()).isFalse();
    assertStock(lowId, 80, 30);
    assertStock(highId, 80, 0);
    var creationSnapshot =
        queries
            .getSalesSlip(highSlip.id())
            .items()
            .getFirst()
            .allocations()
            .getFirst()
            .creationSnapshot();
    assertThat(creationSnapshot.quantity()).isEqualTo(transformWins ? 80 : 100);
    assertThat(creationSnapshot.reservedQuantity()).isEqualTo(10);
    assertThat(groups.count()).isEqualTo(4);
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @Test
  void aWaitingEditSucceedsWhenTheFirstReallocationRollsBack() throws Exception {
    var originalRoot =
        jdbc.queryForObject(
            "SELECT to_jsonb(row)::text FROM sales_slips row WHERE id = ?",
            String.class,
            highSlip.id());
    var originalItem =
        jdbc.queryForObject(
            "SELECT to_jsonb(row)::text FROM sales_slip_items row WHERE sales_slip_id = ?",
            String.class,
            highSlip.id());
    var originalSnapshots = slipSnapshots(highSlip.id());
    var race =
        crossAfterRelease(
            () -> updates.update(highSlip.id(), request(highPartner, lowId, 95)),
            () -> updates.update(lowSlip.id(), request(lowPartner, highId, 10)));
    assertThat(race.winnerFailure()).isInstanceOf(IllegalArgumentException.class);
    assertThat(race.loserFailure()).isNull();
    assertThat(race.loserReleasedBeforeWaiting()).isFalse();
    assertStock(lowId, 100, 0);
    assertStock(highId, 100, 30);
    assertThat(
            jdbc.queryForObject(
                "SELECT to_jsonb(row)::text FROM sales_slips row WHERE id = ?",
                String.class,
                highSlip.id()))
        .isEqualTo(originalRoot);
    assertThat(
            jdbc.queryForObject(
                "SELECT to_jsonb(row)::text FROM sales_slip_items row WHERE sales_slip_id = ?",
                String.class,
                highSlip.id()))
        .isEqualTo(originalItem);
    assertThat(slipSnapshots(highSlip.id())).isEqualTo(originalSnapshots);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM sales_inventory_movements", Integer.class))
        .isEqualTo(4);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM orchid_group_mutations WHERE mutation_type IN ('RESERVE', 'RELEASE_RESERVATION')",
                Integer.class))
        .isEqualTo(4);
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @Test
  void overlappingAndDuplicateAllocationsKeepPostReleaseSnapshotsAndReservationTotals() {
    var input =
        new SalesSlipItemInput(
            "E2E 난",
            "팔레놉시스",
            "A",
            20,
            1000,
            null,
            List.of(
                new SalesSlipAllocationInput(highId, 5),
                new SalesSlipAllocationInput(lowId, 3),
                new SalesSlipAllocationInput(highId, 5),
                new SalesSlipAllocationInput(lowId, 7)));
    var base = request(lowPartner, lowId, 20);
    var edited =
        updates.update(
            lowSlip.id(),
            new SalesSlipCommand(
                base.saleDate(),
                base.salesType(),
                base.partnerId(),
                null,
                base.paymentStatus(),
                base.salesStatus(),
                base.paymentMethod(),
                base.memo(),
                List.of(input)));
    assertThat(edited.items().getFirst().allocations()).hasSize(2);
    edited
        .items()
        .getFirst()
        .allocations()
        .forEach(
            line -> {
              assertThat(line.allocatedQuantity()).isEqualTo(10);
              assertThat(line.creationSnapshot().reservedQuantity())
                  .isEqualTo(line.orchidGroupId().equals(lowId) ? 0 : 20);
            });
    assertStock(lowId, 100, 10);
    assertStock(highId, 100, 30);
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @Test
  void missingReplacementGroupLeavesNoPartialReleaseAndAllowsRetry() {
    var before = snapshot();
    assertThatThrownBy(() -> updates.update(highSlip.id(), request(highPartner, -99L, 20)))
        .isInstanceOf(NotFoundException.class);
    assertThat(snapshot()).isEqualTo(before);
    updates.update(highSlip.id(), request(highPartner, lowId, 20));
    assertStock(lowId, 100, 30);
    assertStock(highId, 100, 0);
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @Test
  void groupPrelockingRequiresTheUseCaseTransaction() {
    var before = snapshot();
    assertThatThrownBy(() -> reader.lockGroups(List.of(highId, lowId)))
        .isInstanceOf(IllegalTransactionStateException.class);
    assertThat(snapshot()).isEqualTo(before);
  }

  private RaceResult crossAfterRelease(Runnable winner, Runnable loser) throws Exception {
    return compete(winner, loser, true);
  }

  private RaceResult compete(Runnable winner, Runnable loser, boolean pauseAfterRelease)
      throws Exception {
    var released = new CountDownLatch(1);
    var resumeWinner = new CountDownLatch(1);
    var loserStarted = new CountDownLatch(1);
    var winnerPid = new AtomicInteger();
    var loserPid = new AtomicInteger();
    var loserReleased = new AtomicBoolean();
    doAnswer(
            invocation -> {
              var result = invocation.callRealMethod();
              int pid = jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
              if (pid == winnerPid.get() && pauseAfterRelease) {
                entityManager.flush();
                released.countDown();
                await(resumeWinner);
              } else if (pid == loserPid.get()) {
                loserReleased.set(true);
              }
              return result;
            })
        .when((OrchidGroupMutationEngine) AopTestUtils.getUltimateTargetObject(engine))
        .releaseReservation(any(ReleaseOrchidGroupReservationsMutationCommand.class));
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first =
          executor.submit(
              () ->
                  runTransaction(
                      winnerPid,
                      null,
                      () -> {
                        winner.run();
                        if (!pauseAfterRelease) {
                          entityManager.flush();
                          released.countDown();
                          await(resumeWinner);
                        }
                      }));
      try {
        assertThat(released.await(10, TimeUnit.SECONDS)).isTrue();
        var second = executor.submit(() -> runTransaction(loserPid, loserStarted, loser));
        assertThat(loserStarted.await(10, TimeUnit.SECONDS)).isTrue();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        boolean blocked = false;
        while (!second.isDone() && System.nanoTime() < deadline) {
          blocked =
              Boolean.TRUE.equals(
                  jdbc.queryForObject(
                      "SELECT ? = ANY(pg_blocking_pids(?))",
                      Boolean.class,
                      winnerPid.get(),
                      loserPid.get()));
          if (blocked) break;
          Thread.sleep(25);
        }
        assertThat(blocked).as("second backend PID waits for the winning transaction").isTrue();
        boolean releasedBeforeWaiting = loserReleased.get();
        resumeWinner.countDown();
        return new RaceResult(
            first.get(20, TimeUnit.SECONDS),
            second.get(20, TimeUnit.SECONDS),
            releasedBeforeWaiting);
      } finally {
        resumeWinner.countDown();
      }
    }
  }

  private Throwable runTransaction(AtomicInteger pid, CountDownLatch started, Runnable action) {
    try {
      new TransactionTemplate(transactionManager)
          .executeWithoutResult(
              status -> {
                pid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                jdbc.execute("SET LOCAL lock_timeout = '15s'");
                if (started != null) started.countDown();
                action.run();
              });
      return null;
    } catch (RuntimeException exception) {
      return exception;
    }
  }

  private void await(CountDownLatch latch) {
    try {
      assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }

  private Long partner() {
    return partners
        .saveAndFlush(
            new BusinessPartner(
                "교차 수정 " + UUID.randomUUID(), PartnerType.WHOLESALE, null, null, null, null))
        .getId();
  }

  private List<String> slipSnapshots(Long slipId) {
    return jdbc.queryForList(
        """
        SELECT to_jsonb(snapshot)::text FROM sales_orchid_group_snapshots snapshot
        JOIN sales_slip_item_allocations allocation ON allocation.id = snapshot.sales_slip_item_allocation_id
        JOIN sales_slip_items item ON item.id = allocation.sales_slip_item_id
        WHERE item.sales_slip_id = ? ORDER BY 1
        """,
        String.class,
        slipId);
  }

  private void assertStock(Long id, int quantity, int reserved) {
    var state = reader.getStates(List.of(id)).get(id);
    assertThat(state.quantity()).isEqualTo(quantity);
    assertThat(state.reservedQuantity()).isEqualTo(reserved);
  }

  private Map<String, List<String>> snapshot() {
    var rows = new LinkedHashMap<String, List<String>>();
    for (String table :
        List.of(
            "sales_slips",
            "sales_slip_items",
            "sales_slip_item_allocations",
            "sales_orchid_group_snapshots",
            "orchid_groups",
            "orchid_group_mutations",
            "orchid_group_mutation_entries",
            "orchid_group_mutation_relations",
            "sales_inventory_movements",
            "partner_balance_summaries",
            "partner_settlement_settings",
            "partner_payment_events",
            "audit_events")) {
      rows.put(
          table,
          jdbc.queryForList(
              "SELECT to_jsonb(row)::text FROM " + table + " row ORDER BY 1", String.class));
    }
    return rows;
  }

  private void transform() {
    var sourceGroups = groups.findAllForUpdateByIdIn(List.of(highId, lowId));
    var example = sourceGroups.getFirst();
    var key = UUID.randomUUID();
    engine.transform(
        new TransformOrchidGroupsMutationCommand(
            new OrchidGroupMutationSource(
                OrchidGroupMutationSourceDomain.FARM,
                "LOCK_ORDER_TEST",
                key.toString(),
                "TRANSFORM",
                key),
            List.of(
                new TransformOrchidGroupMutationSource(
                    lowId, 20, BigDecimal.valueOf(4), BigDecimal.valueOf(5)),
                new TransformOrchidGroupMutationSource(
                    highId, 20, BigDecimal.valueOf(9), BigDecimal.TEN)),
            List.of(
                new TransformOrchidGroupMutationResult(
                    example.getBedZone().getId(), resultDetails(example, 4, 5)),
                new TransformOrchidGroupMutationResult(
                    example.getBedZone().getId(), resultDetails(example, 9, 10))),
            DATE,
            "잠금 순서 구조 변경"));
  }

  private OrchidGroupMutationDetails resultDetails(OrchidGroup example, int start, int end) {
    return new OrchidGroupMutationDetails(
        example.getVariety().getId(),
        20,
        example.getPotSize(),
        example.getAgeYear(),
        "정상",
        example.getPlacementType(),
        example.getTrayCount(),
        example.getSplitPlacementAllowed(),
        BigDecimal.valueOf(start),
        BigDecimal.valueOf(end),
        null);
  }

  private SalesSlipCommand request(Long partnerId, Long groupId, int quantity) {
    return new SalesSlipCommand(
        DATE,
        SalesType.DIRECT,
        partnerId,
        null,
        "미입금",
        SalesSlip.STATUS_DRAFT,
        null,
        "배분 변경",
        List.of(
            new SalesSlipItemInput(
                "E2E 난",
                "팔레놉시스",
                "A",
                quantity,
                1000,
                null,
                List.of(new SalesSlipAllocationInput(groupId, quantity)))));
  }

  private record RaceResult(
      Throwable winnerFailure, Throwable loserFailure, boolean loserReleasedBeforeWaiting) {}
}
