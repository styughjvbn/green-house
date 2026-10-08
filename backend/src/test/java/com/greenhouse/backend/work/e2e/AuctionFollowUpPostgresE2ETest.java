package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationDetails;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerReconciliationService;
import com.greenhouse.backend.sales.api.document.SalesType;
import com.greenhouse.backend.sales.api.partner.PartnerType;
import com.greenhouse.backend.sales.auction.application.*;
import com.greenhouse.backend.sales.auction.domain.*;
import com.greenhouse.backend.sales.auction.repository.AuctionShipmentRepository;
import com.greenhouse.backend.sales.document.application.SalesSlipCreationService;
import com.greenhouse.backend.sales.document.application.command.SalesSlipAllocationInput;
import com.greenhouse.backend.sales.document.application.command.SalesSlipCommand;
import com.greenhouse.backend.sales.document.application.command.SalesSlipItemInput;
import com.greenhouse.backend.sales.document.domain.SalesSlip;
import com.greenhouse.backend.sales.partner.domain.BusinessPartner;
import com.greenhouse.backend.sales.partner.repository.BusinessPartnerRepository;
import com.greenhouse.backend.support.OrchidGroupLedgerTestFixture;
import java.math.BigDecimal;
import java.sql.Timestamp;
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
import org.springframework.test.context.TestPropertySource;

@Tag("work-e2e")
@TestPropertySource(properties = "app.orchid-ledger.writer-version=1.1.0")
class AuctionFollowUpPostgresE2ETest extends WorkE2ETestBase {
  private static final LocalDate DATE = LocalDate.of(2043, 1, 1);
  @Autowired AuctionFollowUpService followUp;
  @Autowired SalesSlipCreationService creation;
  @Autowired OrchidGroupLedgerReconciliationService reconciliation;
  @Autowired AuctionShipmentRepository shipments;
  @Autowired BusinessPartnerRepository partners;
  @Autowired WorkTestDataSeeder seeder;
  @Autowired OrchidGroupLedgerTestFixture ledgerFixture;
  @Autowired JdbcTemplate jdbc;
  private Long lotId;
  private Long zoneId;
  private Long varietyId;

  @BeforeEach
  void seed() {
    jdbc.execute("TRUNCATE sales_slips, auction_shipments CONTINUE IDENTITY CASCADE");
    seeder.resetKeepingSequences();
    var fixture = seeder.seedContractScenario();
    zoneId = fixture.bedZoneId();
    varietyId =
        jdbc.queryForObject(
            "SELECT variety_id FROM orchid_groups WHERE id = ?",
            Long.class,
            fixture.orchidGroupId());
    UUID key = UUID.randomUUID();
    ledgerFixture.seedBaseline(key, DATE, "1.0.0");
    ledgerFixture.activate(key);
    var house =
        partners.saveAndFlush(
            new BusinessPartner("반환 " + key, PartnerType.AUCTION_HOUSE, null, null, null, null));
    var shipment = new AuctionShipment(DATE, house.getId(), PartnerType.AUCTION_HOUSE);
    var lot = new AuctionShipmentLot("난", "E2E 난", null, null, 40);
    lot.recordResult(
        DATE,
        1,
        AuctionAttemptStatus.PARTIALLY_SOLD,
        List.of(new AuctionResultLineInput(null, 10, 1000, null, AuctionInspectionStatus.NORMAL)),
        null,
        null,
        DATE.atStartOfDay());
    shipment.addLot(lot);
    shipments.saveAndFlush(shipment);
    lotId = lot.getId();
  }

