package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.greenhouse.backend.OrchidGroupStateChainTestSupport;
import com.greenhouse.backend.auction.application.AuctionTrackingService;
import com.greenhouse.backend.auction.application.RecordAuctionResultCommand;
import com.greenhouse.backend.auction.domain.AuctionAttemptStatus;
import com.greenhouse.backend.auction.domain.AuctionLotStatus;
import com.greenhouse.backend.auction.domain.AuctionResultLineInput;
import com.greenhouse.backend.auction.dto.AuctionLotStatusRequest;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerCutoverCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerCutoverService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerReconciliationService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupStateChainMigrationService;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.partner.domain.PartnerType;
import com.greenhouse.backend.partner.repository.BusinessPartnerRepository;
import com.greenhouse.backend.sales.application.SalesQueryService;
import com.greenhouse.backend.sales.application.SalesSlipCreationService;
import com.greenhouse.backend.sales.application.SalesSlipStatusService;
import com.greenhouse.backend.sales.application.command.SalesSlipAllocationInput;
import com.greenhouse.backend.sales.application.command.SalesSlipCommand;
import com.greenhouse.backend.sales.application.command.SalesSlipItemInput;
import com.greenhouse.backend.sales.application.document.SalesSlipDocument;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesSlipAction;
import com.greenhouse.backend.sales.domain.SalesType;
import com.greenhouse.backend.sales.dto.SalesSlipStatusUpdateRequest;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("work-e2e")
@TestPropertySource(properties = "app.orchid-ledger.writer-version=1.1.0")
class AuctionShipmentCancellationPostgresE2ETest extends WorkE2ETestBase {

  private static final LocalDate DATE = LocalDate.of(2043, 1, 1);

  @Autowired private WorkTestDataSeeder seeder;
  @Autowired private OrchidGroupRepository groups;
  @Autowired private BusinessPartnerRepository partners;
  @Autowired private SalesSlipCreationService creation;
  @Autowired private SalesSlipStatusService statuses;
  @Autowired private SalesQueryService sales;
  @Autowired private AuctionTrackingService auctions;
  @Autowired private OrchidGroupStateChainMigrationService migration;
  @Autowired private OrchidGroupLedgerCutoverService cutover;
  @Autowired private OrchidGroupLedgerReconciliationService reconciliation;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private EntityManager entityManager;
  @Autowired private JdbcTemplate jdbc;

  private Long groupId;
  private SalesSlipDocument slip;
  private Long lotId;

  @BeforeEach
  void seed() {
    jdbc.execute("TRUNCATE TABLE sales_slips, auction_shipments CONTINUE IDENTITY CASCADE");
    seeder.resetKeepingSequences();
    groupId = seeder.seedContractScenario().orchidGroupId();
    UUID key = UUID.randomUUID();
    OrchidGroupStateChainTestSupport.importCurrentGroups(migration, groups, key, DATE, "1.0.0");
    cutover.execute(new OrchidGroupLedgerCutoverCommand(key, DATE, "1.0.0", "1.1.0", true));
    var partner =
        partners.saveAndFlush(
            new BusinessPartner("경매 취소 " + key, PartnerType.AUCTION_HOUSE, null, null, null, null));
    slip =
        creation.create(
            new SalesSlipCommand(
                DATE,
                SalesType.AUCTION,
                partner.getId(),
                null,
                null,
                SalesSlip.STATUS_AUCTION_SHIPMENT_COMPLETED,
                null,
                null,
                List.of(item(3), item(2))));
    lotId = slip.items().getFirst().auctionShipmentLotId();
    assertThat(groups.findById(groupId).orElseThrow().getQuantity()).isEqualTo(95);
  }

  @ParameterizedTest
  @EnumSource(AuctionAttemptStatus.class)
  void resultsRemainProtectedAfterStatusIsResetToWaiting(AuctionAttemptStatus resultStatus) {
    recordResult(resultStatus);
    resetStatusToWaiting();
    assertThat(auctions.getLot(lotId).currentStatus()).isEqualTo(AuctionLotStatus.WAITING);
    assertCancellationBlockedWithoutChanges();
  }

