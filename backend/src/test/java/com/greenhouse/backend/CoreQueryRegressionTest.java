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
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
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

	@Autowired FarmStatusService farmStatusService;
	@Autowired AuctionTrackingService auctionTrackingService;
	@Autowired SalesQueryService salesQueryService;
	@Autowired HouseRepository houseRepository;
	@Autowired OrchidGroupRepository orchidGroupRepository;
	@Autowired AuctionShipmentRepository auctionShipmentRepository;
	@Autowired BusinessPartnerRepository partnerRepository;
	@Autowired SalesSlipRepository salesSlipRepository;
	@Autowired EntityManager entityManager;
	@Autowired EntityManagerFactory entityManagerFactory;

	@Test
	void farmViewportUsesFixedQueryCount() {
		createOrchidGroup(901, "회귀 품종", 20);

		long queryCount = measure(() -> farmStatusService.getOrchidManagementViewport(null, 2));

		assertThat(queryCount).isLessThanOrEqualTo(3L);
	}

	@Test
	void auctionLotPageUsesFixedQueryCount() {
		BusinessPartner auctionHouse = partnerRepository.save(
				new BusinessPartner("회귀 경매장", PartnerType.AUCTION_HOUSE, null, null, null, null));
		AuctionShipment shipment = new AuctionShipment(LocalDate.of(2030, 2, 1), auctionHouse);
		for (int index = 0; index < 3; index++) {
			AuctionShipmentLot lot = new AuctionShipmentLot("난", "품종 " + index, "특", 1, 10);
			AuctionAttempt attempt = new AuctionAttempt(
					LocalDate.of(2030, 2, 2), 1, AuctionAttemptStatus.SOLD, null, null);
			attempt.addResultLine(new AuctionResultLine(
					LocalDate.of(2030, 2, 2), "특", 10, 1_000, 10_000, null,
					AuctionInspectionStatus.NORMAL));
			lot.addAttempt(attempt);
			lot.applyResult(10, 0, false, false);
			shipment.addLot(lot);
		}
		auctionShipmentRepository.save(shipment);

		long queryCount = measure(() -> auctionTrackingService.getLots(
				null, null, null, null, null, null, false, false, false, null, 0, 10));

		assertThat(queryCount).isLessThanOrEqualTo(4L);
	}

	@Test
	void salesSlipDetailLoadsAllocationsAndActionsWithFixedQueryCount() {
		OrchidGroup orchidGroup = createOrchidGroup(902, "판매 회귀 품종", 20);
		BusinessPartner partner = partnerRepository.save(
				new BusinessPartner("회귀 거래처", PartnerType.WHOLESALE, null, null, null, null));
		SalesSlip slip = new SalesSlip(
				"S20300301-999",
				LocalDate.of(2030, 3, 1),
				SalesType.DIRECT,
				null,
				partner,
				"미입금",
				SalesSlip.STATUS_DRAFT,
				null,
				null);
		SalesSlipItem item = new SalesSlipItem(null, orchidGroup.getVarietyName(), null, null, 2, 1_000, null);
		item.addAllocation(new SalesSlipItemAllocation(orchidGroup, 2));
		slip.addItem(item);
		salesSlipRepository.save(slip);

		long queryCount = measure(() -> salesQueryService.getSalesSlip(slip.getId()));

		assertThat(queryCount).isLessThanOrEqualTo(3L);
	}

	private OrchidGroup createOrchidGroup(int houseNumber, String varietyName, int quantity) {
		House house = new House(houseNumber, houseNumber + "동");
		PhysicalBed bed = new PhysicalBed(1, 1);
		BedZone zone = new BedZone("좌", BedZoneSide.LEFT, 1);
		bed.addBedZone(zone);
		house.addPhysicalBed(bed);
		houseRepository.save(house);
		return orchidGroupRepository.save(new OrchidGroup(
				zone,
				"속",
				varietyName,
				quantity,
				"3치",
				1,
				"정상",
				1,
				BigDecimal.ZERO,
				BigDecimal.TEN));
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
