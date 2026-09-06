package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.greenhouse.backend.auction.domain.AuctionAttempt;
import com.greenhouse.backend.auction.application.AuctionShipmentCreator;
import com.greenhouse.backend.auction.application.AuctionShipmentCreator.LotDraft;
import com.greenhouse.backend.sales.application.SalesSlipStatusService;
import com.greenhouse.backend.sales.dto.SalesSlipStatusUpdateRequest;
import com.greenhouse.backend.auction.domain.AuctionAttemptStatus;
import com.greenhouse.backend.auction.domain.AuctionInspectionStatus;
import com.greenhouse.backend.auction.domain.AuctionResultLine;
import com.greenhouse.backend.auction.domain.AuctionShipment;
import com.greenhouse.backend.auction.domain.AuctionShipmentLot;
import com.greenhouse.backend.auction.repository.AuctionShipmentRepository;
import com.greenhouse.backend.audit.repository.AuditEventRepository;
import com.greenhouse.backend.partner.application.BusinessPartnerInfo;
import com.greenhouse.backend.partner.application.BusinessPartnerLock;
import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.partner.domain.PartnerType;
import com.greenhouse.backend.partner.repository.BusinessPartnerRepository;
import com.greenhouse.backend.sales.application.SalesPaymentService;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesSlipItem;
import com.greenhouse.backend.sales.domain.SalesType;
import com.greenhouse.backend.sales.dto.SalesSlipResponse;
import com.greenhouse.backend.sales.repository.SalesSlipRepository;
import com.greenhouse.backend.settlement.application.PartnerBalanceService;
import com.greenhouse.backend.settlement.application.PartnerSettlementSettingsService;
import com.greenhouse.backend.settlement.application.AuctionSettlementService;
import com.greenhouse.backend.settlement.application.PaymentService;
import com.greenhouse.backend.settlement.domain.AuctionSettlement;
import com.greenhouse.backend.settlement.domain.PartnerPaymentEvent;
import com.greenhouse.backend.settlement.domain.PartnerBalanceSummary;
import com.greenhouse.backend.settlement.domain.PartnerSettlementSettings;
import com.greenhouse.backend.settlement.domain.PaymentEventType;
import com.greenhouse.backend.settlement.domain.PaymentTargetType;
import com.greenhouse.backend.settlement.domain.SettlementUnit;
import com.greenhouse.backend.settlement.dto.ManualPaymentRequest;
import com.greenhouse.backend.settlement.dto.PartnerSettlementSettingsResponse;
import com.greenhouse.backend.settlement.repository.PartnerBalanceSummaryRepository;
import com.greenhouse.backend.settlement.repository.AuctionSettlementRepository;
import com.greenhouse.backend.settlement.repository.PartnerPaymentEventRepository;
import com.greenhouse.backend.settlement.repository.PartnerSettlementSettingsRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("work-e2e")
@Timeout(60)
class PartnerSettlementPostgresE2ETest extends WorkE2ETestBase {

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
	@Autowired AuctionSettlementRepository settlementRepository;
	@Autowired AuctionSettlementService settlementService;
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
			var owner = executor.submit(() -> transaction().executeWithoutResult(status -> {
				partnerLock.lockAll(List.of(partner.getId()));
				locked.countDown();
				await(release);
			}));
			await(locked);

			assertThatThrownBy(() -> transaction().executeWithoutResult(status -> {
				jdbcTemplate.execute("set local lock_timeout = '300ms'");
				partnerLock.lockAll(List.of(partner.getId()));
			})).isInstanceOf(PessimisticLockingFailureException.class);

