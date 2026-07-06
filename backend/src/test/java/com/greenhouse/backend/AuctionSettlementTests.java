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
import com.greenhouse.backend.settlement.application.AuctionSettlementService;
import com.greenhouse.backend.settlement.domain.AuctionSettlementStatus;
import com.greenhouse.backend.settlement.repository.AuctionSettlementRepository;
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
class AuctionSettlementTests {
	@Autowired AuctionSettlementService settlementService;
	@Autowired AuctionSettlementRepository settlementRepository;
	@Autowired AuctionShipmentRepository shipmentRepository;
	@Autowired BusinessPartnerRepository partnerRepository;
	@Autowired MockMvc mockMvc;

	@Test
	void rebuildsAuctionDateSettlementFromSoldResultLines() throws Exception {
		var auctionHouse = partnerRepository.saveAndFlush(
			new BusinessPartner("정산 경매장", PartnerType.AUCTION_HOUSE, null, null, null, null));
		LocalDate auctionDate = LocalDate.of(2026, 7, 3);

		createResult(auctionHouse, LocalDate.of(2026, 7, 1), auctionDate, "카틀레야", 10, 12_000);
		createResult(auctionHouse, LocalDate.of(2026, 7, 2), auctionDate, "호접란", 5, 20_000);
		createResult(auctionHouse, LocalDate.of(2026, 7, 2), auctionDate, "유찰란", 3, 0);

		var rebuilt = settlementService.rebuild(auctionHouse.getId(), auctionDate);

		assertThat(rebuilt.status()).isEqualTo(AuctionSettlementStatus.PAYMENT_WAITING);
		assertThat(rebuilt.grossAmount()).isEqualTo(220_000L);
		assertThat(rebuilt.expectedDepositAmount()).isEqualTo(220_000L);
		assertThat(rebuilt.remainingAmount()).isEqualTo(220_000L);
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

	private void createResult(
		BusinessPartner auctionHouse,
		LocalDate shipmentDate,
		LocalDate auctionDate,
		String variety,
		int quantity,
		int unitPrice
	) {
		var shipment = new AuctionShipment(shipmentDate, auctionHouse);
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
