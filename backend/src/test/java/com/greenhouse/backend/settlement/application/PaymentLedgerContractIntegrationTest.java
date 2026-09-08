package com.greenhouse.backend.settlement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.greenhouse.backend.audit.repository.AuditEventRepository;
import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.partner.domain.PartnerType;
import com.greenhouse.backend.partner.repository.BusinessPartnerRepository;
import com.greenhouse.backend.settlement.domain.PaymentEventType;
import com.greenhouse.backend.settlement.domain.PaymentTargetType;
import com.greenhouse.backend.settlement.repository.PartnerBalanceSummaryRepository;
import com.greenhouse.backend.settlement.repository.PartnerPaymentEventRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PaymentLedgerContractIntegrationTest {

	private static final LocalDate PAYMENT_DATE = LocalDate.of(2026, 9, 6);

	private static final Long TARGET_ID = 99123L;

	@Autowired
	PaymentLedgerService ledger;

	@Autowired
	PartnerBalanceService balanceService;

	@Autowired
	BusinessPartnerRepository partnerRepository;

	@Autowired
	PartnerPaymentEventRepository eventRepository;

	@Autowired
	PartnerBalanceSummaryRepository balanceRepository;

	@Autowired
	AuditEventRepository auditRepository;

	@Autowired
	EntityManager entityManager;

	@Autowired
	PlatformTransactionManager transactionManager;

	@Test
	void recordsTheReceiptMatchBalanceAndAuditWithoutExposingEntities() {
		var partner = createPartner();
		var receipt = ledger.recordManualPayment(partner.getId(), PaymentTargetType.SALES_SLIP, TARGET_ID,
				payment(1_000L, PAYMENT_DATE));
		balanceService.updateReceivable(partner.getId(), 2_000L, receipt);
		balanceService.updateReceivable(partner.getId(), 1_500L, null);
		entityManager.flush();
		entityManager.clear();

		var events = eventRepository
			.search(partner.getId(), PaymentTargetType.SALES_SLIP, TARGET_ID, null, PageRequest.of(0, 100))
			.getContent();
		assertThat(events).hasSize(2);
		assertThat(events.getFirst().getEventType()).isEqualTo(PaymentEventType.MANUAL_MATCH_CONFIRMED);
		assertThat(events.getFirst().getParentEvent().getId()).isEqualTo(receipt);
		assertThat(events).allSatisfy(event -> {
			assertThat(event.getPartnerId()).isEqualTo(partner.getId());
			assertThat(event.getAmount()).isEqualTo(1_000L);
			assertThat(event.getPaymentMethod()).isEqualTo("계좌이체");
			assertThat(event.getDepositorName()).isEqualTo("입금자");
			assertThat(event.getMemo()).isEqualTo("민감 메모");
		});
		assertThat(balanceService.getBalance(partner.getId()).receivableBalance()).isEqualTo(1_500L);
		var lastEventId = (Number) entityManager
			.createNativeQuery(
					"select last_payment_event_id from partner_balance_summaries where partner_id = :partnerId")
			.setParameter("partnerId", partner.getId())
			.getSingleResult();
		assertThat(lastEventId.longValue()).isEqualTo(receipt);
		assertThat(auditRepository.findAll()
			.stream()
			.filter(event -> event.getEntityType().equals("PAYMENT_EVENT"))
			.filter(event -> event.getEntityId().equals(receipt))).singleElement()
			.satisfies(event -> assertThat(event.getAfterData().toString()).doesNotContain("입금자", "민감 메모"));
	}

	@Test
	void replaysTheSameReceiptAndRejectsChangesToAmountOrDate() {
		var partner = createPartner();
		var command = payment(1_000L, PAYMENT_DATE);
		var receipt = ledger.recordManualPayment(partner.getId(), PaymentTargetType.SALES_SLIP, TARGET_ID, command);

		assertThat(ledger.findManualPayment(PaymentTargetType.SALES_SLIP, TARGET_ID, command)).contains(receipt);
		assertThatThrownBy(
				() -> ledger.findManualPayment(PaymentTargetType.SALES_SLIP, TARGET_ID, payment(2_000L, PAYMENT_DATE)))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> ledger.findManualPayment(PaymentTargetType.SALES_SLIP, TARGET_ID,
				payment(1_000L, PAYMENT_DATE.plusDays(1))))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(eventRepository.search(partner.getId(), null, null, null, PageRequest.of(0, 100)).getContent())
			.hasSize(2);
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void rejectsLedgerWritesWithoutAnOwningTransaction() {
		assertThatThrownBy(() -> ledger.recordManualPayment(-1L, PaymentTargetType.SALES_SLIP, TARGET_ID,
				payment(1_000L, PAYMENT_DATE)))
			.isInstanceOf(IllegalTransactionStateException.class);
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void rollsBackEventsBalanceAndAuditWithTheCallingUseCase() {
		var partner = createPartner();
		var receipt = new AtomicReference<Long>();
		assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			receipt.set(ledger.recordManualPayment(partner.getId(), PaymentTargetType.SALES_SLIP, TARGET_ID,
					payment(1_000L, PAYMENT_DATE)));
			balanceService.updateReceivable(partner.getId(), 2_000L, receipt.get());
			entityManager.flush();
			throw new IllegalStateException("후속 처리 실패");
		})).isInstanceOf(IllegalStateException.class).hasMessage("후속 처리 실패");

		assertThat(eventRepository.search(partner.getId(), null, null, null, PageRequest.of(0, 100)).getContent())
			.isEmpty();
		assertThat(balanceRepository.findByPartnerId(partner.getId())).isEmpty();
		assertThat(auditRepository.findAll()
			.stream()
			.filter(event -> event.getEntityType().equals("PAYMENT_EVENT"))
			.filter(event -> event.getEntityId().equals(receipt.get()))).isEmpty();
	}

	private BusinessPartner createPartner() {
		return partnerRepository
			.saveAndFlush(new BusinessPartner("입금 계약 거래처", PartnerType.WHOLESALE, null, null, null, null));
	}

	private ManualPaymentCommand payment(long amount, LocalDate date) {
		return new ManualPaymentCommand(amount, date, " contract-payment ", " 계좌이체 ", " 입금자 ", "작성자", " 민감 메모 ");
	}

}