			release.countDown();
			owner.get(10, TimeUnit.SECONDS);
			var reacquired = transaction().execute(status -> partnerLock.lockAll(List.of(partner.getId())));
			assertThat(reacquired)
					.extracting(BusinessPartnerInfo::id).containsExactly(partner.getId());
		} finally {
			release.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	void oppositeInputOrdersAcquireTheSameSortedLocks() throws Exception {
		var first = createPartner("잠금 순서 1");
		var second = createPartner("잠금 순서 2");

		List<List<BusinessPartnerInfo>> results = concurrently(List.of(
				() -> transaction().execute(status -> partnerLock.lockAll(List.of(first.getId(), second.getId()))),
				() -> transaction().execute(status -> partnerLock.lockAll(List.of(second.getId(), first.getId())))));

		assertThat(results).allSatisfy(partners -> assertThat(partners)
				.extracting(BusinessPartnerInfo::id).containsExactly(first.getId(), second.getId()));
	}

	@Test
	void concurrentPaymentsAndReplaysKeepOneAccuratePartnerBalance() throws Exception {
		var partner = createPartner("동시 입금");
		var first = createSlip(partner, "S20400102-801");
		var second = createSlip(partner, "S20400102-802");
		var firstPayment = payment(20_000L, "first");
		var secondPayment = payment(30_000L, "second");

		List<SalesSlipResponse> paid = concurrently(List.of(
				() -> salesPaymentService.confirmPayment(first.getId(), firstPayment),
				() -> salesPaymentService.confirmPayment(second.getId(), secondPayment)));
		assertThat(paid).extracting(slip -> slip.paidAmount()).containsExactly(20_000L, 30_000L);
		assertThat(balanceService.getBalance(partner.getId()).receivableBalance()).isEqualTo(150_000L);

		concurrently(List.of(
				() -> salesPaymentService.confirmPayment(first.getId(), firstPayment),
				() -> salesPaymentService.confirmPayment(second.getId(), secondPayment)));
		assertThatThrownBy(() -> salesPaymentService.confirmPayment(first.getId(), payment(90_000L, "overpay")))
				.isInstanceOf(IllegalArgumentException.class);

		assertThat(balanceService.getBalance(partner.getId()).receivableBalance()).isEqualTo(150_000L);
		assertThat(balanceRepository.findByPartnerId(partner.getId())).isPresent();
		assertThat(eventRepository.search(partner.getId(), null, null))
				.extracting(event -> event.getEventType()).containsExactlyInAnyOrder(
						PaymentEventType.PAYMENT_RECEIVED, PaymentEventType.MANUAL_MATCH_CONFIRMED,
						PaymentEventType.PAYMENT_RECEIVED, PaymentEventType.MANUAL_MATCH_CONFIRMED);
	}

	@Test
	void concurrentFirstReadsReturnTheSameDefaultSettings() throws Exception {
		var partner = createPartner("동시 기본 설정");
		var tasks = new ArrayList<Callable<PartnerSettlementSettingsResponse>>();
		for (int index = 0; index < 8; index++) {
			tasks.add(() -> settingsService.getOrCreate(partner.getId()));
		}

		var settings = concurrently(tasks);

		assertThat(settings).extracting(PartnerSettlementSettingsResponse::id)
				.containsOnly(settings.getFirst().id());
		assertThat(settings).allSatisfy(value -> {
			assertThat(value.partnerId()).isEqualTo(partner.getId());
			assertThat(value.settlementUnit()).isEqualTo(SettlementUnit.SALES_SLIP);
			assertThat(value.paymentDelayDays()).isZero();
		});
		assertThat(settingsRepository.findByPartnerIdIn(List.of(partner.getId()))).hasSize(1);
	}

	@Test
	void concurrentAuctionPaymentsAndReplaysKeepOneLedgerResultPerKey() throws Exception {
		var house = partnerRepository.saveAndFlush(new BusinessPartner(
				"동시 경매 입금", PartnerType.AUCTION_HOUSE, null, null, null, null));
		var date = LocalDate.of(2040, 1, 3);
		var shipment = new AuctionShipment(date.minusDays(1), house.getId(), house.getPartnerType());
		var lot = new AuctionShipmentLot("난", "카틀레야", "A", 1, 10);
		var attempt = new AuctionAttempt(date, 1, AuctionAttemptStatus.SOLD, null, null);
		attempt.addResultLine(new AuctionResultLine(date, "A", 10, 10_000, 100_000, null, AuctionInspectionStatus.NORMAL));
		lot.addAttempt(attempt);
		shipment.addLot(lot);
		shipmentRepository.saveAndFlush(shipment);
		var settlement = settlementService.rebuild(house.getId(), date);
		var first = payment(20_000L, "auction-first");
		var second = payment(30_000L, "auction-second");

		concurrently(List.of(
				() -> paymentService.confirmAuctionPayment(settlement.id(), first),
				() -> paymentService.confirmAuctionPayment(settlement.id(), second)));
		concurrently(List.of(
				() -> paymentService.confirmAuctionPayment(settlement.id(), first),
				() -> paymentService.confirmAuctionPayment(settlement.id(), second)));

		assertThat(settlementService.getSettlement(settlement.id()).paidAmount()).isEqualTo(50_000L);
		assertThat(settlementService.getSettlement(settlement.id()).remainingAmount()).isEqualTo(50_000L);
		var page = settlementService.getSettlementPage(house.getId(), date, date, null, 0, 1);
		assertThat(page.totalElements()).isEqualTo(1);
		assertThat(page.content()).singleElement().satisfies(row -> {
			assertThat(row.id()).isEqualTo(settlement.id());
			assertThat(row.remainingAmount()).isEqualTo(50_000L);
		});
		var totals = settlementService.getSummary(house.getId(), date, date, null);
		assertThat(totals.expectedDepositAmount()).isEqualTo(100_000L);
		assertThat(totals.remainingAmount()).isEqualTo(50_000L);
		assertThat(settlementService.getSummary(-1L, null, null, null).remainingAmount()).isZero();
		assertThat(eventRepository.search(house.getId(), PaymentTargetType.AUCTION_SETTLEMENT, settlement.id()))
				.hasSize(4);
		assertThat(balanceService.getBalance(house.getId()).receivableBalance()).isZero();
	}

	@Test
	void aLaterFailureRollsBackTheSalesPaymentAndAllLedgerEffects() {
		var partner = createPartner("입금 전체 rollback");
		var slip = createSlip(partner, "S20400102-803");
		assertThatThrownBy(() -> transaction().executeWithoutResult(status -> {
			salesPaymentService.confirmPayment(slip.getId(), payment(20_000L, "rollback"));
			salesSlipRepository.flush();
			throw new IllegalStateException("후속 처리 실패");
		})).isInstanceOf(IllegalStateException.class).hasMessage("후속 처리 실패");

		assertThat(salesSlipRepository.findById(slip.getId()).orElseThrow().getPaidAmount()).isZero();
		assertThat(eventRepository.search(partner.getId(), null, null)).isEmpty();
		assertThat(balanceRepository.findByPartnerId(partner.getId())).isEmpty();
		assertThat(auditRepository.findAll().stream()
				.filter(event -> event.getEntityType().equals("SALES_SLIP"))
				.filter(event -> event.getEntityId().equals(slip.getId()))).isEmpty();
	}

	@Test
	void scalarPartnerReferencesRetainTheExistingForeignKeys() {
		assertThatThrownBy(() -> balanceRepository.saveAndFlush(new PartnerBalanceSummary(-1L)))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("partner_balance_summaries_partner_id_fkey");
		assertThatThrownBy(() -> settingsRepository.saveAndFlush(
				new PartnerSettlementSettings(-1L, PartnerType.WHOLESALE)))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("partner_settlement_settings_partner_id_fkey");
		assertThatThrownBy(() -> eventRepository.saveAndFlush(PartnerPaymentEvent.received(
				-1L, LocalDate.of(2040, 1, 2), 1_000L, PaymentTargetType.SALES_SLIP, 1L,
				null, null, "foreign-key", null, null)))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("partner_payment_events_partner_id_fkey");
		assertThatThrownBy(() -> settlementRepository.saveAndFlush(
				new AuctionSettlement(-1L, LocalDate.of(2040, 1, 2))))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("auction_settlements_auction_house_id_fkey");
		assertThatThrownBy(() -> shipmentRepository.saveAndFlush(
				new AuctionShipment(LocalDate.of(2040, 1, 2), -1L, PartnerType.AUCTION_HOUSE)))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("auction_shipments_auction_house_id_fkey");
		assertThatThrownBy(() -> salesSlipRepository.saveAndFlush(new SalesSlip("INVALID-PARTNER",
				LocalDate.of(2040, 1, 2), SalesType.DIRECT, null, -1L, "미입금", "작성중", null, null)))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("sales_slips_partner_id_fkey");
	}

	@Test
	void shipmentCreationAndCancellationPreserveForeignKeysAndCallerRollback() {
		var house = partnerRepository.saveAndFlush(new BusinessPartner(
				"출하 계약", PartnerType.AUCTION_HOUSE, null, null, null, null));
		var date = LocalDate.of(2040, 1, 2);
		var drafts = List.of(new LotDraft(20L, "난", "호접란", "A", 3));
		assertThatThrownBy(() -> shipmentCreator.create(date, house.getId(), drafts))
				.isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
		long before = shipmentRepository.count();
		assertThatThrownBy(() -> transaction().executeWithoutResult(status -> {
			shipmentCreator.create(date, house.getId(), drafts);
			shipmentRepository.flush();
			throw new IllegalStateException("후속 실패");
		})).isInstanceOf(IllegalStateException.class).hasMessage("후속 실패");
		assertThat(shipmentRepository.count()).isEqualTo(before);

		Long slipId = transaction().execute(status -> {
			var created = shipmentCreator.create(date, house.getId(), drafts);
			var slip = new SalesSlip("SHIPMENT-LINK", date, SalesType.AUCTION, created.id(), house.getId(),
					"정산 대기", "출하 완료", null, null);
			slip.addItem(new SalesSlipItem(created.lotIdsBySourceItemId().get(20L), "호접란", "난", "A", 3, 0, null));
			return salesSlipRepository.saveAndFlush(slip).getId();
		});
		assertThat(shipmentRepository.count()).isEqualTo(before + 1);
		var canceled = salesStatusService.updateStatus(slipId, new SalesSlipStatusUpdateRequest("취소", null));
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
		var slip = new SalesSlip(number, LocalDate.of(2040, 1, 2), SalesType.DIRECT, null,
				partner.getId(), "미입금", "작성중", "계좌이체", null);
		slip.addItem(new SalesSlipItem(null, "카틀레야", null, "A", 10, 10_000, null));
		return salesSlipRepository.saveAndFlush(slip);
	}

	private ManualPaymentRequest payment(long amount, String key) {
		return new ManualPaymentRequest(amount, LocalDate.of(2040, 1, 2), key,
				"계좌이체", null, "테스트", null);
	}

	private TransactionTemplate transaction() {
		return new TransactionTemplate(transactionManager);
	}

	private <T> List<T> concurrently(List<Callable<T>> tasks) throws Exception {
		var ready = new CountDownLatch(tasks.size());
		var start = new CountDownLatch(1);
		var executor = Executors.newFixedThreadPool(tasks.size());
		try {
			var futures = tasks.stream().map(task -> executor.submit(() -> {
				ready.countDown();
				await(start);
				return task.call();
			})).toList();
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