  @Test
  void attemptsRemainProtectedWhenLegacyStatusHistoryIsMissing() {
    recordResult(AuctionAttemptStatus.FAILED);
    resetStatusToWaiting();
    // Legacy data may retain attempts without status history.
    jdbc.update("DELETE FROM auction_lot_status_history WHERE shipment_lot_id = ?", lotId);
    assertCancellationBlockedWithoutChanges();
  }

  @Test
  void statusHistoryWithoutResultsCannotBeDeletedByCancelingTheSlip() {
    auctions.changeStatus(
        lotId, new AuctionLotStatusRequest(AuctionLotStatus.IN_PROGRESS, "경매 진행", null, null));
    resetStatusToWaiting();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM auction_attempts", Long.class)).isZero();
    assertCancellationBlockedWithoutChanges();
  }

  @Test
  void untouchedShipmentCanBeCanceledAndRestoresStock() {
    assertThat(sales.getSalesSlip(slip.id()).availableActions()).contains(SalesSlipAction.CANCEL);
    cancel();
    var canceled = sales.getSalesSlip(slip.id());
    assertThat(canceled.salesStatus()).isEqualTo(SalesSlip.STATUS_CANCELED);
    assertThat(canceled.auctionShipmentId()).isNull();
    assertThat(canceled.items())
        .allSatisfy(item -> assertThat(item.auctionShipmentLotId()).isNull());
    assertThat(groups.findById(groupId).orElseThrow().getQuantity()).isEqualTo(100);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM auction_shipments", Long.class)).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM auction_shipment_lots", Long.class))
        .isZero();
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @Test
  void cancellationAndDeletionRollBackWhenLaterWorkFails() {
    var before = snapshot();
    assertThatThrownBy(
            () ->
                new TransactionTemplate(transactionManager)
                    .executeWithoutResult(
                        status -> {
                          cancel();
                          entityManager.flush();
                          throw new IllegalStateException("후속 실패");
                        }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("후속 실패");
    assertThat(snapshot()).isEqualTo(before);
    assertThat(sales.getSalesSlip(slip.id()).availableActions()).contains(SalesSlipAction.CANCEL);
    cancel();
    assertThat(groups.findById(groupId).orElseThrow().getQuantity()).isEqualTo(100);
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @Test
  void resultWinningTheLockBlocksCancellationAndPreservesHistory() throws Exception {
    long mutations = jdbc.queryForObject("SELECT count(*) FROM orchid_group_mutations", Long.class);
    long movements =
        jdbc.queryForObject("SELECT count(*) FROM sales_inventory_movements", Long.class);
    var failure =
        compete(
            () -> {
              recordResult(AuctionAttemptStatus.SOLD);
              resetStatusToWaiting();
            },
            this::cancel);
    assertThat(failure)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("취소할 수 없습니다");
    assertThat(sales.getSalesSlip(slip.id()).salesStatus())
        .isEqualTo(SalesSlip.STATUS_AUCTION_SHIPMENT_COMPLETED);
    assertThat(sales.getSalesSlip(slip.id()).availableActions())
        .doesNotContain(SalesSlipAction.CANCEL);
    assertThat(auctions.getLot(lotId).attempts()).hasSize(1);
    assertThat(auctions.getLot(lotId).statusHistory()).hasSize(2);
    assertThat(groups.findById(groupId).orElseThrow().getQuantity()).isEqualTo(95);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM orchid_group_mutations", Long.class))
        .isEqualTo(mutations);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM sales_inventory_movements", Long.class))
        .isEqualTo(movements);
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @Test
  void cancellationWinningTheLockRejectsTheWaitingResultWriter() throws Exception {
    var failure = compete(this::cancel, () -> recordResult(AuctionAttemptStatus.SOLD));
    assertThat(failure).isInstanceOf(NotFoundException.class);
    assertThat(sales.getSalesSlip(slip.id()).salesStatus()).isEqualTo(SalesSlip.STATUS_CANCELED);
    assertThat(groups.findById(groupId).orElseThrow().getQuantity()).isEqualTo(100);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM auction_attempts", Long.class)).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM auction_result_lines", Long.class))
        .isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM auction_lot_status_history", Long.class))
        .isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM auction_shipments", Long.class)).isZero();
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  private void assertCancellationBlockedWithoutChanges() {
    var before = snapshot();
    assertThatThrownBy(this::cancel)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("취소할 수 없습니다");
    assertThat(snapshot()).isEqualTo(before);
    assertThat(sales.getSalesSlip(slip.id()).availableActions())
        .doesNotContain(SalesSlipAction.CANCEL);
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  private Map<String, List<Map<String, Object>>> snapshot() {
    var rows = new LinkedHashMap<String, List<Map<String, Object>>>();
    for (String table :
        List.of(
            "sales_slips",
            "sales_slip_items",
            "sales_slip_item_allocations",
            "auction_shipments",
            "auction_shipment_lots",
            "auction_attempts",
            "auction_result_lines",
            "auction_lot_status_history",
            "orchid_groups",
            "orchid_group_mutations",
            "orchid_group_mutation_entries",
            "sales_inventory_movements",
            "audit_events")) {
      rows.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id"));
    }
    return rows;
  }

  private Throwable compete(Runnable winner, Runnable loser) throws Exception {
    var winnerReady = new CountDownLatch(1);
    var loserReady = new CountDownLatch(1);
    var commitWinner = new CountDownLatch(1);
    var winnerPid = new AtomicInteger();
    var loserPid = new AtomicInteger();
    var transaction = new TransactionTemplate(transactionManager);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first =
          executor.submit(
              () ->
                  transaction.executeWithoutResult(
                      status -> {
                        winnerPid.set(
                            jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                        winner.run();
                        entityManager.flush();
                        winnerReady.countDown();
                        await(commitWinner);
                      }));
      try {
        assertThat(winnerReady.await(10, TimeUnit.SECONDS)).isTrue();
        var second =
            executor.submit(
                () -> {
                  try {
                    transaction.executeWithoutResult(
                        status -> {
                          loserPid.set(
                              jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                          loserReady.countDown();
                          loser.run();
                        });
                    return null;
                  } catch (RuntimeException exception) {
                    return exception;
                  }
                });
        assertThat(loserReady.await(10, TimeUnit.SECONDS)).isTrue();
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
        assertThat(blocked).as("second transaction waits for the winning row lock").isTrue();
        commitWinner.countDown();
        first.get(20, TimeUnit.SECONDS);
        return second.get(20, TimeUnit.SECONDS);
      } finally {
        commitWinner.countDown();
      }
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

  private void recordResult(AuctionAttemptStatus status) {
    List<AuctionResultLineInput> lines =
        switch (status) {
          case SOLD -> List.of(new AuctionResultLineInput("A", 3, 1000, null, null));
          case PARTIALLY_SOLD -> List.of(new AuctionResultLineInput("A", 1, 1000, null, null));
          case FAILED, RETURN_INFERRED -> List.of();
        };
    auctions.addResult(
        lotId,
        new RecordAuctionResultCommand(
            UUID.randomUUID().toString(), DATE, 1, status, null, null, lines));
  }

  private void resetStatusToWaiting() {
    auctions.changeStatus(
        lotId, new AuctionLotStatusRequest(AuctionLotStatus.WAITING, "대기 상태 보정", null, null));
  }

  private void cancel() {
    statuses.updateStatus(
        slip.id(), new SalesSlipStatusUpdateRequest(SalesSlip.STATUS_CANCELED, null));
  }

  private SalesSlipItemInput item(int quantity) {
    return new SalesSlipItemInput(
        "E2E 난",
        "팔레놉시스",
        "A",
        quantity,
        0,
        null,
        List.of(new SalesSlipAllocationInput(groupId, quantity)));
  }
}
