package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.greenhouse.backend.sales.api.document.SalesType;
import com.greenhouse.backend.sales.api.partner.PartnerType;
import com.greenhouse.backend.sales.auction.application.AuctionTrackingService;
import com.greenhouse.backend.sales.auction.application.RecordAuctionResultCommand;
import com.greenhouse.backend.sales.auction.domain.*;
import com.greenhouse.backend.sales.auction.repository.AuctionShipmentRepository;
import com.greenhouse.backend.sales.document.application.SalesQueryService;
import com.greenhouse.backend.sales.document.domain.SalesSlip;
import com.greenhouse.backend.sales.document.domain.SalesSlipItem;
import com.greenhouse.backend.sales.document.repository.SalesSlipRepository;
import com.greenhouse.backend.sales.partner.domain.BusinessPartner;
import com.greenhouse.backend.sales.partner.repository.BusinessPartnerRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("work-e2e")
class AuctionDocumentAmountPostgresE2ETest extends WorkE2ETestBase {
  private static final LocalDate DATE = LocalDate.of(2043, 1, 1);
  @Autowired AuctionTrackingService auctions;
  @Autowired AuctionShipmentRepository shipments;
  @Autowired SalesSlipRepository documents;
  @Autowired BusinessPartnerRepository partners;
  @Autowired SalesQueryService sales;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager transactions;
  Long documentId;
  Long partnerId;
  List<Long> lotIds;

  @BeforeEach
  void seed() {
    var partner =
        partners.saveAndFlush(
            new BusinessPartner(
                "금액 " + UUID.randomUUID(), PartnerType.AUCTION_HOUSE, null, null, null, null));
    partnerId = partner.getId();
    var shipment = new AuctionShipment(DATE, partnerId, PartnerType.AUCTION_HOUSE);
    shipment.addLot(new AuctionShipmentLot("난", "품종1", "A", null, 10));
    shipment.addLot(new AuctionShipmentLot("난", "품종2", "B", null, 10));
    shipments.saveAndFlush(shipment);
    lotIds = shipment.getLots().stream().map(AuctionShipmentLot::getId).toList();
    var document =
        new SalesSlip(
            DATE,
            SalesType.AUCTION,
            shipment.getId(),
            partnerId,
            "정산 대기",
            SalesSlip.STATUS_AUCTION_SHIPMENT_COMPLETED,
            null,
            null);
    for (var id : lotIds) document.addItem(new SalesSlipItem(id, "난", null, "A", 10, 0, null));
    documentId = documents.saveAndFlush(document).getId();
  }

  @Test
  void currentGrossIncludesEveryAttemptAndLotButDoesNotChangeReceivablesOrReceipts() {
    var partial = command("partial", AuctionAttemptStatus.PARTIALLY_SOLD, 4, 1000);
    auctions.addResult(lotIds.getFirst(), partial);
    auctions.addResult(lotIds.getFirst(), partial); // Replay must not add the same money twice.
    assertAmount(4000);
    auctions.addResult(lotIds.getFirst(), command("failed", AuctionAttemptStatus.FAILED, 0, 0));
    assertAmount(4000);
    auctions.addResult(lotIds.getFirst(), command("remaining", AuctionAttemptStatus.SOLD, 6, 2000));
    auctions.addResult(lotIds.getLast(), command("other", AuctionAttemptStatus.SOLD, 10, 3000));
    assertAmount(46000);
    auctions.addResult(lotIds.getFirst(), partial); // Old replay retains the current projection.
    assertAmount(46000);
    var slip = sales.getSalesSlip(documentId);
    assertThat(slip.remainingAmount()).isZero();
    assertThat(slip.paidAmount()).isZero();
    assertThat(slip.paymentStatus()).isEqualTo("정산 대기");
    assertThat(slip.items())
        .allSatisfy(
            item -> {
              assertThat(item.amount()).isZero();
              assertThat(item.unitPrice()).isZero();
            });
  }

