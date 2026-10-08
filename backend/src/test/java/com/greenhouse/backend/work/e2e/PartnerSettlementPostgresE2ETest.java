package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.greenhouse.backend.audit.repository.AuditEventRepository;
import com.greenhouse.backend.sales.api.document.SalesSlipDocument;
import com.greenhouse.backend.sales.api.document.SalesType;
import com.greenhouse.backend.sales.api.partner.BusinessPartnerInfo;
import com.greenhouse.backend.sales.api.partner.PartnerType;
import com.greenhouse.backend.sales.application.auction.AuctionProceedsPaymentService;
import com.greenhouse.backend.sales.application.auction.AuctionProceedsReader;
import com.greenhouse.backend.sales.application.auction.AuctionProceedsService;
import com.greenhouse.backend.sales.application.auction.AuctionShipmentCreator;
import com.greenhouse.backend.sales.application.direct.SalesPaymentService;
import com.greenhouse.backend.sales.application.document.SalesSlipStatusService;
import com.greenhouse.backend.sales.application.partner.BusinessPartnerLock;
import com.greenhouse.backend.sales.application.partner.PartnerSettlementSettingsService;
import com.greenhouse.backend.sales.application.payment.ManualPaymentCommand;
import com.greenhouse.backend.sales.application.payment.PartnerBalanceService;
import com.greenhouse.backend.sales.application.payment.PaymentService;
import com.greenhouse.backend.sales.document.spi.AuctionDocumentPort.LotDraft;
import com.greenhouse.backend.sales.domain.auction.AuctionAttempt;
import com.greenhouse.backend.sales.domain.auction.AuctionAttemptStatus;
import com.greenhouse.backend.sales.domain.auction.AuctionInspectionStatus;
import com.greenhouse.backend.sales.domain.auction.AuctionProceeds;
import com.greenhouse.backend.sales.domain.auction.AuctionResultLine;
import com.greenhouse.backend.sales.domain.auction.AuctionShipment;
import com.greenhouse.backend.sales.domain.auction.AuctionShipmentLot;
import com.greenhouse.backend.sales.domain.document.SalesSlip;
import com.greenhouse.backend.sales.domain.document.SalesSlipItem;
import com.greenhouse.backend.sales.domain.partner.BusinessPartner;
import com.greenhouse.backend.sales.domain.partner.PartnerSettlementSettings;
import com.greenhouse.backend.sales.domain.partner.SettlementUnit;
import com.greenhouse.backend.sales.domain.payment.PartnerBalanceSummary;
import com.greenhouse.backend.sales.domain.payment.PartnerPaymentEvent;
import com.greenhouse.backend.sales.domain.payment.PaymentEventType;
import com.greenhouse.backend.sales.domain.payment.PaymentTargetType;
import com.greenhouse.backend.sales.dto.auction.AuctionProceedsResponse;
import com.greenhouse.backend.sales.dto.document.SalesSlipStatusUpdateRequest;
import com.greenhouse.backend.sales.dto.partner.PartnerSettlementSettingsResponse;
import com.greenhouse.backend.sales.repository.auction.AuctionProceedsRepository;
import com.greenhouse.backend.sales.repository.auction.AuctionShipmentRepository;
import com.greenhouse.backend.sales.repository.document.SalesSlipRepository;
import com.greenhouse.backend.sales.repository.partner.BusinessPartnerRepository;
import com.greenhouse.backend.sales.repository.partner.PartnerSettlementSettingsRepository;
import com.greenhouse.backend.sales.repository.payment.PartnerBalanceSummaryRepository;
import com.greenhouse.backend.sales.repository.payment.PartnerPaymentEventRepository;
import com.greenhouse.backend.support.DirectSaleFixtures;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("work-e2e")
@Timeout(60)
class PartnerSettlementPostgresE2ETest extends WorkE2ETestBase {

  @org.springframework.beans.factory.annotation.Autowired
  private AuctionProceedsPaymentService auctionPayments;

