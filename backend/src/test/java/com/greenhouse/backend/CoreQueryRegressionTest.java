package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.auction.application.AuctionTrackingService;
import com.greenhouse.backend.auction.domain.AuctionAttempt;
import com.greenhouse.backend.auction.domain.AuctionAttemptStatus;
import com.greenhouse.backend.auction.domain.AuctionInspectionStatus;
import com.greenhouse.backend.auction.domain.AuctionResultLine;
import com.greenhouse.backend.auction.domain.AuctionShipment;
import com.greenhouse.backend.auction.domain.AuctionShipmentLot;
import com.greenhouse.backend.auction.repository.AuctionShipmentRepository;
import com.greenhouse.backend.farm.application.orchid.OrchidGroupReader;
import com.greenhouse.backend.farm.application.status.FarmStatusService;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.structure.BedZone;
import com.greenhouse.backend.farm.domain.structure.BedZoneSide;
import com.greenhouse.backend.farm.domain.structure.House;
import com.greenhouse.backend.farm.domain.structure.PhysicalBed;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.structure.HouseRepository;
import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.partner.domain.PartnerType;
import com.greenhouse.backend.partner.repository.BusinessPartnerRepository;
import com.greenhouse.backend.sales.application.SalesQueryService;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesSlipItem;
import com.greenhouse.backend.sales.domain.SalesSlipItemAllocation;
import com.greenhouse.backend.sales.domain.SalesType;
import com.greenhouse.backend.sales.repository.SalesSlipRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
class CoreQueryRegressionTest {

	@Autowired
	FarmStatusService farmStatusService;

	@Autowired
	OrchidGroupReader orchidGroupReader;

	@Autowired
	AuctionTrackingService auctionTrackingService;

	@Autowired
	SalesQueryService salesQueryService;

	@Autowired
	HouseRepository houseRepository;

	@Autowired
	OrchidGroupRepository orchidGroupRepository;

	@Autowired
	AuctionShipmentRepository auctionShipmentRepository;

	@Autowired
	BusinessPartnerRepository partnerRepository;

	@Autowired
	SalesSlipRepository salesSlipRepository;

	@Autowired
	EntityManager entityManager;

	@Autowired
	EntityManagerFactory entityManagerFactory;

	@Test
	void farmViewportUsesFixedQueryCount() {
		createOrchidGroup(901, "회귀 품종", 20);

		long queryCount = measure(() -> farmStatusService.getOrchidManagementViewport(null, 2));

		assertThat(queryCount).isLessThanOrEqualTo(3L);
	}

	@ParameterizedTest
	@CsvSource({ "0,0", "1,1", "500,1", "501,2" })
	void farmStateValuesAreLoadedInBoundedBatchesWithoutLazyQueries(int count, long expectedQueries) {
		var ids = new ArrayList<Long>();
		for (int index = 0; index < count; index++) {
			ids.add(createOrchidGroup(10_000 + index, "상태 계약 " + index, 20).getId());
		}
		if (!ids.isEmpty())
			ids.add(ids.getFirst());
		long queryCount = measure(() -> {
			var states = orchidGroupReader.getStates(ids.reversed());
			entityManager.clear();
			assertThat(states).hasSize(count);
			for (int index = 0; index < count; index++) {
				var state = states.get(ids.get(index));
				assertThat(state.varietyId()).isNull();
				assertThat(state.varietyName()).isEqualTo("상태 계약 " + index);
				assertThat(state.houseNumber()).isEqualTo(10_000 + index);
				assertThat(state.availableQuantity()).isEqualTo(20);
			}
		});
		assertThat(queryCount).isEqualTo(expectedQueries);
	}