  @Test
  void partialArrivalCreatesNewStockAndMustBeCanceledBeforeChangingTheWholeDecision() {
    long inboundBefore = count("inbound_records");
    var decision = followUp.decide(lotId, decision(AuctionFollowUpMethod.FARM_RETURN, "farm"));
    assertThat(decision.followUp().decidedQuantity()).isEqualTo(30);
    var arrived = followUp.arrive(lotId, arrival("arrive", 10));
    assertThat(arrived.followUp().pendingQuantity()).isEqualTo(20);
    assertThat(arrived.followUp().decisionChangeAllowed()).isFalse();
    assertThat(arrived.arrival().decisionId()).isEqualTo(decision.followUp().decisionId());
    assertThat(
            jdbc.queryForObject(
                "SELECT quantity FROM orchid_groups WHERE id = ?",
                Integer.class,
                arrived.arrival().orchidGroupId()))
        .isEqualTo(10);
    assertThat(
            jdbc.queryForObject(
                "SELECT inbound_record_id FROM orchid_groups WHERE id = ?",
                Long.class,
                arrived.arrival().orchidGroupId()))
        .isNull();
    assertThat(count("inbound_records")).isEqualTo(inboundBefore);
    assertThat(reconciliation.reconcile().issues()).isEmpty();
    assertThatThrownBy(
            () -> followUp.decide(lotId, decision(AuctionFollowUpMethod.REAUCTION, "blocked")))
        .isInstanceOf(ConflictException.class);
    assertThat(followUp.arrive(lotId, arrival("arrive", 10))).isEqualTo(arrived);
    var corrected =
        followUp.cancelArrival(
            lotId,
            arrived.arrival().id(),
            new CancelAuctionArrivalCommand(DATE, "cancel", "작업자", "도착 입력 정정"));
    assertThat(arrived.arrival().cancellationAllowed()).isTrue();
    assertThat(corrected.arrival().cancellationAllowed()).isFalse();
    assertThat(corrected.followUp().pendingQuantity()).isEqualTo(30);
    assertThat(corrected.followUp().decisionChangeAllowed()).isTrue();
    assertThat(corrected.arrival().creationMutationId())
        .isEqualTo(arrived.arrival().creationMutationId());
    assertThat(corrected.arrival().cancellationMutationId()).isNotNull();
    assertThat(
            jdbc.queryForObject(
                "SELECT quantity FROM orchid_groups WHERE id = ?",
                Integer.class,
                arrived.arrival().orchidGroupId()))
        .isZero();
    assertThat(
            followUp.cancelArrival(
                lotId,
                arrived.arrival().id(),
                new CancelAuctionArrivalCommand(DATE, "cancel", "작업자", "도착 입력 정정")))
        .isEqualTo(corrected);
    assertThat(reconciliation.reconcile().issues()).isEmpty();
    var reauction = followUp.decide(lotId, decision(AuctionFollowUpMethod.REAUCTION, "again"));
    assertThat(reauction.followUp().method()).isEqualTo(AuctionFollowUpMethod.REAUCTION);
    assertThat(followUp.arrive(lotId, arrival("arrive", 10))).isEqualTo(arrived);
  }

  @Test
  void disposalCompletesTheRemainderWithoutChangingFarmStock() {
    long groupsBefore = count("orchid_groups");
    long mutationsBefore = count("orchid_group_mutations");
    var command = decision(AuctionFollowUpMethod.AUCTION_DISPOSAL, "dispose");
    var disposed = followUp.decide(lotId, command);
    assertThat(disposed.followUp().disposedQuantity()).isEqualTo(30);
    assertThat(disposed.followUp().pendingQuantity()).isZero();
    assertThat(disposed.followUp().arrivalAllowed()).isFalse();
    assertThat(count("orchid_groups")).isEqualTo(groupsBefore);
    assertThat(count("orchid_group_mutations")).isEqualTo(mutationsBefore);
    assertThat(followUp.decide(lotId, command)).isEqualTo(disposed);
    assertThat(
            jdbc.queryForObject(
                "SELECT current_status FROM auction_shipment_lots WHERE id = ?",
                String.class,
                lotId))
        .isEqualTo("DISPOSED");
  }

