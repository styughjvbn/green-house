package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.greenhouse.backend.auction.dto.AuctionLotResultLineRequest;
import com.greenhouse.backend.auction.dto.AuctionLotResultRequest;
import com.greenhouse.backend.auction.dto.AuctionLotReturnRequest;
import com.greenhouse.backend.auction.repository.AuctionShipmentLotRepository;
import com.greenhouse.backend.auction.repository.AuctionShipmentRepository;
import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.partner.domain.PartnerType;
import com.greenhouse.backend.partner.repository.BusinessPartnerRepository;
import com.greenhouse.backend.farm.repository.OrchidGroupRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Disabled;
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
@Disabled("Seed data is currently disabled; re-enable after deterministic test fixtures are restored.")
class AuctionTrackingTests {

	@Autowired AuctionTrackingService trackingService;
	@Autowired AuctionShipmentLotRepository lotRepository;
	@Autowired AuctionShipmentRepository shipmentRepository;
	@Autowired BusinessPartnerRepository partnerRepository;
	@Autowired OrchidGroupRepository orchidGroupRepository;
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
	void createsAuctionShipmentAndLotsWhenAuctionSalesSlipIsCreatedAsCompleted() throws Exception {
		var auctionHouse = createAuctionHouse("??");
		var sampleGroups = orchidGroupRepository.findAll().stream()
				.filter(group -> group.getVariety() != null)
				.limit(2)
				.toList();
		String request = """
			{
			  "saleDate": "2026-07-05",
			  "salesType": "AUCTION",
			  "partnerId": %d,
			  "salesStatus": "출하 완료",
			  "items": [
			    {
			      "itemName": "%s",
			      "genus": "%s",
			      "spec": "?",
			      "quantity": 20,
			      "unitPrice": 0,
			      "allocations": [
			        {
			          "orchidGroupId": %d,
			          "quantity": 20
			        }
			      ]
			    },
			    {
			      "itemName": "%s",
			      "genus": "%s",
			      "spec": "A",
			      "quantity": 30,
			      "unitPrice": 0,
			      "allocations": [
			        {
			          "orchidGroupId": %d,
			          "quantity": 30
			        }
			      ]
			    }
			  ]
			}
			""".formatted(
					auctionHouse.getId(),
					sampleGroups.get(0).getVarietyName(),
					sampleGroups.get(0).getGenus(),
					sampleGroups.get(0).getId(),
					sampleGroups.get(1).getVarietyName(),
					sampleGroups.get(1).getGenus(),
					sampleGroups.get(1).getId());

		mockMvc.perform(post("/api/sales-slips")
				.contentType(MediaType.APPLICATION_JSON)
				.content(request))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.salesType").value("AUCTION"))
			.andExpect(jsonPath("$.data.auctionShipmentId").isNumber())
			.andExpect(jsonPath("$.data.partner.partnerType").value("AUCTION_HOUSE"))
			.andExpect(jsonPath("$.data.saleDate").value("2026-07-05"))
			.andExpect(jsonPath("$.data.totalAmount").value(0))
			.andExpect(jsonPath("$.data.items.length()").value(2));

		assertThat(shipmentRepository.findAll()).hasSize(1);
		assertThat(lotRepository.findAll()).hasSize(2);
	}

