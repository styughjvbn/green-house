package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.partner.domain.PartnerType;
import com.greenhouse.backend.partner.repository.BusinessPartnerRepository;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesSlipItem;
import com.greenhouse.backend.sales.domain.SalesType;
import com.greenhouse.backend.sales.repository.SalesSlipRepository;
import com.greenhouse.backend.settlement.application.AuctionSettlementService;
import com.greenhouse.backend.settlement.domain.PaymentEventType;
import com.greenhouse.backend.settlement.repository.PartnerPaymentEventRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PaymentTests {
	@Autowired MockMvc mockMvc;
	@Autowired BusinessPartnerRepository partnerRepository;
	@Autowired SalesSlipRepository salesSlipRepository;
	@Autowired AuctionShipmentRepository shipmentRepository;
	@Autowired AuctionSettlementService settlementService;
	@Autowired PartnerPaymentEventRepository eventRepository;

	@Test
	void confirmsPartialAndFullSalesSlipPayments() throws Exception {
		var partner = partnerRepository.saveAndFlush(
			new BusinessPartner("직거래 화원", PartnerType.WHOLESALE, null, null, null, null));
		var slip = new SalesSlip(
			"S20260706-900", LocalDate.of(2026, 7, 6), SalesType.DIRECT, null, partner,
			"미입금", "작성중", "계좌이체", null);
		slip.addItem(new SalesSlipItem(null, "카틀레야", null, "A", 10, 10_000, null));
		slip = salesSlipRepository.saveAndFlush(slip);

		mockMvc.perform(post("/api/sales-slips/{id}/confirm-payment", slip.getId())
				.contentType("application/json")
				.content(paymentJson(30_000)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.paidAmount").value(30_000))
			.andExpect(jsonPath("$.data.remainingAmount").value(70_000))
			.andExpect(jsonPath("$.data.paymentStatus").value("부분입금"));

		mockMvc.perform(post("/api/sales-slips/{id}/confirm-payment", slip.getId())
				.contentType("application/json")
				.content(paymentJson(70_000)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.paidAmount").value(100_000))
			.andExpect(jsonPath("$.data.remainingAmount").value(0))
			.andExpect(jsonPath("$.data.paymentStatus").value("입금 완료"));

		mockMvc.perform(get("/api/partner-payment-events")
				.param("partnerId", partner.getId().toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(4));
		assertThat(eventRepository.findAll()).extracting(event -> event.getEventType())
			.containsExactlyInAnyOrder(
				PaymentEventType.PAYMENT_RECEIVED, PaymentEventType.MANUAL_MATCH_CONFIRMED,
				PaymentEventType.PAYMENT_RECEIVED, PaymentEventType.MANUAL_MATCH_CONFIRMED);

		mockMvc.perform(get("/api/business-partners/{id}/balance-summary", partner.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.receivableBalance").value(0));
	}

	@Test
	void confirmsAuctionPaymentAndRejectsOverpayment() throws Exception {
		var auctionHouse = partnerRepository.saveAndFlush(
			new BusinessPartner("입금 경매장", PartnerType.AUCTION_HOUSE, null, null, null, null));
		LocalDate auctionDate = LocalDate.of(2026, 7, 6);
		var shipment = new AuctionShipment(LocalDate.of(2026, 7, 5), auctionHouse);
		var lot = new AuctionShipmentLot("난", "덴드로비움", "A", 1, 10);
		var attempt = new AuctionAttempt(auctionDate, 1, AuctionAttemptStatus.SOLD, null, null);
		attempt.addResultLine(new AuctionResultLine(
			auctionDate, "A", 10, 10_000, 100_000, null, AuctionInspectionStatus.NORMAL));
		lot.addAttempt(attempt);
		shipment.addLot(lot);
		shipmentRepository.saveAndFlush(shipment);
		var settlement = settlementService.rebuild(auctionHouse.getId(), auctionDate);

		mockMvc.perform(post("/api/auction-settlements/{id}/confirm-payment", settlement.id())
				.contentType("application/json")
				.content(paymentJson(40_000)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.paidAmount").value(40_000))
			.andExpect(jsonPath("$.data.remainingAmount").value(60_000))
			.andExpect(jsonPath("$.data.status").value("PARTIALLY_PAID"));

		mockMvc.perform(post("/api/auction-settlements/{id}/confirm-payment", settlement.id())
				.contentType("application/json")
				.content(paymentJson(70_000)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").exists());
	}

	private String paymentJson(long amount) {
		return """
			{
			  "amount": %d,
			  "paymentDate": "2026-07-06",
			  "paymentMethod": "계좌이체",
			  "depositorName": "테스트 입금자",
			  "worker": "관리자",
			  "memo": "수동 확인"
			}
			""".formatted(amount);
	}
}
