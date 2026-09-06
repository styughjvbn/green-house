package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.greenhouse.backend.auction.domain.AuctionAttempt;
import com.greenhouse.backend.auction.domain.AuctionAttemptStatus;
import com.greenhouse.backend.auction.domain.AuctionInspectionStatus;
import com.greenhouse.backend.auction.domain.AuctionResultLine;
import com.greenhouse.backend.auction.domain.AuctionShipment;
import com.greenhouse.backend.auction.domain.AuctionShipmentLot;
import com.greenhouse.backend.auction.repository.AuctionShipmentRepository;
import com.greenhouse.backend.audit.domain.AuditSource;
import com.greenhouse.backend.audit.repository.AuditEventRepository;
import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.partner.domain.PartnerType;
import com.greenhouse.backend.partner.repository.BusinessPartnerRepository;
import com.greenhouse.backend.settlement.application.AuctionSettlementService;
import com.greenhouse.backend.settlement.domain.AuctionSettlement;
import com.greenhouse.backend.settlement.domain.AuctionSettlementStatus;
import com.greenhouse.backend.settlement.repository.AuctionSettlementRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
class AuctionSettlementTests {
	@Autowired AuctionSettlementService settlementService;
	@Autowired AuctionSettlementRepository settlementRepository;
	@Autowired AuctionShipmentRepository shipmentRepository;
	@Autowired BusinessPartnerRepository partnerRepository;
	@Autowired MockMvc mockMvc;
	@Autowired AuditEventRepository auditEventRepository;