  @Autowired BusinessPartnerRepository partnerRepository;

  @Autowired AuctionShipmentCreator shipmentCreator;

  @Autowired SalesSlipStatusService salesStatusService;

  @Autowired BusinessPartnerLock partnerLock;

  @Autowired PartnerBalanceService balanceService;

  @Autowired PartnerBalanceSummaryRepository balanceRepository;

  @Autowired PartnerSettlementSettingsRepository settingsRepository;

  @Autowired PartnerSettlementSettingsService settingsService;

  @Autowired PartnerPaymentEventRepository eventRepository;

  @Autowired SalesSlipRepository salesSlipRepository;

  @Autowired SalesPaymentService salesPaymentService;

  @Autowired AuctionShipmentRepository shipmentRepository;

  @Autowired AuctionProceedsRepository settlementRepository;

  @Autowired AuctionProceedsReader settlementService;
  @Autowired AuctionProceedsService proceedsService;

  @Autowired PaymentService paymentService;

  @Autowired AuditEventRepository auditRepository;

  @Autowired PlatformTransactionManager transactionManager;

  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  void holdsPartnerLocksUntilTheCallingTransactionCompletes() throws Exception {
    var partner = createPartner("잠금 유지");
    var locked = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var executor = Executors.newSingleThreadExecutor();
    try {
      var owner =
          executor.submit(
              () ->
                  transaction()
                      .executeWithoutResult(
                          status -> {
                            partnerLock.lockAll(List.of(partner.getId()));
                            locked.countDown();
                            await(release);
                          }));
      await(locked);

      assertThatThrownBy(
              () ->
                  transaction()
                      .executeWithoutResult(
                          status -> {
                            jdbcTemplate.execute("set local lock_timeout = '300ms'");
                            partnerLock.lockAll(List.of(partner.getId()));
                          }))
          .isInstanceOf(PessimisticLockingFailureException.class);

      release.countDown();
      owner.get(10, TimeUnit.SECONDS);
      var reacquired =
          transaction().execute(status -> partnerLock.lockAll(List.of(partner.getId())));
      assertThat(reacquired).extracting(BusinessPartnerInfo::id).containsExactly(partner.getId());
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void oppositeInputOrdersAcquireTheSameSortedLocks() throws Exception {
    var first = createPartner("잠금 순서 1");
    var second = createPartner("잠금 순서 2");

    List<List<BusinessPartnerInfo>> results =
        concurrently(
            List.of(
                () ->
                    transaction()
                        .execute(
                            status -> partnerLock.lockAll(List.of(first.getId(), second.getId()))),
                () ->
                    transaction()
                        .execute(
                            status ->
                                partnerLock.lockAll(List.of(second.getId(), first.getId())))));

    assertThat(results)
        .allSatisfy(
            partners ->
                assertThat(partners)
                    .extracting(BusinessPartnerInfo::id)
                    .containsExactly(first.getId(), second.getId()));
  }

  @Test
  void concurrentPaymentsAndReplaysKeepOneAccuratePartnerBalance() throws Exception {
    var partner = createPartner("동시 입금");
    var first = createSlip(partner, "S20400102-801");
    var second = createSlip(partner, "S20400102-802");
    var firstPayment = payment(20_000L, "first");
    var secondPayment = payment(30_000L, "second");

    List<SalesSlipDocument> paid =
        concurrently(
            List.of(
                () -> salesPaymentService.confirmPayment(first.getId(), firstPayment),
                () -> salesPaymentService.confirmPayment(second.getId(), secondPayment)));
    assertThat(paid).extracting(slip -> slip.paidAmount()).containsExactly(20_000L, 30_000L);
    assertThat(balanceService.getBalance(partner.getId()).receivableBalance()).isEqualTo(150_000L);

    concurrently(
        List.of(
            () -> salesPaymentService.confirmPayment(first.getId(), firstPayment),
            () -> salesPaymentService.confirmPayment(second.getId(), secondPayment)));
    assertThatThrownBy(
            () -> salesPaymentService.confirmPayment(first.getId(), payment(90_000L, "overpay")))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(balanceService.getBalance(partner.getId()).receivableBalance()).isEqualTo(150_000L);
    assertThat(balanceRepository.findByPartnerId(partner.getId())).isPresent();
    assertThat(
            eventRepository
                .search(partner.getId(), null, null, null, PageRequest.of(0, 100))
                .getContent())
        .extracting(event -> event.getEventType())
        .containsExactlyInAnyOrder(
            PaymentEventType.PAYMENT_RECEIVED,
            PaymentEventType.MANUAL_MATCH_CONFIRMED,
            PaymentEventType.PAYMENT_RECEIVED,
            PaymentEventType.MANUAL_MATCH_CONFIRMED);
  }

  @Test
  void concurrentFullPaymentRetriesRecordPaymentOnlyOnce() throws Exception {
    var partner = createPartner("완납 재요청");
    var slip = createSlip(partner, "S20400102-FULL-PAYMENT-RETRY");
    var payment = payment(100_000L, "full-payment");

    List<SalesSlipDocument> results =
        concurrently(
            List.of(
                () -> salesPaymentService.confirmPayment(slip.getId(), payment),
                () -> salesPaymentService.confirmPayment(slip.getId(), payment)));

    assertThat(results)
        .allSatisfy(
            result -> {
              assertThat(result.paidAmount()).isEqualTo(100_000L);
              assertThat(result.remainingAmount()).isZero();
              assertThat(result.paymentStatus()).isEqualTo("입금 완료");
            });
    assertThat(salesSlipRepository.findById(slip.getId()).orElseThrow().getPaidAmount())
        .isEqualTo(100_000L);
    assertThat(balanceService.getBalance(partner.getId()).receivableBalance()).isZero();
    assertThat(
            eventRepository
                .search(partner.getId(), null, null, null, PageRequest.of(0, 100))
                .getContent())
        .extracting(PartnerPaymentEvent::getEventType)
        .containsExactlyInAnyOrder(
            PaymentEventType.PAYMENT_RECEIVED, PaymentEventType.MANUAL_MATCH_CONFIRMED);
    assertThat(
            auditRepository.findAll().stream()
                .filter(
                    event ->
                        "SALES_SLIP".equals(event.getEntityType())
                            && slip.getId().equals(event.getEntityId())))
        .hasSize(1);
  }

  @Test
  void concurrentFirstReadsReturnTheSameDefaultSettings() throws Exception {
    var partner = createPartner("동시 기본 설정");
    var tasks = new ArrayList<Callable<PartnerSettlementSettingsResponse>>();
    for (int index = 0; index < 8; index++) {
      tasks.add(() -> settingsService.getOrCreate(partner.getId()));
    }

    var settings = concurrently(tasks);

    assertThat(settings)
        .extracting(PartnerSettlementSettingsResponse::id)
        .containsOnly(settings.getFirst().id());
    assertThat(settings)
        .allSatisfy(
            value -> {
              assertThat(value.partnerId()).isEqualTo(partner.getId());
              assertThat(value.settlementUnit()).isEqualTo(SettlementUnit.SALES_SLIP);
              assertThat(value.paymentDelayDays()).isZero();
            });
    assertThat(settingsRepository.findByPartnerIdIn(List.of(partner.getId()))).hasSize(1);
  }

  @Test
  void concurrentAuctionPaymentsAndReplaysKeepOneLedgerResultPerKey() throws Exception {
    long originalEventCount = eventRepository.count();
    var house =
        partnerRepository.saveAndFlush(
            new BusinessPartner("동시 경매 입금", PartnerType.AUCTION_HOUSE, null, null, null, null));
    var date = LocalDate.of(2040, 1, 3);
    var settlement = createAuctionProceeds(house, date);
    var first = payment(20_000L, "auction-first");
    var second = payment(30_000L, "auction-second");

    concurrently(
        List.of(
            () -> auctionPayments.confirm(settlement.id(), first),
            () -> auctionPayments.confirm(settlement.id(), second)));
    concurrently(
        List.of(
            () -> auctionPayments.confirm(settlement.id(), first),
            () -> auctionPayments.confirm(settlement.id(), second)));

    assertThat(settlementService.get(settlement.id()).paidAmount()).isEqualByComparingTo("50000");
    assertThat(settlementService.get(settlement.id()).remainingAmount())
        .isEqualByComparingTo("50000");
    var page = settlementService.page(house.getId(), 0, 1);
    assertThat(page.totalElements()).isEqualTo(1);
    assertThat(page.content())
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.id()).isEqualTo(settlement.id());
              assertThat(row.remainingAmount()).isEqualByComparingTo("50000");
            });
    assertThat(
            eventRepository
                .search(
                    house.getId(),
                    PaymentTargetType.AUCTION_PROCEEDS,
                    settlement.id(),
                    null,
                    PageRequest.of(0, 100))
                .getContent())
        .hasSize(4);
    var history =
        paymentService.getEventPage(
            house.getId(),
            PaymentTargetType.AUCTION_PROCEEDS,
            settlement.id(),
            PaymentEventType.PAYMENT_RECEIVED,
            0,
            1);
    assertThat(history.totalElements()).isEqualTo(2);
    assertThat(history.content())
        .singleElement()
        .satisfies(
            event -> assertThat(event.eventType()).isEqualTo(PaymentEventType.PAYMENT_RECEIVED));
    assertThat(paymentService.getEventPage(null, null, null, null, 0, 100).totalElements())
        .isEqualTo(originalEventCount + 4);
    assertThat(paymentService.getEventPage(-1L, null, null, null, 0, 100).totalElements()).isZero();
    assertThat(balanceService.getBalance(house.getId()).receivableBalance()).isZero();
  }

  @Test
  void callerFailureRollsBackTheParticipatingSalesPaymentAndAllLedgerEffects() {
    var partner = createPartner("입금 전체 rollback");
    var slip = createSlip(partner, "S20400102-803");
    assertThatThrownBy(
            () ->
                transaction()
                    .executeWithoutResult(
                        status -> {
                          salesPaymentService.confirmPayment(
                              slip.getId(), payment(20_000L, "rollback"));
                          salesSlipRepository.flush();
                          throw new IllegalStateException("후속 처리 실패");
                        }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("후속 처리 실패");

    assertThat(salesSlipRepository.findById(slip.getId()).orElseThrow().getPaidAmount()).isZero();
    assertThat(
            eventRepository
                .search(partner.getId(), null, null, null, PageRequest.of(0, 100))
                .getContent())
        .isEmpty();
    assertThat(balanceRepository.findByPartnerId(partner.getId())).isEmpty();
    assertThat(
            auditRepository.findAll().stream()
                .filter(event -> event.getEntityType().equals("SALES_SLIP"))
                .filter(event -> event.getEntityId().equals(slip.getId())))
        .isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"SALES_SLIP", "PAYMENT_EVENT"})
  void eitherPaymentAuditFailureRollsBackTheEntirePaymentAndAllowsSameKeyRetry(String entityType) {
    var partner = createPartner("감사 실패 " + entityType);
    var slip = createSlip(partner, "PAY-AUDIT-" + partner.getId());
    var payment = payment(20_000L, "audit-failure");
    var before =
        PostgresWriteTestSupport.snapshot(
            jdbcTemplate,
            transactionManager,
            List.of(
                "sales_slips",
                "partner_payment_events",
                "partner_balance_summaries",
                "audit_events"));
    jdbcTemplate.execute(
        "ALTER TABLE audit_events ADD CONSTRAINT test_payment_audit CHECK (entity_type <> '"
            + entityType
            + "' OR context_data ->> 'partnerId' <> '"
            + partner.getId()
            + "')");
    try {
      PostgresWriteTestSupport.assertStandaloneCheckFailure(
          () -> salesPaymentService.confirmPayment(slip.getId(), payment), "test_payment_audit");
    } finally {
      jdbcTemplate.execute("ALTER TABLE audit_events DROP CONSTRAINT test_payment_audit");
    }
    assertThat(
            PostgresWriteTestSupport.snapshot(
                jdbcTemplate,
                transactionManager,
                List.of(
                    "sales_slips",
                    "partner_payment_events",
                    "partner_balance_summaries",
                    "audit_events")))
        .isEqualTo(before);
    var retry = salesPaymentService.confirmPayment(slip.getId(), payment);
    assertThat(retry.paidAmount()).isEqualTo(20_000L);
    assertThat(retry.remainingAmount()).isEqualTo(80_000L);
    assertThat(balanceService.getBalance(partner.getId()).receivableBalance()).isEqualTo(80_000L);
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT entity_type FROM audit_events WHERE context_data ->> 'partnerId' = ? ORDER BY id",
                String.class,
                partner.getId().toString()))
        .containsExactly("PAYMENT_EVENT", "SALES_SLIP");
    assertThat(
            eventRepository
                .search(partner.getId(), null, null, null, PageRequest.of(0, 100))
                .getContent())
        .extracting(PartnerPaymentEvent::getEventType)
        .containsExactly(
            PaymentEventType.MANUAL_MATCH_CONFIRMED, PaymentEventType.PAYMENT_RECEIVED);
    var after = paymentState(partner.getId(), slip.getId());
    salesPaymentService.confirmPayment(slip.getId(), payment);
    assertThat(paymentState(partner.getId(), slip.getId())).isEqualTo(after);
  }

  @ParameterizedTest
  @CsvSource({
    "SALES_SLIP, false", "SALES_SLIP, true",
    "AUCTION_PROCEEDS, false", "AUCTION_PROCEEDS, true"
  })
  void changedAmountOrDateReturnsAConflictWithoutChangingTheFullyPaidTarget(
      PaymentTargetType targetType, boolean changeDate) throws Exception {
    var target = createPaymentTarget(targetType);
    var original = payment(100_000L, "be026-conflict");
    String json = objectMapper.writeValueAsString(original);
    var first = post(target.path(), json);
    assertThat(first.status()).as(first.body().toString()).isEqualTo(200);
    assertThat(first.data().path("paidAmount").asLong()).isEqualTo(100_000L);
    var before = paymentState(target.partnerId(), target.id(), targetType);

    var changed =
        new ManualPaymentCommand(
            changeDate ? original.amount() : 90_000L,
            changeDate ? original.paymentDate().plusDays(1) : original.paymentDate(),
            " be026-conflict ",
            "다른 방법",
            "다른 입금자",
            "다른 작업자",
            "다른 메모");
    var conflict = post(target.path(), objectMapper.writeValueAsString(changed));
    assertThat(conflict.status()).as(conflict.body().toString()).isEqualTo(409);
    assertThat(conflict.body().path("error").path("code").asText())
        .isEqualTo("IDEMPOTENCY_KEY_REUSED");
    assertThat(paymentState(target.partnerId(), target.id(), targetType)).isEqualTo(before);
    var replay = post(target.path(), json);
    assertThat(replay.status()).as(replay.body().toString()).isEqualTo(200);
    assertThat(replay.data().path("paidAmount").asLong()).isEqualTo(100_000L);
    assertThat(paymentState(target.partnerId(), target.id(), targetType)).isEqualTo(before);
    var metadataOnly =
        new ManualPaymentCommand(
            original.amount(),
            original.paymentDate(),
            " be026-conflict ",
            "다른 방법",
            "다른 입금자",
            "다른 작업자",
            "다른 메모");
    var metadataReplay = post(target.path(), objectMapper.writeValueAsString(metadataOnly));
    assertThat(metadataReplay.status()).as(metadataReplay.body().toString()).isEqualTo(200);
    assertThat(paymentState(target.partnerId(), target.id(), targetType)).isEqualTo(before);
  }

  @ParameterizedTest
  @EnumSource(
      value = PaymentTargetType.class,
      names = {"SALES_SLIP", "AUCTION_PROCEEDS"})
  void concurrentDifferentAmountsWithTheSameKeyCommitOnePaymentAndReturnOneConflict(
      PaymentTargetType targetType) throws Exception {
    var target = createPaymentTarget(targetType);
    String first = objectMapper.writeValueAsString(payment(20_000L, "be026-race"));
    String second = objectMapper.writeValueAsString(payment(30_000L, "be026-race"));
    List<ApiResult> outcomes =
        concurrently(List.of(() -> post(target.path(), first), () -> post(target.path(), second)));
    assertThat(outcomes).extracting(ApiResult::status).containsExactlyInAnyOrder(200, 409);
    var conflict =
        outcomes.stream().filter(result -> result.status() == 409).findFirst().orElseThrow();
    assertThat(conflict.body().path("error").path("code").asText())
        .isEqualTo("IDEMPOTENCY_KEY_REUSED");
    var accepted =
        outcomes.stream().filter(result -> result.status() == 200).findFirst().orElseThrow();
    long paidAmount = accepted.data().path("paidAmount").asLong();
    assertThat(paidAmount).isIn(20_000L, 30_000L);
    assertThat(accepted.data().path("remainingAmount").asLong()).isEqualTo(100_000L - paidAmount);
    assertThat(
            eventRepository
                .search(target.partnerId(), targetType, target.id(), null, PageRequest.of(0, 100))
                .getContent())
        .extracting(PartnerPaymentEvent::getEventType)
        .containsExactlyInAnyOrder(
            PaymentEventType.PAYMENT_RECEIVED, PaymentEventType.MANUAL_MATCH_CONFIRMED);
    var beforeReplay = paymentState(target.partnerId(), target.id(), targetType);
    var replay = post(target.path(), paidAmount == 20_000L ? first : second);
    assertThat(replay.status()).as(replay.body().toString()).isEqualTo(200);
    assertThat(paymentState(target.partnerId(), target.id(), targetType)).isEqualTo(beforeReplay);
  }

  private record PaymentTarget(Long partnerId, Long id, PaymentTargetType type) {
    String path() {
      String collection = type == PaymentTargetType.SALES_SLIP ? "sales-slips" : "auction-proceeds";
      return "/api/" + collection + "/" + id + "/confirm-payment";
    }
  }

  private PaymentTarget createPaymentTarget(PaymentTargetType type) {
    if (type == PaymentTargetType.SALES_SLIP) {
      var partner = createPartner("입금 충돌 판매 거래처");
      return new PaymentTarget(
          partner.getId(), createSlip(partner, "S20400102-" + partner.getId()).getId(), type);
    }
    var house =
        partnerRepository.saveAndFlush(
            new BusinessPartner("입금 충돌 경매장", PartnerType.AUCTION_HOUSE, null, null, null, null));
    var date = LocalDate.of(2040, 1, 3);
    return new PaymentTarget(house.getId(), createAuctionProceeds(house, date).id(), type);
  }

  private AuctionProceedsResponse createAuctionProceeds(BusinessPartner house, LocalDate date) {
    var shipment = new AuctionShipment(date.minusDays(1), house.getId(), house.getPartnerType());
    var lot = new AuctionShipmentLot("난", "카틀레야", "A", 1, 10);
    var attempt = new AuctionAttempt(date, 1, AuctionAttemptStatus.SOLD, null, null);
    attempt.addResultLine(
        new AuctionResultLine(
            date, "A", 10, 10_000, 100_000, null, AuctionInspectionStatus.NORMAL));
    lot.addAttempt(attempt);
    shipment.addLot(lot);
    shipmentRepository.saveAndFlush(shipment);
    Long id =
        proceedsService.record(
            house.getId(),
            "제공 지급 자료",
            100000L,
            100000L,
            List.of(attempt.getResultLines().getFirst().getId()));
    proceedsService.confirm(id, "확인자");
    return settlementService.get(id);
  }

  private Map<String, Object> paymentState(Long partnerId, Long slipId) {
    return paymentState(partnerId, slipId, PaymentTargetType.SALES_SLIP);
  }

  private Map<String, Object> paymentState(Long partnerId, Long targetId, PaymentTargetType type) {
    String table = type == PaymentTargetType.SALES_SLIP ? "sales_slips" : "auction_proceeds";
    return Map.of(
        "target",
            jdbcTemplate.queryForList(
                "SELECT row_to_json(snapshot)::text FROM (SELECT * FROM "
                    + table
                    + " WHERE id = ?) snapshot",
                String.class,
                targetId),
        "events",
            jdbcTemplate.queryForList(
                "SELECT row_to_json(snapshot)::text FROM (SELECT * FROM partner_payment_events WHERE partner_id = ? ORDER BY id) snapshot",
                String.class,
                partnerId),
        "balance",
            jdbcTemplate.queryForList(
                "SELECT row_to_json(snapshot)::text FROM (SELECT * FROM partner_balance_summaries WHERE partner_id = ?) snapshot",
                String.class,
                partnerId),
        "audit",
            jdbcTemplate.queryForList(
                "SELECT row_to_json(snapshot)::text FROM (SELECT * FROM audit_events WHERE context_data ->> 'partnerId' = ? ORDER BY id) snapshot",
                String.class,
                partnerId.toString()));
  }

  @Test
  void scalarPartnerReferencesRetainTheExistingForeignKeys() {
    assertThatThrownBy(() -> balanceRepository.saveAndFlush(new PartnerBalanceSummary(-1L)))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("partner_balance_summaries_partner_id_fkey");
    assertThatThrownBy(
            () ->
                settingsRepository.saveAndFlush(
                    new PartnerSettlementSettings(-1L, PartnerType.WHOLESALE)))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("partner_settlement_settings_partner_id_fkey");
    assertThatThrownBy(
            () ->
                eventRepository.saveAndFlush(
                    PartnerPaymentEvent.received(
                        -1L,
                        LocalDate.of(2040, 1, 2),
                        1_000L,
                        PaymentTargetType.SALES_SLIP,
                        1L,
                        null,
                        null,
                        "foreign-key",
                        null,
                        null)))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("partner_payment_events_partner_id_fkey");
    assertThatThrownBy(
            () ->
                settlementRepository.saveAndFlush(
                    new AuctionProceeds(-1L, "외래키", 1000L, 1000L, List.of(1L))))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("auction_proceeds_auction_house_id_fkey");
    assertThatThrownBy(
            () ->
                shipmentRepository.saveAndFlush(
                    new AuctionShipment(LocalDate.of(2040, 1, 2), -1L, PartnerType.AUCTION_HOUSE)))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("auction_shipments_auction_house_id_fkey");
    assertThatThrownBy(
            () ->
                salesSlipRepository.saveAndFlush(
                    new SalesSlip(
                        "INVALID-PARTNER",
                        LocalDate.of(2040, 1, 2),
                        SalesType.DIRECT,
                        null,
                        -1L,
                        "미입금",
                        "작성중",
                        null,
                        null)))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("sales_slips_partner_id_fkey");
  }

  @Test
  void shipmentCreationAndCancellationPreserveForeignKeysAndCallerRollback() {
    var house =
        partnerRepository.saveAndFlush(
            new BusinessPartner("출하 계약", PartnerType.AUCTION_HOUSE, null, null, null, null));
    var date = LocalDate.of(2040, 1, 2);
    var drafts = List.of(new LotDraft(20L, "난", "호접란", "A", 3));
    assertThatThrownBy(() -> shipmentCreator.create(date, house.getId(), drafts))
        .isInstanceOf(IllegalTransactionStateException.class);
    long before = shipmentRepository.count();
    assertThatThrownBy(
            () ->
                transaction()
                    .executeWithoutResult(
                        status -> {
                          shipmentCreator.create(date, house.getId(), drafts);
                          shipmentRepository.flush();
                          throw new IllegalStateException("후속 실패");
                        }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("후속 실패");
    assertThat(shipmentRepository.count()).isEqualTo(before);

    Long slipId =
        transaction()
            .execute(
                status -> {
                  var created = shipmentCreator.create(date, house.getId(), drafts);
                  var slip =
                      new SalesSlip(
                          "SHIPMENT-LINK",
                          date,
                          SalesType.AUCTION,
                          created.id(),
                          house.getId(),
                          "정산 대기",
                          "출하 완료",
                          null,
                          null);
                  slip.addItem(
                      new SalesSlipItem(
                          created.lotIdsBySourceItemId().get(20L), "호접란", "난", "A", 3, 0, null));
                  DirectSaleFixtures.refreshProjection(slip);
                  return salesSlipRepository.saveAndFlush(slip).getId();
                });
    assertThat(shipmentRepository.count()).isEqualTo(before + 1);
    var canceled =
        salesStatusService.updateStatus(slipId, new SalesSlipStatusUpdateRequest("취소", null));
    assertThat(canceled.auctionShipmentId()).isNull();
    assertThat(canceled.items().getFirst().auctionShipmentLotId()).isNull();
    assertThat(shipmentRepository.count()).isEqualTo(before);
    assertThat(salesSlipRepository.findById(slipId).orElseThrow().isCanceled()).isTrue();
  }

  private BusinessPartner createPartner(String name) {
    return partnerRepository.saveAndFlush(
        new BusinessPartner(name, PartnerType.WHOLESALE, null, null, null, null));
  }

  private SalesSlip createSlip(BusinessPartner partner, String number) {
    var slip =
        new SalesSlip(
            number,
            LocalDate.of(2040, 1, 2),
            SalesType.DIRECT,
            null,
            partner.getId(),
            "미입금",
            "작성중",
            "계좌이체",
            null);
    slip.addItem(new SalesSlipItem(null, "카틀레야", null, "A", 10, 10_000, null));
    DirectSaleFixtures.refreshProjection(slip);
    var saved = salesSlipRepository.saveAndFlush(slip);
    DirectSaleFixtures.copyTerms(jdbcTemplate, saved.getId());
    return saved;
  }

  private ManualPaymentCommand payment(long amount, String key) {
    return new ManualPaymentCommand(
        amount, LocalDate.of(2040, 1, 2), key, "계좌이체", null, "테스트", null);
  }

  private TransactionTemplate transaction() {
    return new TransactionTemplate(transactionManager);
  }

  private <T> List<T> concurrently(List<Callable<T>> tasks) throws Exception {
    var ready = new CountDownLatch(tasks.size());
    var start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(tasks.size());
    try {
      var futures =
          tasks.stream()
              .map(
                  task ->
                      executor.submit(
                          () -> {
                            ready.countDown();
                            await(start);
                            return task.call();
                          }))
              .toList();
      await(ready);
      start.countDown();
      var results = new ArrayList<T>();
      for (var future : futures) {
        results.add(future.get(20, TimeUnit.SECONDS));
      }
      return results;
    } finally {
      start.countDown();
      executor.shutdownNow();
    }
  }

  private void await(CountDownLatch latch) {
    try {
      assertThat(latch.await(10, TimeUnit.SECONDS)).as("Concurrent request coordination").isTrue();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AssertionError(exception);
    }
  }
}