  @Test
  void overflowingDocumentTotalRejectsTheSecondResultAtomically() {
    auctions.addResult(
        lotIds.getFirst(), command("large", AuctionAttemptStatus.PARTIALLY_SOLD, 1, 1500000000));
    assertThatThrownBy(
            () ->
                auctions.addResult(
                    lotIds.getLast(),
                    command("overflow", AuctionAttemptStatus.PARTIALLY_SOLD, 1, 1500000000)))
        .isInstanceOf(IllegalArgumentException.class);
    assertAmount(1500000000);
    assertThat(auctions.getLot(lotIds.getLast()).attempts()).isEmpty();
    assertThat(auctions.getLot(lotIds.getLast()).soldQuantity()).isZero();
  }

  @Test
  void resultAndProjectionRollBackTogether() {
    assertThatThrownBy(
            () ->
                new TransactionTemplate(transactions)
                    .executeWithoutResult(
                        status -> {
                          auctions.addResult(
                              lotIds.getFirst(),
                              command("rollback", AuctionAttemptStatus.SOLD, 10, 1000));
                          throw new IllegalStateException("later failure");
                        }))
        .isInstanceOf(IllegalStateException.class);
    assertAmount(0);
    assertThat(auctions.getLot(lotIds.getFirst()).attempts()).isEmpty();
    auctions.addResult(lotIds.getFirst(), command("rollback", AuctionAttemptStatus.SOLD, 10, 1000));
    assertAmount(10000);
  }

  @Test
  void separateLotsSerializeAndPreserveBothCommittedAmounts() throws Exception {
    var ready = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var secondReady = new CountDownLatch(1);
    var firstPid = new AtomicInteger();
    var secondPid = new AtomicInteger();
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first =
          executor.submit(
              () ->
                  new TransactionTemplate(transactions)
                      .executeWithoutResult(
                          status -> {
                            firstPid.set(
                                jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                            auctions.addResult(
                                lotIds.getFirst(),
                                command("first", AuctionAttemptStatus.SOLD, 10, 1000));
                            ready.countDown();
                            await(release);
                          }));
      try {
        assertThat(ready.await(15, TimeUnit.SECONDS)).isTrue();
        var second =
            executor.submit(
                () ->
                    new TransactionTemplate(transactions)
                        .executeWithoutResult(
                            status -> {
                              secondPid.set(
                                  jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                              secondReady.countDown();
                              auctions.addResult(
                                  lotIds.getLast(),
                                  command("second", AuctionAttemptStatus.SOLD, 10, 2000));
                            }));
        assertThat(secondReady.await(10, TimeUnit.SECONDS)).isTrue();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        boolean blocked = false;
        while (!second.isDone() && System.nanoTime() < deadline) {
          blocked =
              Boolean.TRUE.equals(
                  jdbc.queryForObject(
                      "SELECT ? = ANY(pg_blocking_pids(?))",
                      Boolean.class,
                      firstPid.get(),
                      secondPid.get()));
          if (blocked) break;
          Thread.sleep(25);
        }
        assertThat(blocked).as("second lot waits for the document's transaction").isTrue();
        release.countDown();
        first.get(20, TimeUnit.SECONDS);
        second.get(20, TimeUnit.SECONDS);
      } finally {
        release.countDown();
      }
    }
    assertAmount(30000);
  }

  private void assertAmount(int expected) {
    assertThat(sales.getSalesSlip(documentId).totalAmount()).isEqualTo(expected);
    assertThat(sales.getSalesSlipPage(partnerId, null, null, null, null, null, 0, 10).content())
        .singleElement()
        .satisfies(slip -> assertThat(slip.totalAmount()).isEqualTo(expected));
  }

  private RecordAuctionResultCommand command(
      String key, AuctionAttemptStatus status, int quantity, int price) {
    return new RecordAuctionResultCommand(
        key,
        DATE,
        null,
        status,
        null,
        null,
        quantity == 0
            ? List.of()
            : List.of(new AuctionResultLineInput("A", quantity, price, null, null)));
  }

  private void await(CountDownLatch latch) {
    try {
      if (!latch.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
