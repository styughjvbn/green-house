package com.greenhouse.backend.settlement.application;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.greenhouse.backend.settlement.dto.ManualPaymentRequest;
import com.greenhouse.backend.settlement.repository.AuctionSettlementRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Import(AuctionSettlementClockIntegrationTest.FixedTime.class)
@Transactional
class AuctionSettlementClockIntegrationTest {

	private static final LocalDate AUCTION_DATE = LocalDate.of(2026, 9, 5);
	private static final LocalDateTime UTC_TIME = LocalDateTime.of(2026, 9, 5, 15, 30);
	private static final LocalDateTime FARM_TIME = LocalDateTime.of(2026, 9, 6, 0, 30);

	@Autowired AuctionSettlementService settlementService;
	@Autowired PaymentService paymentService;
	@Autowired BusinessPartnerRepository partnerRepository;
	@Autowired AuctionShipmentRepository shipmentRepository;
	@Autowired AuctionSettlementRepository settlementRepository;

	@Test
	void persistsTheInjectedUtcTimeAndReturnsFarmTimeAcrossTheDateBoundary() {
		var house = createHouse("정산 시각 경매장");
		createResult(house, AUCTION_DATE, 10_000);

		var settlement = settlementService.rebuild(house.getId(), AUCTION_DATE);
		assertThat(settlement.resultReceivedAt()).isEqualTo(FARM_TIME);
		assertThat(settlementRepository.findById(settlement.id()).orElseThrow().getResultReceivedAt()).isEqualTo(UTC_TIME);

		var paid = paymentService.confirmAuctionPayment(settlement.id(), new ManualPaymentRequest(
				1_000L, FARM_TIME.toLocalDate(), "clock-payment", "계좌이체", null, "작성자", null));
		assertThat(paid.confirmedAt()).isEqualTo(FARM_TIME);
		assertThat(paid.paidAmount()).isEqualTo(1_000L);
		assertThat(settlementRepository.findById(settlement.id()).orElseThrow().getConfirmedAt()).isEqualTo(UTC_TIME);
	}

	@Test
	void bulkRebuildMergesOnlyNewSoldLinesAndDoesNothingWhenRepeated() {
		var first = createHouse("일괄 경매장 1");
		var second = createHouse("일괄 경매장 2");
		createResult(first, AUCTION_DATE, 10_000);
		var original = settlementService.rebuild(first.getId(), AUCTION_DATE);
		createResult(first, AUCTION_DATE, 20_000);
		createResult(first, AUCTION_DATE, 0);
		createResult(second, AUCTION_DATE.plusDays(1), 30_000);

		assertThat(settlementService.rebuildExistingResults()).isEqualTo(2);
		var updated = settlementService.getSettlement(original.id());
		assertThat(updated.lines()).hasSize(2);
		assertThat(updated.grossAmount()).isEqualTo(30_000L);
		assertThat(updated.resultReceivedAt()).isEqualTo(FARM_TIME);
		assertThat(settlementService.getSettlements(second.getId(), null, null, null))
				.singleElement().satisfies(settlement -> {
					assertThat(settlement.lines()).hasSize(1);
					assertThat(settlement.grossAmount()).isEqualTo(30_000L);
					assertThat(settlement.resultReceivedAt()).isEqualTo(FARM_TIME);
				});
		assertThat(settlementService.rebuildExistingResults()).isZero();
		assertThat(settlementService.getSettlement(original.id()).lines()).hasSize(2);
	}

	@Test
	void allowsRebuildingExistingResultsForInactiveAuctionHouses() {
		var house = createHouse("과거 경매장");
		createResult(house, AUCTION_DATE, 10_000);
		ReflectionTestUtils.setField(house, "active", false);

		assertThat(settlementService.rebuild(house.getId(), AUCTION_DATE).grossAmount()).isEqualTo(10_000L);
	}

	private BusinessPartner createHouse(String name) {
		return partnerRepository.saveAndFlush(new BusinessPartner(name, PartnerType.AUCTION_HOUSE,
				null, null, null, null));
	}

	private void createResult(BusinessPartner house, LocalDate date, int amount) {
		var shipment = new AuctionShipment(date.minusDays(1), house.getId(), house.getPartnerType());
		var lot = new AuctionShipmentLot("난", "카틀레야", "A", 1, 10);
		var attempt = new AuctionAttempt(date, 1,
				amount > 0 ? AuctionAttemptStatus.SOLD : AuctionAttemptStatus.FAILED, null, null);
		attempt.addResultLine(new AuctionResultLine(date, "A", 10, amount / 10, amount,
				null, AuctionInspectionStatus.NORMAL));
		lot.addAttempt(attempt);
		shipment.addLot(lot);
		shipmentRepository.saveAndFlush(shipment);
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedTime {
		@Bean
		@Primary
		Clock settlementTestClock() {
			return Clock.fixed(Instant.parse("2026-09-05T15:30:00Z"), ZoneOffset.UTC);
		}
	}
}