	@Test
	void auctionLotPageUsesFixedQueryCount() {
		BusinessPartner auctionHouse = partnerRepository
			.save(new BusinessPartner("회귀 경매장", PartnerType.AUCTION_HOUSE, null, null, null, null));
		AuctionShipment shipment = new AuctionShipment(LocalDate.of(2030, 2, 1), auctionHouse.getId(),
				auctionHouse.getPartnerType());
		for (int index = 0; index < 3; index++) {
			AuctionShipmentLot lot = new AuctionShipmentLot("난", "품종 " + index, "특", 1, 10);
			AuctionAttempt attempt = new AuctionAttempt(LocalDate.of(2030, 2, 2), 1, AuctionAttemptStatus.SOLD, null,
					null);
			attempt.addResultLine(new AuctionResultLine(LocalDate.of(2030, 2, 2), "특", 10, 1_000, 10_000, null,
					AuctionInspectionStatus.NORMAL));
			lot.addAttempt(attempt);
			lot.applyResult(10, 0, false, false, java.time.LocalDateTime.of(2026, 9, 8, 1, 2));
			shipment.addLot(lot);
		}
		auctionShipmentRepository.save(shipment);

		long queryCount = measure(() -> auctionTrackingService.getLots(null, null, null, null, null, null, false, false,
				false, null, 0, 10));

		// One bulk lookup supplies current auction house names.
		assertThat(queryCount).isLessThanOrEqualTo(5L);
	}

	@ParameterizedTest
	@ValueSource(ints = { 1, 10, 50 })
	void salesSlipDetailLoadsAllocationsAndActionsWithFixedQueryCount(int count) {
		BusinessPartner partner = partnerRepository
			.save(new BusinessPartner("회귀 거래처", PartnerType.WHOLESALE, null, null, null, null));
		SalesSlip slip = new SalesSlip("S20300301-999", LocalDate.of(2030, 3, 1), SalesType.DIRECT, null,
				partner.getId(), "미입금", SalesSlip.STATUS_DRAFT, null, null);
		for (int index = 0; index < count; index++) {
			var group = createOrchidGroup(902 + index, "판매 회귀 품종 " + index, 20);
			var item = new SalesSlipItem(null, group.getVarietyName(), null, null, 2, 1_000, null);
			item.addAllocation(new SalesSlipItemAllocation(group.getId(), 2));
			slip.addItem(item);
		}
		salesSlipRepository.save(slip);

		long queryCount = measure(() -> salesQueryService.getSalesSlip(slip.getId()));

		// Root/items, allocations/snapshots, Farm states and partner values are loaded in
		// bulk.
		assertThat(queryCount).isEqualTo(5L);
	}

	@ParameterizedTest
	@ValueSource(ints = { 1, 10, 50 })
	void salesPageLoadsCurrentPartnerDetailsInOneBatch(int count) {
		var date = LocalDate.of(2041, 1, 1);
		for (int index = 0; index < count; index++) {
			var partner = partnerRepository
				.save(new BusinessPartner("이전 이름", PartnerType.WHOLESALE, null, null, null, null));
			salesSlipRepository.save(new SalesSlip("PARTNER-PAGE-" + index, date, SalesType.DIRECT, null,
					partner.getId(), "미입금", "작성중", null, null));
			partner.update("현재 이름 " + index, PartnerType.WHOLESALE, "대표 Search", "010-7890", "주소", "메모");
		}
		long queries = measure(() -> {
			var page = salesQueryService.getSalesSlipPage(null, date, date, null, null, "search", 0, 100);
			assertThat(page.totalElements()).isEqualTo(count);
			assertThat(page.content()).hasSize(count);
			for (int index = 0; index < count; index++) {
				var partner = page.content().get(index).partner();
				assertThat(partner.name()).isEqualTo("현재 이름 " + (count - index - 1));
				assertThat(partner.ownerName()).isEqualTo("대표 Search");
				assertThat(partner.phone()).isEqualTo("010-7890");
				assertThat(partner.address()).isEqualTo("주소");
				assertThat(partner.memo()).isEqualTo("메모");
			}
		});
		// One scalar Partner search, followed by the unchanged page/count/detail queries.
		assertThat(queries).isEqualTo(4);
		assertThat(salesQueryService.getSalesSlipPage(null, date, date, null, null, "7890", 0, 1).totalElements())
			.isEqualTo(count);
		assertThat(salesQueryService.getSalesSlipPage(null, date, date, null, null, "현재 이름", 0, 1).totalElements())
			.isEqualTo(count);
	}

