package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.greenhouse.backend.auction.application.AuctionTrackingService;
import com.greenhouse.backend.auction.domain.AuctionAttempt;
import com.greenhouse.backend.auction.domain.AuctionAttemptStatus;
import com.greenhouse.backend.auction.domain.AuctionInspectionStatus;
import com.greenhouse.backend.auction.domain.AuctionLotStatus;
import com.greenhouse.backend.auction.domain.AuctionResultLine;
import com.greenhouse.backend.auction.domain.AuctionShipment;
import com.greenhouse.backend.auction.domain.AuctionShipmentLot;
import com.greenhouse.backend.auction.dto.AuctionLotAdjustmentRequest;
import com.greenhouse.backend.auction.dto.AuctionLotReturnRequest;
import com.greenhouse.backend.auction.repository.AuctionShipmentLotRepository;
import com.greenhouse.backend.auction.repository.AuctionShipmentRepository;
import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.partner.domain.PartnerType;
import com.greenhouse.backend.partner.repository.BusinessPartnerRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuctionTrackingTests {

	@Autowired AuctionTrackingService trackingService;
	@Autowired AuctionShipmentLotRepository lotRepository;
	@Autowired AuctionShipmentRepository shipmentRepository;
	@Autowired BusinessPartnerRepository partnerRepository;
	@Autowired MockMvc mockMvc;

	@Test
	void tracksFailedThenSoldAuctionsWithPagination() throws Exception {
		var lot = createLot(LocalDate.of(2026, 6, 1), "태성", "카틀레야 A", "특", 100);
		addResult(lot, LocalDate.of(2026, 6, 3), 1, 100, 0, "유찰", AuctionAttemptStatus.FAILED);
		lot.applyResult(0, 0, true, false);
		addResult(lot, LocalDate.of(2026, 6, 6), 2, 100, 10_000, null, AuctionAttemptStatus.SOLD);
		lot.applyResult(100, 0, false, false);
		lotRepository.flush();

		var response = trackingService.getLots(null, null, null, null, null, null, null, null, null, null, 0, 20);
		var tracked = response.content().getFirst();
		assertThat(response.totalElements()).isEqualTo(1);
		assertThat(tracked.currentStatus()).isEqualTo(AuctionLotStatus.SOLD);
		assertThat(tracked.soldQuantity()).isEqualTo(100);
		assertThat(tracked.failedCount()).isEqualTo(1);
		assertThat(tracked.totalAmount()).isEqualTo(1_000_000);

		mockMvc.perform(get("/api/auction-lots").param("page", "0").param("size", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.content.length()").value(1))
			.andExpect(jsonPath("$.data.totalElements").value(1));

		mockMvc.perform(get("/api/auction-tracking/summary"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.lotCount").value(1))
			.andExpect(jsonPath("$.data.totalAmount").value(1_000_000));
	}

	@Test
	void confirmsReturnAndAdjustsQuantitiesWithHistory() {
		var lot = createLot(LocalDate.of(2026, 6, 1), "양재", "심비디움 A", "A", 50);
		lot.applyResult(0, 50, false, true);
		lotRepository.flush();

		var partial = trackingService.confirmReturn(
			lot.getId(),
			new AuctionLotReturnRequest(20, LocalDate.of(2026, 6, 8), "관리자", "일부 반환")
		);
		assertThat(partial.currentStatus()).isEqualTo(AuctionLotStatus.PARTIALLY_RETURNED);
		assertThat(partial.returnedQuantity()).isEqualTo(20);
		assertThat(partial.waitingQuantity()).isEqualTo(30);
		assertThat(partial.returnConfirmableQuantity()).isEqualTo(30);
		assertThat(partial.returnConfirmedDate()).isEqualTo(LocalDate.of(2026, 6, 8));

		var returned = trackingService.confirmReturn(
			lot.getId(),
			new AuctionLotReturnRequest(null, LocalDate.of(2026, 6, 9), "관리자", "나머지 반환")
		);
		assertThat(returned.currentStatus()).isEqualTo(AuctionLotStatus.RETURNED);
		assertThat(returned.returnedQuantity()).isEqualTo(50);

		var adjusted = trackingService.adjust(
			lot.getId(),
			new AuctionLotAdjustmentRequest(10, 0, 40, "관리자", "수량 보정")
		);
		assertThat(adjusted.soldQuantity()).isEqualTo(10);
		assertThat(adjusted.statusHistory()).hasSizeGreaterThanOrEqualTo(2);
	}

	@Test
	void confirmsReturnFromReauctionWaiting() {
		var lot = createLot(LocalDate.of(2026, 6, 1), "수원", "카틀레야 B", "A", 30);
		lot.applyResult(0, 0, true, false);
		lotRepository.flush();

		var returned = trackingService.confirmReturn(
			lot.getId(),
			new AuctionLotReturnRequest(30, LocalDate.of(2026, 6, 10), "관리자", "재경매 없이 반환")
		);

		assertThat(returned.currentStatus()).isEqualTo(AuctionLotStatus.RETURNED);
		assertThat(returned.returnedQuantity()).isEqualTo(30);
		assertThat(returned.waitingQuantity()).isZero();
		assertThat(returned.returnConfirmedDate()).isEqualTo(LocalDate.of(2026, 6, 10));
	}

	@Test
	void createsAuctionShipmentAndLotsWhenAuctionSalesSlipIsCreated() throws Exception {
		var auctionHouse = createAuctionHouse("태성");
		String request = """
			{
			  "saleDate": "2026-07-05",
			  "salesType": "AUCTION",
			  "partnerId": %d,
			  "items": [
			    {
			      "itemName": "카틀레야 A",
			      "genus": "절화",
			      "spec": "특",
			      "quantity": 20,
			      "unitPrice": 0
			    },
			    {
			      "itemName": "덴드로비움 B",
			      "genus": "절화",
			      "spec": "A",
			      "quantity": 30,
			      "unitPrice": 0
			    }
			  ]
			}
			""".formatted(auctionHouse.getId());

		mockMvc.perform(post("/api/sales-slips")
				.contentType(MediaType.APPLICATION_JSON)
				.content(request))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.salesType").value("AUCTION"))
			.andExpect(jsonPath("$.data.auctionShipmentId").isNumber())
			.andExpect(jsonPath("$.data.partner.name").value("태성"))
			.andExpect(jsonPath("$.data.partner.partnerType").value("AUCTION_HOUSE"))
			.andExpect(jsonPath("$.data.saleDate").value("2026-07-05"))
			.andExpect(jsonPath("$.data.totalAmount").value(0))
			.andExpect(jsonPath("$.data.items.length()").value(2));

		assertThat(shipmentRepository.findAll()).hasSize(1);
		assertThat(lotRepository.findAll()).hasSize(2);
	}

	private AuctionShipmentLot createLot(
		LocalDate shipmentDate,
		String market,
		String variety,
		String grade,
		int quantity
	) {
		var shipment = new AuctionShipment(shipmentDate, createAuctionHouse(market));
		var lot = new AuctionShipmentLot("절화", variety, grade, 1, quantity);
		shipment.addLot(lot);
		shipmentRepository.saveAndFlush(shipment);
		return lot;
	}

	private BusinessPartner createAuctionHouse(String name) {
		return partnerRepository.saveAndFlush(
			new BusinessPartner(name, PartnerType.AUCTION_HOUSE, null, null, null, null)
		);
	}

	private void addResult(
		AuctionShipmentLot lot,
		LocalDate auctionDate,
		int attemptNo,
		int quantity,
		int unitPrice,
		String note,
		AuctionAttemptStatus status
	) {
		var attempt = new AuctionAttempt(auctionDate, attemptNo, status, note, null);
		attempt.addResultLine(new AuctionResultLine(
			auctionDate,
			lot.getShipmentGrade(),
			quantity,
			unitPrice,
			quantity * unitPrice,
			note,
			AuctionInspectionStatus.NORMAL
		));
		lot.addAttempt(attempt);
	}
}