	@Test
	void createsDraftAuctionSalesSlipWithoutShipmentAndMaterializesOnCompletion() throws Exception {
		var auctionHouse = createAuctionHouse("양재");
		var sampleGroup = orchidGroupRepository.findAll().stream()
				.filter(group -> group.getVariety() != null)
				.findFirst()
				.orElseThrow();

		var createResult = mockMvc.perform(post("/api/sales-slips")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "saleDate": "2026-07-08",
					  "salesType": "AUCTION",
					  "partnerId": %d,
					  "salesStatus": "작성중",
					  "items": [
					    {
					      "itemName": "%s",
					      "genus": "%s",
					      "quantity": 10,
					      "unitPrice": 0,
					      "allocations": [
					        {
					          "orchidGroupId": %d,
					          "quantity": 10
					        }
					      ]
					    }
					  ]
					}
					""".formatted(
						auctionHouse.getId(),
						sampleGroup.getVarietyName(),
						sampleGroup.getGenus(),
						sampleGroup.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.salesStatus").value("작성중"))
				.andExpect(jsonPath("$.data.auctionShipmentId").value(nullValue()))
				.andReturn();

		var salesSlipId = Long.valueOf(
				createResult.getResponse().getContentAsString().replaceFirst(".*?\\\"id\\\":(\\d+).*", "$1"));

		assertThat(shipmentRepository.findAll()).isEmpty();
		assertThat(lotRepository.findAll()).isEmpty();

		mockMvc.perform(patch("/api/sales-slips/{salesSlipId}/sales-status", salesSlipId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "salesStatus": "출하 완료"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.salesStatus").value("출하 완료"))
			.andExpect(jsonPath("$.data.auctionShipmentId").isNumber())
			.andExpect(jsonPath("$.data.items[0].auctionShipmentLotId").isNumber());

		assertThat(shipmentRepository.findAll()).hasSize(1);
		assertThat(lotRepository.findAll()).hasSize(1);
	}

	@Test
	void cancelsCompletedAuctionSalesSlipAndRemovesShipmentWhenNoAuctionProgressExists() throws Exception {
		var auctionHouse = createAuctionHouse("양재");
		var sampleGroup = orchidGroupRepository.findAll().stream()
				.filter(group -> group.getVariety() != null)
				.findFirst()
				.orElseThrow();

		var createResult = mockMvc.perform(post("/api/sales-slips")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "saleDate": "2026-07-08",
					  "salesType": "AUCTION",
					  "partnerId": %d,
					  "salesStatus": "출하 완료",
					  "items": [
					    {
					      "itemName": "%s",
					      "genus": "%s",
					      "quantity": 10,
					      "unitPrice": 0,
					      "allocations": [
					        {
					          "orchidGroupId": %d,
					          "quantity": 10
					        }
					      ]
					    }
					  ]
					}
					""".formatted(
						auctionHouse.getId(),
						sampleGroup.getVarietyName(),
						sampleGroup.getGenus(),
						sampleGroup.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.auctionShipmentId").isNumber())
				.andReturn();

		var salesSlipId = Long.valueOf(
				createResult.getResponse().getContentAsString().replaceFirst(".*?\\\"id\\\":(\\d+).*", "$1"));

		mockMvc.perform(patch("/api/sales-slips/{salesSlipId}/sales-status", salesSlipId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "salesStatus": "취소"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.salesStatus").value("취소"))
			.andExpect(jsonPath("$.data.auctionShipmentId").value(nullValue()));

		assertThat(shipmentRepository.findAll()).isEmpty();
		assertThat(lotRepository.findAll()).isEmpty();
	}

	@Test
	void rejectsAuctionSalesSlipCancelWhenAuctionResultAlreadyExists() throws Exception {
		var auctionHouse = createAuctionHouse("수원");
		var sampleGroup = orchidGroupRepository.findAll().stream()
				.filter(group -> group.getVariety() != null)
				.findFirst()
				.orElseThrow();

		var createResult = mockMvc.perform(post("/api/sales-slips")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "saleDate": "2026-07-08",
					  "salesType": "AUCTION",
					  "partnerId": %d,
					  "salesStatus": "출하 완료",
					  "items": [
					    {
					      "itemName": "%s",
					      "genus": "%s",
					      "quantity": 10,
					      "unitPrice": 0,
					      "allocations": [
					        {
					          "orchidGroupId": %d,
					          "quantity": 10
					        }
					      ]
					    }
					  ]
					}
					""".formatted(
						auctionHouse.getId(),
						sampleGroup.getVarietyName(),
						sampleGroup.getGenus(),
						sampleGroup.getId())))
				.andExpect(status().isCreated())
				.andReturn();
		var salesSlipId = Long.valueOf(
				createResult.getResponse().getContentAsString().replaceFirst(".*?\\\"id\\\":(\\d+).*", "$1"));
		var createdLot = lotRepository.findAll().getFirst();

		trackingService.addResult(
			createdLot.getId(),
			new AuctionLotResultRequest(
				LocalDate.of(2026, 7, 9),
				1,
				AuctionAttemptStatus.FAILED,
				"유찰",
				null,
				null
			)
		);

		mockMvc.perform(patch("/api/sales-slips/{salesSlipId}/sales-status", salesSlipId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "salesStatus": "취소"
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
	}

	@Test
	void recordsManualAuctionResults() throws Exception {
		var soldLot = createLot(LocalDate.of(2026, 7, 1), "양재", "카틀레야 A", "특", 100);
		var partialLot = createLot(LocalDate.of(2026, 7, 1), "양재", "덴드로비움 B", "A", 80);
		var returnLot = createLot(LocalDate.of(2026, 7, 1), "양재", "호접란 C", "A", 30);

		var soldResponse = trackingService.addResult(
			soldLot.getId(),
			new AuctionLotResultRequest(
				LocalDate.of(2026, 7, 3),
				null,
				AuctionAttemptStatus.SOLD,
				null,
				"수동 입력",
				java.util.List.of(
					new AuctionLotResultLineRequest("특", 60, 12000, null, AuctionInspectionStatus.NORMAL),
					new AuctionLotResultLineRequest("특", 40, 11500, null, AuctionInspectionStatus.NORMAL)
				)
			)
		);
		assertThat(soldResponse.currentStatus()).isEqualTo(AuctionLotStatus.SOLD);
		assertThat(soldResponse.totalAmount()).isEqualTo(1_180_000);
		assertThat(soldResponse.attempts()).hasSize(1);

		var partialResponse = trackingService.addResult(
			partialLot.getId(),
			new AuctionLotResultRequest(
				LocalDate.of(2026, 7, 4),
				1,
				AuctionAttemptStatus.PARTIALLY_SOLD,
				"잔량 유찰",
				null,
				java.util.List.of(
					new AuctionLotResultLineRequest("A", 30, 9000, null, AuctionInspectionStatus.NORMAL)
				)
			)
		);
		assertThat(partialResponse.currentStatus()).isEqualTo(AuctionLotStatus.PARTIALLY_SOLD);
		assertThat(partialResponse.waitingQuantity()).isEqualTo(50);
		assertThat(partialResponse.attempts().getFirst().attemptStatus()).isEqualTo(AuctionAttemptStatus.PARTIALLY_SOLD);
		assertThat(partialResponse.attempts().getFirst().resultLines()).hasSize(2);

		mockMvc.perform(post("/api/auction-lots/" + returnLot.getId() + "/results")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "auctionDate": "2026-07-05",
					  "attemptStatus": "RETURN_INFERRED",
					  "failedReason": "반환 추정 등록",
					  "memo": "수동 확인 대기"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.currentStatus").value("RETURN_INFERRED"))
			.andExpect(jsonPath("$.data.returnedQuantity").value(30))
			.andExpect(jsonPath("$.data.returnConfirmableQuantity").value(30))
			.andExpect(jsonPath("$.data.attempts[0].attemptStatus").value("RETURN_INFERRED"));
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
