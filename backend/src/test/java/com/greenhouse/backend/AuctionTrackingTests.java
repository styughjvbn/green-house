package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.greenhouse.backend.auction.application.AuctionImportService;
import com.greenhouse.backend.auction.application.AuctionTrackingService;
import com.greenhouse.backend.auction.domain.AuctionInspectionStatus;
import com.greenhouse.backend.auction.domain.AuctionLotStatus;
import com.greenhouse.backend.auction.dto.AuctionLotAdjustmentRequest;
import com.greenhouse.backend.auction.dto.AuctionLotReturnRequest;
import com.greenhouse.backend.auction.repository.AuctionShipmentLotRepository;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuctionTrackingTests {

	@Autowired AuctionImportService importService;
	@Autowired AuctionTrackingService trackingService;
	@Autowired AuctionShipmentLotRepository lotRepository;
	@Autowired MockMvc mockMvc;

	@Test
	void importsShipmentAndTracksFailedThenSoldAuctions() throws Exception {
		var batch = importService.importCsv(csv("""
			구분,출하일자,경매일자,경매장,품목명,품종명,등급,상자,분수량,단가,금액,비고
			출하,2026-06-01,,음성,난,카틀레야 A,특,10,100,0,0,
			경매,2026-06-01,2026-06-03,음성,난,카틀레야 A,특,0,100,0,0,유찰
			경매,2026-06-01,2026-06-06,음성,난,카틀레야 A,특,0,100,10000,1000000,
			"""));

		assertThat(batch.rowCount()).isEqualTo(3);
		assertThat(lotRepository.count()).isEqualTo(1);
		var lot = trackingService.getLots(null, null, null, null, null, null, null, null, null, null).getFirst();
		assertThat(lot.currentStatus()).isEqualTo(AuctionLotStatus.SOLD);
		assertThat(lot.soldQuantity()).isEqualTo(100);
		assertThat(lot.waitingQuantity()).isZero();
		assertThat(lot.failedCount()).isEqualTo(1);
		assertThat(lot.totalAmount()).isEqualTo(1_000_000);

		mockMvc.perform(get("/api/auction-imports/{id}/rows", batch.id()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(3))
			.andExpect(jsonPath("$.data[1].validationStatus").value("AUTO_MATCHED"));
	}

	@Test
	void flagsAmbiguousDuplicateLotsForManualReview() {
		var batch = importService.importCsv(csv("""
			구분,출하일자,경매일자,경매장,품종명,등급,상자,분수량,단가,금액,비고
			출하,2026-06-01,,음성,덴드로비움,,3,30,0,0,
			출하,2026-06-01,,음성,덴드로비움,,2,20,0,0,
			경매,2026-06-01,2026-06-03,음성,덴드로비움,,0,50,5000,250000,
			"""));

		var rows = importService.getRows(batch.id());
		assertThat(rows.getLast().validationStatus()).isEqualTo(AuctionInspectionStatus.MANUAL_REVIEW);
		assertThat(rows.getLast().matchedEntityId()).isNull();
	}

	@Test
	void confirmsReturnAndAdjustsQuantitiesWithHistory() {
		importService.importCsv(csv("""
			구분,출하일자,경매일자,경매장,품종명,등급,상자,분수량,단가,금액,비고
			출하,2026-06-01,,양재,호접란 A,A,5,50,0,0,
			경매,2026-06-01,2026-06-03,양재,호접란 A,A,0,50,0,0,유찰
			"""));
		var lot = trackingService.getLots(null, null, null, null, null, null, null, null, null, null).getFirst();

		var returned = trackingService.confirmReturn(lot.id(), new AuctionLotReturnRequest("관리자", "농장 도착"));
		assertThat(returned.currentStatus()).isEqualTo(AuctionLotStatus.RETURNED);
		assertThat(returned.returnedQuantity()).isEqualTo(50);

		var adjusted = trackingService.adjust(lot.id(), new AuctionLotAdjustmentRequest(10, 0, 40, "관리자", "실수량 확인"));
		assertThat(adjusted.soldQuantity()).isEqualTo(10);
		assertThat(adjusted.statusHistory()).hasSizeGreaterThanOrEqualTo(2);
	}

	private MockMultipartFile csv(String content) {
		return new MockMultipartFile("file", "auction.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
	}
}