	@Test
	void rebuildsAuctionDateSettlementFromSoldResultLines() throws Exception {
		var auctionHouse = partnerRepository.saveAndFlush(
			new BusinessPartner("정산 경매장", PartnerType.AUCTION_HOUSE, null, null, null, null));
		LocalDate auctionDate = LocalDate.of(2026, 7, 3);
		mockMvc.perform(get("/api/business-partners/{partnerId}/settlement-settings", auctionHouse.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.settlementUnit").value("AUCTION_DATE"))
			.andExpect(jsonPath("$.data.paymentDelayDays").value(0));

		mockMvc.perform(put("/api/business-partners/{partnerId}/settlement-settings", auctionHouse.getId())
				.contentType("application/json")
				.content("""
					{
					  "settlementUnit": "AUCTION_DATE",
					  "paymentDelayDays": 3,
					  "paymentDayMode": "BUSINESS_DAY",
					  "autoMatchEnabled": true,
					  "autoSettleEnabled": false,
					  "amountTolerance": 1000,
					  "depositorAliases": [" 정산경매 ", "정산경매"],
					  "allowPrepayment": false,
					  "creditAutoApplyEnabled": false,
					  "ruleJson": {"auctionDays": ["MON", "THU"]},
					  "memo": "경매장 설정"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.depositorAliases.length()").value(1))
			.andExpect(jsonPath("$.data.ruleJson.auctionDays[0]").value("MON"));
		var settingsAudit = auditEventRepository.findAll().stream()
				.filter(event -> event.getSource() == AuditSource.SETTLEMENT_MANAGEMENT)
				.findFirst().orElseThrow();
		assertThat(settingsAudit.getEntityType()).isEqualTo("PARTNER_SETTLEMENT_SETTINGS");
		assertThat(settingsAudit.getChangedFields())
				.contains("paymentDelayDays", "paymentDayMode", "autoMatchEnabled",
						"amountTolerance", "depositorAliasCount", "ruleJson");
		assertThat(settingsAudit.getAfterData().toString()).doesNotContain("정산경매", "경매장 설정");

		createResult(auctionHouse, LocalDate.of(2026, 7, 1), auctionDate, "카틀레야", 10, 12_000);
		createResult(auctionHouse, LocalDate.of(2026, 7, 2), auctionDate, "덴드로비움", 5, 20_000);
		createResult(auctionHouse, LocalDate.of(2026, 7, 2), auctionDate, "신비디움", 3, 0);

		var rebuilt = settlementService.rebuild(auctionHouse.getId(), auctionDate);

		assertThat(rebuilt.status()).isEqualTo(AuctionSettlementStatus.PAYMENT_WAITING);
		assertThat(rebuilt.grossAmount()).isEqualTo(220_000L);
		assertThat(rebuilt.expectedDepositAmount()).isEqualTo(220_000L);
		assertThat(rebuilt.remainingAmount()).isEqualTo(220_000L);
		assertThat(rebuilt.expectedPaymentDate()).isEqualTo(LocalDate.of(2026, 7, 8));
		assertThat(rebuilt.lines()).hasSize(2);
		assertThat(rebuilt.lines()).extracting(line -> line.shipmentDate())
			.containsExactlyInAnyOrder(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2));

		var rebuiltAgain = settlementService.rebuild(auctionHouse.getId(), auctionDate);
		assertThat(rebuiltAgain.id()).isEqualTo(rebuilt.id());
		assertThat(rebuiltAgain.lines()).hasSize(2);
		assertThat(settlementRepository.count()).isEqualTo(1);

		mockMvc.perform(get("/api/auction-settlements")
				.param("auctionHouseId", auctionHouse.getId().toString())
				.param("from", "2026-07-01")
				.param("to", "2026-07-31"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].auctionHouseName").value("정산 경매장"))
			.andExpect(jsonPath("$.data[0].grossAmount").value(220_000))
			.andExpect(jsonPath("$.data[0].lines.length()").value(2));

		mockMvc.perform(post("/api/auction-settlements/rebuild")
				.param("auctionHouseId", auctionHouse.getId().toString())
				.param("auctionDate", auctionDate.toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(rebuilt.id()));
	}

	@Test
	void pagesSummariesAndAggregatesTheSameFiltersAcrossAllPages() throws Exception {
		var house = partnerRepository.saveAndFlush(
				new BusinessPartner("목록 경매장", PartnerType.AUCTION_HOUSE, null, null, null, null));
		var otherHouse = partnerRepository.saveAndFlush(
				new BusinessPartner("다른 경매장", PartnerType.AUCTION_HOUSE, null, null, null, null));
		var firstDate = LocalDate.of(2044, 1, 1);
		var first = createSettlement(house, firstDate, 1_000_000_000);
		var second = createSettlement(house, firstDate.plusDays(1), 1_000_000_000);
		var third = createSettlement(house, firstDate.plusDays(2), 1_000_000_000);
		var other = createSettlement(otherHouse, firstDate.plusDays(1), 1_000_000_000);
		first.recordPayment(1_000_000_000L, "테스트", LocalDateTime.of(2044, 1, 4, 0, 0));
		second.recordPayment(100_000_000L, "테스트", LocalDateTime.of(2044, 1, 4, 0, 0));
		settlementRepository.flush();

		mockMvc.perform(get("/api/auction-settlements/page").param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(4))
				.andExpect(jsonPath("$.data.totalPages").value(2))
				.andExpect(jsonPath("$.data.content.length()").value(2))
				.andExpect(jsonPath("$.data.content[0].id").value(third.getId()))
				.andExpect(jsonPath("$.data.content[1].id").value(other.getId()))
				.andExpect(jsonPath("$.data.content[0].lines").doesNotExist());
		mockMvc.perform(get("/api/auction-settlements/page").param("page", "1").param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[0].id").value(second.getId()))
				.andExpect(jsonPath("$.data.content[1].id").value(first.getId()));
		mockMvc.perform(get("/api/auction-settlements/summary"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.expectedDepositAmount").value(4_000_000_000L))
				.andExpect(jsonPath("$.data.remainingAmount").value(2_900_000_000L));
		mockMvc.perform(get("/api/auction-settlements/page")
				.param("auctionHouseId", house.getId().toString())
				.param("from", firstDate.toString()).param("to", firstDate.plusDays(1).toString()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2))
				.andExpect(jsonPath("$.data.content[0].id").value(second.getId()))
				.andExpect(jsonPath("$.data.content[1].id").value(first.getId()));
		mockMvc.perform(get("/api/auction-settlements/summary")
				.param("auctionHouseId", house.getId().toString())
				.param("from", firstDate.toString()).param("to", firstDate.plusDays(1).toString()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.expectedDepositAmount").value(2_000_000_000L))
				.andExpect(jsonPath("$.data.remainingAmount").value(900_000_000L));

		for (var path : new String[] { "/page", "/summary" }) {
			var result = mockMvc.perform(get("/api/auction-settlements" + path)
					.param("auctionHouseId", house.getId().toString())
					.param("from", firstDate.toString()).param("to", firstDate.plusDays(1).toString())
					.param("status", "PARTIALLY_PAID"))
					.andExpect(status().isOk());
			if (path.equals("/page")) {
				result.andExpect(jsonPath("$.data.totalElements").value(1))
						.andExpect(jsonPath("$.data.content[0].id").value(second.getId()));
			} else {
				result.andExpect(jsonPath("$.data.expectedDepositAmount").value(1_000_000_000L))
						.andExpect(jsonPath("$.data.remainingAmount").value(900_000_000L));
			}
		}
		mockMvc.perform(get("/api/auction-settlements/page").param("page", "8").param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content").isEmpty())
				.andExpect(jsonPath("$.data.totalElements").value(4));
		mockMvc.perform(get("/api/auction-settlements/page").param("page", "-1").param("size", "0"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.page").value(0))
				.andExpect(jsonPath("$.data.size").value(1));
		mockMvc.perform(get("/api/auction-settlements/page").param("size", "101"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.size").value(100));
		mockMvc.perform(get("/api/auction-settlements/{id}", second.getId()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.lines.length()").value(1));
	}

	@Test
	void emptySummaryIsZeroAndLegacyLimitDoesNotTruncateGlobalTotals() throws Exception {
		mockMvc.perform(get("/api/auction-settlements/summary").param("auctionHouseId", "-1"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.expectedDepositAmount").value(0))
				.andExpect(jsonPath("$.data.remainingAmount").value(0));
		var house = partnerRepository.saveAndFlush(
				new BusinessPartner("누적 경매장", PartnerType.AUCTION_HOUSE, null, null, null, null));
		var firstDate = LocalDate.of(2045, 1, 1);
		var oldest = createSettlement(house, firstDate, 1_000);
		for (int index = 1; index <= 500; index++) {
			settlementRepository.save(new AuctionSettlement(house.getId(), firstDate.plusDays(index)));
		}
		settlementRepository.flush();

		var legacy = settlementService.getSettlements(house.getId(), null, null, null);
		assertThat(legacy).hasSize(500);
		assertThat(legacy.getFirst().auctionDate()).isEqualTo(firstDate.plusDays(500));
		assertThat(legacy.getLast().auctionDate()).isEqualTo(firstDate.plusDays(1));
		assertThat(legacy).noneMatch(row -> row.id().equals(oldest.getId()));
		mockMvc.perform(get("/api/auction-settlements/page").param("auctionHouseId", house.getId().toString()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(501))
				.andExpect(jsonPath("$.data.size").value(10));
		mockMvc.perform(get("/api/auction-settlements/summary").param("auctionHouseId", house.getId().toString()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.expectedDepositAmount").value(1_000))
				.andExpect(jsonPath("$.data.remainingAmount").value(1_000));
	}

	private AuctionSettlement createSettlement(BusinessPartner house, LocalDate date, int amount) {
		createResult(house, date.minusDays(1), date, "카틀레야", 1, amount);
		var response = settlementService.rebuild(house.getId(), date);
		return settlementRepository.findById(response.id()).orElseThrow();
	}

	private void createResult(
		BusinessPartner auctionHouse,
		LocalDate shipmentDate,
		LocalDate auctionDate,
		String variety,
		int quantity,
		int unitPrice
	) {
		var shipment = new AuctionShipment(shipmentDate, auctionHouse.getId(), auctionHouse.getPartnerType());
		var lot = new AuctionShipmentLot("난", variety, "A", 1, quantity);
		var attempt = new AuctionAttempt(
			auctionDate,
			1,
			unitPrice > 0 ? AuctionAttemptStatus.SOLD : AuctionAttemptStatus.FAILED,
			unitPrice > 0 ? null : "유찰",
			null);
		attempt.addResultLine(new AuctionResultLine(
			auctionDate,
			"A",
			quantity,
			unitPrice,
			quantity * unitPrice,
			unitPrice > 0 ? null : "유찰",
			AuctionInspectionStatus.NORMAL));
		lot.addAttempt(attempt);
		shipment.addLot(lot);
		shipmentRepository.saveAndFlush(shipment);
	}
}