  @Test
  void failedFarmPlacementRollsBackLotArrivalReceiptAndMutationAndAllowsRetry() {
    followUp.decide(lotId, decision(AuctionFollowUpMethod.FARM_RETURN, "farm"));
    long mutationsBefore = count("orchid_group_mutations");
    var command = arrival("placement", 10);
    var invalid =
        new AuctionArrivalCommand(
            DATE, -999L, command.details(), command.idempotencyKey(), command.worker());
    assertThatThrownBy(() -> followUp.arrive(lotId, invalid)).isInstanceOf(RuntimeException.class);
    assertThat(followUp.getFollowUp(lotId).pendingQuantity()).isEqualTo(30);
    assertThat(count("auction_return_arrivals")).isZero();
    assertThat(count("orchid_group_mutations")).isEqualTo(mutationsBefore);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM auction_command_receipts WHERE command_type = 'ARRIVAL'",
                Long.class))
        .isZero();
    assertThat(followUp.arrive(lotId, command).arrival().quantity()).isEqualTo(10);
  }

  @Test
  void concurrentRetriesCreateOnlyOneArrivalAndFarmGroup() throws Exception {
    followUp.decide(lotId, decision(AuctionFollowUpMethod.FARM_RETURN, "farm"));
    var command = arrival("parallel", 10);
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    long groupsBefore = count("orchid_groups");
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first =
          pool.submit(
              () -> {
                ready.countDown();
                start.await();
                return followUp.arrive(lotId, command);
              });
      var second =
          pool.submit(
              () -> {
                ready.countDown();
                start.await();
                return followUp.arrive(lotId, command);
              });
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(first.get(30, TimeUnit.SECONDS)).isEqualTo(second.get(30, TimeUnit.SECONDS));
    }
    assertThat(count("auction_return_arrivals")).isEqualTo(1);
    assertThat(count("orchid_groups")).isEqualTo(groupsBefore + 1);
    assertThat(followUp.getFollowUp(lotId).pendingQuantity()).isEqualTo(20);
  }

  @Test
  void currentHttpContractReturnsCapabilitiesAndRejectsAChangedRetry() throws Exception {
    String path = "/api/auction-lots/" + lotId + "/follow-up";
    var result =
        post(
            path,
            objectMapper.writeValueAsString(decision(AuctionFollowUpMethod.FARM_RETURN, "http")));
    assertThat(result.status()).isEqualTo(200);
    assertThat(result.data().path("followUp").path("arrivalAllowed").asBoolean()).isTrue();
    var conflict =
        post(
            path,
            objectMapper.writeValueAsString(decision(AuctionFollowUpMethod.REAUCTION, "http")));
    assertThat(conflict.status()).isEqualTo(409);
    assertThat(conflict.body().path("error").path("code").asText())
        .isEqualTo("AUCTION_REQUEST_KEY_CONFLICT");
    assertThat(get("/api/auction-lots/" + lotId + "/arrivals?page=0&size=20").status())
        .isEqualTo(200);
  }

  @Test
  void differentConcurrentArrivalKeysCannotExceedTheRemainingQuantity() throws Exception {
    followUp.decide(lotId, decision(AuctionFollowUpMethod.FARM_RETURN, "farm"));
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first = pool.submit(() -> concurrentArrival("first", ready, start));
      var second = pool.submit(() -> concurrentArrival("second", ready, start));
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
    assertThat(count("auction_return_arrivals")).isEqualTo(1);
    assertThat(followUp.getFollowUp(lotId).pendingQuantity()).isEqualTo(10);
  }

  private boolean concurrentArrival(String key, CountDownLatch ready, CountDownLatch start)
      throws Exception {
    ready.countDown();
    start.await();
    try {
      followUp.arrive(lotId, arrival(key, 20));
      return true;
    } catch (IllegalArgumentException rejected) {
      return false;
    }
  }

  @Test
  void laterReservationPreventsArrivalCancellationAndKeepsTheDecisionLocked() {
    followUp.decide(lotId, decision(AuctionFollowUpMethod.FARM_RETURN, "farm"));
    var arrived = followUp.arrive(lotId, arrival("arrive", 10));
    var buyer =
        partners.saveAndFlush(
            new BusinessPartner(
                "반환품 구매자 " + UUID.randomUUID(), PartnerType.WHOLESALE, null, null, null, null));
    creation.create(
        new SalesSlipCommand(
            DATE,
            SalesType.DIRECT,
            buyer.getId(),
            null,
            null,
            SalesSlip.STATUS_DRAFT,
            null,
            null,
            List.of(
                new SalesSlipItemInput(
                    "E2E 난",
                    "팔레놉시스",
                    null,
                    1,
                    1000,
                    null,
                    List.of(new SalesSlipAllocationInput(arrived.arrival().orchidGroupId(), 1))))));
    assertThatThrownBy(
            () ->
                followUp.cancelArrival(
                    lotId,
                    arrived.arrival().id(),
                    new CancelAuctionArrivalCommand(DATE, "blocked-cancel", "작업자", "정정")))
        .isInstanceOf(ConflictException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT canceled_at FROM auction_return_arrivals WHERE id = ?",
                Timestamp.class,
                arrived.arrival().id()))
        .isNull();
    assertThat(
            jdbc.queryForObject(
                "SELECT quantity FROM orchid_groups WHERE id = ?",
                Integer.class,
                arrived.arrival().orchidGroupId()))
        .isEqualTo(10);
    assertThat(followUp.getFollowUp(lotId).decisionChangeAllowed()).isFalse();
    assertThat(reconciliation.reconcile().issues()).isEmpty();
  }

  @Test
  void malformedNewReceiptSnapshotsAreRejectedByPostgres() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO auction_command_receipts (id, lot_id, command_type, request_key, request_fingerprint, response_snapshot, created_at) VALUES (999999, ?, 'ARRIVAL', 'bad', repeat('a', 64), '{}'::jsonb, CURRENT_TIMESTAMP)",
                    lotId))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(count("auction_command_receipts")).isZero();
  }

  private AuctionFollowUpCommand decision(AuctionFollowUpMethod method, String key) {
    return new AuctionFollowUpCommand(method, key, "작업자", "경매사와 확인");
  }

  private AuctionArrivalCommand arrival(String key, int quantity) {
    return new AuctionArrivalCommand(
        DATE,
        zoneId,
        new OrchidGroupMutationDetails(
            varietyId,
            quantity,
            "3.5치",
            2,
            "정상",
            "POT",
            null,
            false,
            new BigDecimal("5"),
            new BigDecimal("6"),
            null),
        key,
        "작업자");
  }

  private long count(String table) {
    return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
  }
}