	@ParameterizedTest
	@ValueSource(ints = { 1, 10, 50 })
	void auctionPageKeepsConcatenatedSearchAndBatchesCurrentMarketNames(int count) {
		var date = LocalDate.of(2041, 2, 1);
		for (int index = 0; index < count; index++) {
			var house = partnerRepository
				.save(new BusinessPartner("이전 경매장", PartnerType.AUCTION_HOUSE, null, null, null, null));
			var shipment = new AuctionShipment(date, house.getId(), house.getPartnerType());
			shipment.addLot(new AuctionShipmentLot("난", "Cattleya", "A", null, 10));
			auctionShipmentRepository.save(shipment);
			house.update("Market " + index, PartnerType.AUCTION_HOUSE, null, null, null, null);
		}
		long queries = measure(() -> {
			var page = auctionTrackingService.getLots(date, date, null, null, null, null, false, false, false,
					"cattleya market", 0, 100);
			assertThat(page.totalElements()).isEqualTo(count);
			assertThat(page.content()).hasSize(count);
			for (int index = 0; index < count; index++) {
				assertThat(page.content().get(index).auctionMarket()).isEqualTo("Market " + (count - index - 1));
			}
		});
		// Two scalar Partner matches preserve this phrase across the market-name
		// separator.
		assertThat(queries).isEqualTo(7);
	}

	@Test
	void shipmentOptionsFillTheLimitAfterSkippingUsedCandidatePages() {
		var house = partnerRepository
			.save(new BusinessPartner("선택지 경매장", PartnerType.AUCTION_HOUSE, null, null, null, null));
		var available = new java.util.ArrayList<Long>();
		var date = LocalDate.of(2042, 1, 1);
		for (int index = 0; index < 420; index++) {
			var shipment = new AuctionShipment(date, house.getId(), house.getPartnerType());
			shipment.addLot(new AuctionShipmentLot("난", "선택 품종", "A", null, 10));
			auctionShipmentRepository.save(shipment);
			if (index < 210) {
				available.add(shipment.getId());
			}
			else {
				salesSlipRepository.save(new SalesSlip("USED-" + index, date, SalesType.AUCTION, shipment.getId(),
						house.getId(), "정산 대기", "출하 완료", null, null));
			}
		}
		long queries = measure(() -> {
			var options = salesQueryService.getAuctionShipmentOptions();
			assertThat(options).extracting(option -> option.id())
				.containsExactlyElementsOf(available.reversed().subList(0, 200));
			assertThat(options).allSatisfy(option -> {
				assertThat(option.auctionMarket()).isEqualTo("선택지 경매장");
				assertThat(option.lots()).singleElement()
					.satisfies(lot -> assertThat(lot.shippedQuantity()).isEqualTo(10));
			});
		});
		assertThat(queries).isEqualTo(8);
	}

	private OrchidGroup createOrchidGroup(int houseNumber, String varietyName, int quantity) {
		House house = new House(houseNumber, houseNumber + "동");
		PhysicalBed bed = new PhysicalBed(1, 1);
		BedZone zone = new BedZone("좌", BedZoneSide.LEFT, 1);
		bed.addBedZone(zone);
		house.addPhysicalBed(bed);
		houseRepository.save(house);
		return orchidGroupRepository
			.save(new OrchidGroup(zone, "속", varietyName, quantity, "3치", 1, "정상", 1, BigDecimal.ZERO, BigDecimal.TEN));
	}

	private long measure(Runnable action) {
		entityManager.flush();
		entityManager.clear();
		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();
		action.run();
		return statistics.getPrepareStatementCount();
	}

}
