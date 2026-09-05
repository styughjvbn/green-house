package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.greenhouse.backend.settlement.domain.PartnerBalanceSummary;
import com.greenhouse.backend.settlement.domain.PartnerSettlementSettings;
import com.greenhouse.backend.settlement.domain.PaymentEventType;
import com.greenhouse.backend.settlement.dto.ManualPaymentRequest;
import com.greenhouse.backend.settlement.repository.PartnerBalanceSummaryRepository;
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
	@Autowired BusinessPartnerLock partnerLock;
	@Autowired PartnerBalanceService balanceService;
	@Autowired PartnerBalanceSummaryRepository balanceRepository;
	@Autowired PartnerSettlementSettingsRepository settingsRepository;
	@Autowired PartnerPaymentEventRepository eventRepository;
	@Autowired SalesSlipRepository salesSlipRepository;
	@Autowired SalesPaymentService salesPaymentService;
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
	void scalarPartnerReferencesRetainTheExistingForeignKeys() {
		assertThatThrownBy(() -> balanceRepository.saveAndFlush(new PartnerBalanceSummary(-1L)))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("partner_balance_summaries_partner_id_fkey");
		assertThatThrownBy(() -> settingsRepository.saveAndFlush(
				new PartnerSettlementSettings(-1L, PartnerType.WHOLESALE)))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("partner_settlement_settings_partner_id_fkey");
	}

	private BusinessPartner createPartner(String name) {
		return partnerRepository.saveAndFlush(
				new BusinessPartner(name, PartnerType.WHOLESALE, null, null, null, null));
	}

	private SalesSlip createSlip(BusinessPartner partner, String number) {
		var slip = new SalesSlip(number, LocalDate.of(2040, 1, 2), SalesType.DIRECT, null,
				partner, "미입금", "작성중", "계좌이체", null);
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
