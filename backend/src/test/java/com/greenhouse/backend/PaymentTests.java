package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.greenhouse.backend.auction.domain.AuctionAttempt;
import com.greenhouse.backend.auction.domain.AuctionAttemptStatus;
import com.greenhouse.backend.auction.domain.AuctionInspectionStatus;
import com.greenhouse.backend.auction.domain.AuctionResultLine;
import com.greenhouse.backend.auction.domain.AuctionShipment;
import com.greenhouse.backend.auction.domain.AuctionShipmentLot;
import com.greenhouse.backend.auction.repository.AuctionShipmentRepository;
import com.greenhouse.backend.audit.domain.AuditAction;
import com.greenhouse.backend.audit.domain.AuditSource;
import com.greenhouse.backend.audit.repository.AuditEventRepository;
import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.partner.domain.PartnerType;
import com.greenhouse.backend.partner.repository.BusinessPartnerRepository;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesSlipItem;
import com.greenhouse.backend.sales.domain.SalesType;
import com.greenhouse.backend.sales.repository.SalesSlipRepository;
import com.greenhouse.backend.settlement.application.AuctionSettlementService;
import com.greenhouse.backend.settlement.domain.PartnerPaymentEvent;
import com.greenhouse.backend.settlement.domain.PaymentEventType;
import com.greenhouse.backend.settlement.domain.PaymentTargetType;
import com.greenhouse.backend.settlement.repository.PartnerPaymentEventRepository;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PaymentTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	BusinessPartnerRepository partnerRepository;

	@Autowired
	SalesSlipRepository salesSlipRepository;

	@Autowired
	AuctionShipmentRepository shipmentRepository;

	@Autowired
	AuctionSettlementService settlementService;

	@Autowired
	PartnerPaymentEventRepository eventRepository;

	@Autowired
	AuditEventRepository auditEventRepository;

	@ParameterizedTest
	@MethodSource("invalidPaymentFields")
	void validatesManualPaymentFieldsBeforeLoadingEitherTarget(String field, Object invalidValue) throws Exception {
		var payload = new LinkedHashMap<String, Object>(
				Map.of("amount", 1L, "paymentDate", "2026-07-06", "idempotencyKey", "validation"));
		if (field == null) {
			payload.clear();
		}
		else {
			payload.put(field, invalidValue);
		}
		String json = JsonMapper.builder().build().writeValueAsString(payload);
		for (String target : List.of("sales-slips", "auction-settlements")) {
			mockMvc
				.perform(post("/api/{target}/-1/confirm-payment", target).contentType("application/json").content(json))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
		}
	}

	static Stream<Arguments> invalidPaymentFields() {
		return Stream.of(Arguments.of(null, null), Arguments.of("amount", null), Arguments.of("amount", 0),
				Arguments.of("amount", -1), Arguments.of("paymentDate", null), Arguments.of("idempotencyKey", null),
				Arguments.of("idempotencyKey", ""), Arguments.of("idempotencyKey", " "),
				Arguments.of("idempotencyKey", "k".repeat(101)), Arguments.of("paymentMethod", "m".repeat(31)),
				Arguments.of("depositorName", "d".repeat(101)), Arguments.of("worker", "w".repeat(101)),
				Arguments.of("memo", "m".repeat(1001)));
	}

	@ParameterizedTest
	@ValueSource(strings = { "sales-slips", "auction-settlements" })
	void acceptsFieldLengthLimitsAndAbsentOptionalPaymentFields(String target) throws Exception {
		var payload = new LinkedHashMap<String, Object>(Map.of("amount", Long.MAX_VALUE, "paymentDate", "2026-07-06",
				"idempotencyKey", "k".repeat(100), "paymentMethod", "m".repeat(30), "depositorName", "d".repeat(100),
				"worker", "w".repeat(100), "memo", "m".repeat(1000)));
		var mapper = JsonMapper.builder().build();
		mockMvc
			.perform(post("/api/{target}/-1/confirm-payment", target).contentType("application/json")
				.content(mapper.writeValueAsString(payload)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

		payload.keySet().retainAll(List.of("amount", "paymentDate", "idempotencyKey"));
		mockMvc
			.perform(post("/api/{target}/-1/confirm-payment", target).contentType("application/json")
				.content(mapper.writeValueAsString(payload)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

		payload.put("paymentDate", "invalid-date");
		mockMvc
			.perform(post("/api/{target}/-1/confirm-payment", target).contentType("application/json")
				.content(mapper.writeValueAsString(payload)))
			.andExpect(status().isBadRequest());
	}

	@Test
	void confirmsPartialAndFullSalesSlipPayments() throws Exception {
		var partner = partnerRepository
			.saveAndFlush(new BusinessPartner("직거래 화원", PartnerType.WHOLESALE, null, null, null, null));
		var slip = new SalesSlip("S20260706-900", LocalDate.of(2026, 7, 6), SalesType.DIRECT, null, partner.getId(),
				"미입금", "작성중", "계좌이체", null);
		slip.addItem(new SalesSlipItem(null, "카틀레야", null, "A", 10, 10_000, null));
		slip = salesSlipRepository.saveAndFlush(slip);

		mockMvc.perform(get("/api/sales-slips/{id}", slip.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.availableActions",
					containsInAnyOrder("EDIT", "COMPLETE", "CANCEL", "CONFIRM_PAYMENT")));

		mockMvc
			.perform(post("/api/sales-slips/{id}/confirm-payment", slip.getId()).contentType("application/json")
				.content(paymentJson(30_000)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.paidAmount").value(30_000))
			.andExpect(jsonPath("$.data.remainingAmount").value(70_000))
			.andExpect(jsonPath("$.data.paymentStatus").value("부분입금"))
			.andExpect(jsonPath("$.data.availableActions", containsInAnyOrder("COMPLETE", "CONFIRM_PAYMENT")));

		mockMvc
			.perform(post("/api/sales-slips/{id}/confirm-payment", slip.getId()).contentType("application/json")
				.content(paymentJson(30_000)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.paidAmount").value(30_000))
			.andExpect(jsonPath("$.data.remainingAmount").value(70_000));

		mockMvc
			.perform(post("/api/sales-slips/{id}/confirm-payment", slip.getId()).contentType("application/json")
				.content(paymentJson(70_000)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.paidAmount").value(100_000))
			.andExpect(jsonPath("$.data.remainingAmount").value(0))
			.andExpect(jsonPath("$.data.paymentStatus").value("입금 완료"))
			.andExpect(jsonPath("$.data.availableActions[0]").value("COMPLETE"))
			.andExpect(jsonPath("$.data.availableActions.length()").value(1));

		mockMvc
			.perform(post("/api/sales-slips/{id}/confirm-payment", slip.getId()).contentType("application/json")
				.content(paymentJson(70_000)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.paidAmount").value(100_000))
			.andExpect(jsonPath("$.data.remainingAmount").value(0));

		for (String rejectedPayment : List.of(paymentJson(70_000).replace("\"amount\": 70000", "\"amount\": 60000"),
				paymentJson(70_000).replace("2026-07-06", "2026-07-07"), paymentJson(1))) {
			mockMvc
				.perform(post("/api/sales-slips/{id}/confirm-payment", slip.getId()).contentType("application/json")
					.content(rejectedPayment))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
		}

		mockMvc.perform(get("/api/partner-payment-events").param("partnerId", partner.getId().toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(4));
		assertThat(eventRepository.findAll()).extracting(event -> event.getEventType())
			.containsExactlyInAnyOrder(PaymentEventType.PAYMENT_RECEIVED, PaymentEventType.MANUAL_MATCH_CONFIRMED,
					PaymentEventType.PAYMENT_RECEIVED, PaymentEventType.MANUAL_MATCH_CONFIRMED);
		var audits = auditEventRepository.findAll()
			.stream()
			.filter(event -> event.getSource() == AuditSource.SETTLEMENT_MANAGEMENT)
			.toList();
		assertThat(audits).hasSize(4);
		assertThat(audits).extracting(event -> event.getEntityType())
			.containsExactly("PAYMENT_EVENT", "SALES_SLIP", "PAYMENT_EVENT", "SALES_SLIP");
		assertThat(audits).extracting(event -> event.getAction())
			.containsExactly(AuditAction.CREATED, AuditAction.UPDATED, AuditAction.CREATED, AuditAction.UPDATED);
		assertThat(audits.stream().filter(event -> event.getEntityType().equals("PAYMENT_EVENT")))
			.allSatisfy(event -> assertThat(event.getAfterData().toString()).doesNotContain("테스트 입금자", "수동 확인"));

		mockMvc.perform(get("/api/business-partners/{id}/balance-summary", partner.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.receivableBalance").value(0));
	}

	@ParameterizedTest
	@CsvSource({ "DIRECT, 취소", "AUCTION, 작성중", "AUCTION, 출하 완료" })
	void rejectsPaymentToCanceledOrAuctionSalesSlip(SalesType salesType, String salesStatus) throws Exception {
		var partner = partnerRepository
			.saveAndFlush(new BusinessPartner("입금 거절 거래처", PartnerType.AUCTION_HOUSE, null, null, null, null));
		var slip = new SalesSlip("REJECT-PAYMENT", LocalDate.of(2026, 7, 6), salesType, null, partner.getId(), "미입금",
				SalesSlip.STATUS_DRAFT, null, null);
		slip.addItem(new SalesSlipItem(null, "카틀레야", null, "A", 10, 10_000, null));
		slip.updateSalesStatus(salesStatus);
		salesSlipRepository.saveAndFlush(slip);

		mockMvc.perform(get("/api/sales-slips/{id}", slip.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.availableActions", not(hasItem("CONFIRM_PAYMENT"))));
		mockMvc
			.perform(post("/api/sales-slips/{id}/confirm-payment", slip.getId()).contentType("application/json")
				.content(paymentJson(30_000)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

		assertThat(slip.getPaidAmount()).isZero();
		assertThat(slip.getRemainingAmount()).isEqualTo(100_000L);
		assertThat(eventRepository.count()).isZero();
		assertThat(auditEventRepository.count()).isZero();
	}

	@Test
	void confirmsAuctionPaymentAndRejectsOverpayment() throws Exception {
		var auctionHouse = partnerRepository
			.saveAndFlush(new BusinessPartner("입금 경매장", PartnerType.AUCTION_HOUSE, null, null, null, null));
		LocalDate auctionDate = LocalDate.of(2026, 7, 6);
		var shipment = new AuctionShipment(LocalDate.of(2026, 7, 5), auctionHouse.getId(),
				auctionHouse.getPartnerType());
		var lot = new AuctionShipmentLot("난", "덴드로비움", "A", 1, 10);
		var attempt = new AuctionAttempt(auctionDate, 1, AuctionAttemptStatus.SOLD, null, null);
		attempt.addResultLine(
				new AuctionResultLine(auctionDate, "A", 10, 10_000, 100_000, null, AuctionInspectionStatus.NORMAL));
		lot.addAttempt(attempt);
		shipment.addLot(lot);
		shipmentRepository.saveAndFlush(shipment);
		var settlement = settlementService.rebuild(auctionHouse.getId(), auctionDate);

		mockMvc
			.perform(post("/api/auction-settlements/{id}/confirm-payment", settlement.id())
				.contentType("application/json")
				.content(paymentJson(40_000)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.paidAmount").value(40_000))
			.andExpect(jsonPath("$.data.remainingAmount").value(60_000))
			.andExpect(jsonPath("$.data.status").value("PARTIALLY_PAID"));

		mockMvc
			.perform(post("/api/auction-settlements/{id}/confirm-payment", settlement.id())
				.contentType("application/json")
				.content(paymentJson(70_000)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").exists());

		var audits = auditEventRepository.findAll()
			.stream()
			.filter(event -> event.getSource() == AuditSource.SETTLEMENT_MANAGEMENT)
			.toList();
		assertThat(audits).hasSize(2);
		assertThat(audits).extracting(event -> event.getEntityType())
			.containsExactly("PAYMENT_EVENT", "AUCTION_SETTLEMENT");
		assertThat(audits.getLast().getChangedFields()).containsExactly("paidAmount", "remainingAmount",
				"paymentStatus");
	}

	@Test
	void filtersBeforePagingAndPreservesOrderAndParentIdentifiers() throws Exception {
		var partner = partnerRepository
			.saveAndFlush(new BusinessPartner("입금 이력", PartnerType.AUCTION_HOUSE, null, null, null, null));
		var other = partnerRepository
			.saveAndFlush(new BusinessPartner("다른 거래처", PartnerType.WHOLESALE, null, null, null, null));
		var date = LocalDate.of(2046, 1, 1);
		var oldest = createPayment(partner, PaymentTargetType.AUCTION_SETTLEMENT, 88L, date, 100L);
		var firstOnLatestDate = createPayment(partner, PaymentTargetType.AUCTION_SETTLEMENT, 88L, date.plusDays(1),
				200L);
		var latest = createPayment(partner, PaymentTargetType.AUCTION_SETTLEMENT, 88L, date.plusDays(1), 300L);
		createPayment(other, PaymentTargetType.AUCTION_SETTLEMENT, 88L, date.plusDays(1), 400L);
		createPayment(partner, PaymentTargetType.SALES_SLIP, 88L, date.plusDays(1), 500L);
		createPayment(partner, PaymentTargetType.AUCTION_SETTLEMENT, 89L, date.plusDays(1), 600L);

		mockMvc
			.perform(get("/api/partner-payment-events/page").param("partnerId", partner.getId().toString())
				.param("targetType", "AUCTION_SETTLEMENT")
				.param("targetId", "88")
				.param("eventType", "PAYMENT_RECEIVED")
				.param("size", "2"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.totalElements").value(3))
			.andExpect(jsonPath("$.data.totalPages").value(2))
			.andExpect(jsonPath("$.data.content.length()").value(2))
			.andExpect(jsonPath("$.data.content[0].id").value(latest.getId()))
			.andExpect(jsonPath("$.data.content[1].id").value(firstOnLatestDate.getId()));
		mockMvc
			.perform(get("/api/partner-payment-events/page").param("partnerId", partner.getId().toString())
				.param("targetType", "AUCTION_SETTLEMENT")
				.param("targetId", "88")
				.param("eventType", "PAYMENT_RECEIVED")
				.param("size", "2")
				.param("page", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.totalElements").value(3))
			.andExpect(jsonPath("$.data.content.length()").value(1))
			.andExpect(jsonPath("$.data.content[0].id").value(oldest.getId()));
		mockMvc
			.perform(get("/api/partner-payment-events/page").param("partnerId", partner.getId().toString())
				.param("targetType", "AUCTION_SETTLEMENT")
				.param("targetId", "88")
				.param("eventType", "MANUAL_MATCH_CONFIRMED"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.totalElements").value(3))
			.andExpect(jsonPath("$.data.content[0].parentEventId").value(latest.getId()));
		mockMvc.perform(get("/api/partner-payment-events/page"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.totalElements").value(12))
			.andExpect(jsonPath("$.data.size").value(10));
		mockMvc.perform(get("/api/partner-payment-events/page").param("page", "99").param("size", "2"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.content").isEmpty())
			.andExpect(jsonPath("$.data.totalElements").value(12));
		mockMvc.perform(get("/api/partner-payment-events/page").param("partnerId", "-1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.totalElements").value(0));
		mockMvc.perform(get("/api/partner-payment-events/page").param("page", "-1").param("size", "0"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.page").value(0))
			.andExpect(jsonPath("$.data.size").value(1));
		mockMvc.perform(get("/api/partner-payment-events/page").param("size", "101"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.size").value(100));
	}

	@Test
	void compatibilityLimitPreservesTheFullLedgerAndPagedHistory() throws Exception {
		var partner = partnerRepository
			.saveAndFlush(new BusinessPartner("누적 입금 이력", PartnerType.WHOLESALE, null, null, null, null));
		for (int index = 0; index < 251; index++) {
			createPayment(partner, PaymentTargetType.SALES_SLIP, 88L, LocalDate.of(2046, 1, 1).plusDays(index), 100L);
		}
		mockMvc.perform(get("/api/partner-payment-events").param("partnerId", partner.getId().toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(500))
			.andExpect(jsonPath("$.data[0].eventDate").value(LocalDate.of(2046, 1, 1).plusDays(250).toString()))
			.andExpect(jsonPath("$.data[499].eventDate").value("2046-01-02"));
		mockMvc
			.perform(get("/api/partner-payment-events/page").param("partnerId", partner.getId().toString())
				.param("eventType", "PAYMENT_RECEIVED")
				.param("page", "25"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.totalElements").value(251))
			.andExpect(jsonPath("$.data.content.length()").value(1))
			.andExpect(jsonPath("$.data.content[0].eventDate").value("2046-01-01"));
		assertThat(eventRepository.count()).isEqualTo(502);
	}

	private PartnerPaymentEvent createPayment(BusinessPartner partner, PaymentTargetType type, Long targetId,
			LocalDate date, long amount) {
		var received = eventRepository.saveAndFlush(PartnerPaymentEvent.received(partner.getId(), date, amount, type,
				targetId, "계좌이체", "입금자", null, null, "확인자"));
		eventRepository.saveAndFlush(PartnerPaymentEvent.manualMatch(received));
		return received;
	}

	private String paymentJson(long amount) {
		return """
				{
				  "amount": %d,
				  "paymentDate": "2026-07-06",
				  "idempotencyKey": "manual-payment-%d",
				  "paymentMethod": "계좌이체",
				  "depositorName": "테스트 입금자",
				  "worker": "관리자",
				  "memo": "수동 확인"
				}
				""".formatted(amount, amount);
	}

}
