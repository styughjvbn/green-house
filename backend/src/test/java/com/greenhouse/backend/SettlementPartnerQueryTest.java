package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.greenhouse.backend.auction.domain.AuctionAttempt;
import com.greenhouse.backend.auction.domain.AuctionAttemptStatus;
import com.greenhouse.backend.auction.domain.AuctionInspectionStatus;
import com.greenhouse.backend.auction.domain.AuctionResultLine;
import com.greenhouse.backend.auction.domain.AuctionShipment;
import com.greenhouse.backend.auction.domain.AuctionShipmentLot;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.partner.domain.PartnerType;
import com.greenhouse.backend.settlement.application.AuctionSettlementService;
import com.greenhouse.backend.settlement.application.PaymentService;
import com.greenhouse.backend.settlement.domain.AuctionSettlement;
import com.greenhouse.backend.settlement.domain.AuctionSettlementLine;
import com.greenhouse.backend.settlement.domain.AuctionSettlementStatus;
import com.greenhouse.backend.settlement.domain.PartnerPaymentEvent;
import com.greenhouse.backend.settlement.domain.PaymentEventType;
import com.greenhouse.backend.settlement.domain.PaymentTargetType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class SettlementPartnerQueryTest {

	@Autowired PaymentService paymentService;
	@Autowired AuctionSettlementService settlementService;
	@Autowired EntityManager entityManager;
	@Autowired EntityManagerFactory entityManagerFactory;

	@ParameterizedTest
	@ValueSource(ints = { 1, 10, 50 })
	void loadsSettlementNamesAndLinesWithoutPerRowQueries(int partnerCount) {
		LocalDate from = LocalDate.of(2026, 9, 6);
		for (int index = 0; index < partnerCount; index++) {
			var house = new BusinessPartner("기존 경매장", PartnerType.AUCTION_HOUSE, null, null, null, null);
			entityManager.persist(house);
			var date = from.plusDays(index);
			var shipment = new AuctionShipment(date.minusDays(1), house.getId(), house.getPartnerType());
			var lot = new AuctionShipmentLot("난", "카틀레야", "A", 1, 10);
			var attempt = new AuctionAttempt(date, 1, AuctionAttemptStatus.SOLD, null, null);
			var result = new AuctionResultLine(date, "A", 10, 1_000, 10_000, null, AuctionInspectionStatus.NORMAL);
			attempt.addResultLine(result);
			lot.addAttempt(attempt);
			shipment.addLot(lot);
			entityManager.persist(shipment);
			var settlement = new AuctionSettlement(house.getId(), date);
			settlement.synchronizeLines(List.of(new AuctionSettlementLine(result.getId(), lot.getId(), 10, 1_000, 10_000L)), LocalDateTime.of(2026, 9, 6, 0, 0));
			entityManager.persist(settlement);
			house.update("변경 경매장 " + index, PartnerType.AUCTION_HOUSE, null, null, null, null);
		}
		var statistics = flushAndResetStatistics();

		var settlements = settlementService.getSettlements(null, from, from.plusDays(partnerCount),
				AuctionSettlementStatus.PAYMENT_WAITING);

		assertThat(settlements).hasSize(partnerCount);
		for (int index = 0; index < partnerCount; index++) {
			var settlement = settlements.get(index);
			assertThat(settlement.auctionHouseName()).isEqualTo("변경 경매장 " + (partnerCount - index - 1));
			assertThat(settlement.auctionDate()).isEqualTo(from.plusDays(partnerCount - index - 1));
			assertThat(settlement.grossAmount()).isEqualTo(10_000L);
			assertThat(settlement.lines()).singleElement().satisfies(line -> {
				assertThat(line.shipmentDate()).isEqualTo(settlement.auctionDate().minusDays(1));
				assertThat(line.quantity()).isEqualTo(10);
			});
		}
		assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(3);
	}

	@Test
	void doesNotLoadPartnersForEmptyEventOrSettlementResults() {
		var statistics = flushAndResetStatistics();
		assertThat(paymentService.getEvents(-1L, null, null)).isEmpty();
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
		statistics.clear();
		assertThat(settlementService.getSettlements(-1L, null, null, null)).isEmpty();
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
	}

	@Test
	void rejectsMissingAuctionHouses() {
		assertThatThrownBy(() -> settlementService.rebuild(-1L, LocalDate.of(2026, 9, 6)))
				.isInstanceOf(NotFoundException.class);
	}

	@Test
	void rejectsNonAuctionPartners() {
		var partner = new BusinessPartner("일반 거래처", PartnerType.WHOLESALE, null, null, null, null);
		entityManager.persist(partner);
		assertThatThrownBy(() -> settlementService.rebuild(partner.getId(), LocalDate.of(2026, 9, 6)))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("경매장 유형 거래처만 정산할 수 있습니다.");
	}

	@Test
	void rebuildScansResultIdsInBatchesAndKeepsExistingFinancialSnapshots() {
		var date = LocalDate.of(2043, 1, 1);
		var house = new BusinessPartner("배치 경매장", PartnerType.AUCTION_HOUSE, null, null, null, null);
		entityManager.persist(house);
		var shipment = new AuctionShipment(date, house.getId(), house.getPartnerType());
		var lot = new AuctionShipmentLot("난", "배치 품종", "A", null, 502);
		var attempt = new AuctionAttempt(date, 1, AuctionAttemptStatus.SOLD, null, null);
		for (int index = 0; index < 501; index++) {
			attempt.addResultLine(new AuctionResultLine(date, "A", 1, 1_000, 1_000, null, AuctionInspectionStatus.NORMAL));
		}
		lot.addAttempt(attempt);
		shipment.addLot(lot);
		entityManager.persist(shipment);
		var original = settlementService.rebuild(house.getId(), date);
		assertThat(original.lines()).hasSize(501);
		var firstResult = attempt.getResultLines().getFirst();
		Long firstResultId = firstResult.getId();
		// A later change to a source must not rewrite an already recorded settlement amount.
		org.springframework.test.util.ReflectionTestUtils.setField(firstResult, "amount", 9_000);
		org.springframework.test.util.ReflectionTestUtils.setField(firstResult, "unitPrice", 9_000);
		var additional = new AuctionResultLine(date, "A", 1, 2_000, 2_000, null, AuctionInspectionStatus.NORMAL);
		attempt.addResultLine(additional);
		entityManager.persist(additional);
		flushAndResetStatistics();

		assertThat(settlementService.rebuildExistingResults()).isEqualTo(1);
		var updated = settlementService.getSettlement(original.id());
		assertThat(updated.lines()).hasSize(502);
		assertThat(updated.grossAmount()).isEqualTo(503_000L);
		assertThat(updated.lines().stream().filter(line -> line.auctionResultLineId().equals(firstResultId)))
				.singleElement().satisfies(line -> {
					assertThat(line.amount()).isEqualTo(1_000L);
					assertThat(line.unitPrice()).isEqualTo(1_000);
				});
		var statistics = flushAndResetStatistics();
		assertThat(settlementService.rebuildExistingResults()).isZero();
		// Two pages of IDs + two local link checks; no source details or settlements are loaded again.
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(4);
	}

	@ParameterizedTest
	@ValueSource(ints = { 1, 10, 50 })
	void loadsEventPartnerNamesInOneBatchWithoutChangingOrderOrParentLinks(int partnerCount) {
		for (int index = 0; index < partnerCount; index++) {
			var partner = new BusinessPartner("기존 이름", PartnerType.WHOLESALE, null, null, null, null);
			entityManager.persist(partner);
			var received = PartnerPaymentEvent.received(partner.getId(), LocalDate.of(2026, 9, 6).plusDays(index),
					1_000L, PaymentTargetType.SALES_SLIP, 99881L, "계좌이체", "입금자", null, "메모", "작성자");
			entityManager.persist(received);
			entityManager.persist(PartnerPaymentEvent.manualMatch(received));
			partner.update("변경 이름 " + index, PartnerType.WHOLESALE, null, null, null, null);
		}
		var statistics = flushAndResetStatistics();

		var events = paymentService.getEvents(null, PaymentTargetType.SALES_SLIP, 99881L);

		assertThat(events).hasSize(partnerCount * 2);
		for (int index = 0; index < partnerCount; index++) {
			var match = events.get(index * 2);
			var received = events.get(index * 2 + 1);
			assertThat(match.partnerName()).isEqualTo("변경 이름 " + (partnerCount - index - 1));
			assertThat(match.eventType()).isEqualTo(PaymentEventType.MANUAL_MATCH_CONFIRMED);
			assertThat(match.parentEventId()).isEqualTo(received.id());
			assertThat(received.partnerId()).isEqualTo(match.partnerId());
			assertThat(received.partnerName()).isEqualTo(match.partnerName());
		}
		assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(2);
	}

	private Statistics flushAndResetStatistics() {
		entityManager.flush();
		entityManager.clear();
		var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();
		return statistics;
	}
}
