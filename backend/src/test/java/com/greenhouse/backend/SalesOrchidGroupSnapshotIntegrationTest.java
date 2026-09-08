package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.auction.repository.AuctionShipmentRepository;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.structure.BedZone;
import com.greenhouse.backend.farm.domain.structure.BedZoneSide;
import com.greenhouse.backend.farm.domain.structure.House;
import com.greenhouse.backend.farm.domain.structure.PhysicalBed;
import com.greenhouse.backend.farm.domain.variety.Variety;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.structure.HouseRepository;
import com.greenhouse.backend.farm.repository.variety.VarietyRepository;
import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.partner.domain.PartnerType;
import com.greenhouse.backend.partner.repository.BusinessPartnerRepository;
import com.greenhouse.backend.sales.application.SalesQueryService;
import com.greenhouse.backend.sales.application.SalesSlipCreationService;
import com.greenhouse.backend.sales.application.SalesSlipStatusService;
import com.greenhouse.backend.sales.domain.SalesOrchidSnapshotSource;
import com.greenhouse.backend.sales.domain.SalesOrchidSnapshotType;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesType;
import com.greenhouse.backend.sales.application.command.SalesSlipCommand;
import com.greenhouse.backend.sales.application.command.SalesSlipAllocationInput;
import com.greenhouse.backend.sales.application.command.SalesSlipItemInput;
import com.greenhouse.backend.sales.dto.SalesSlipStatusUpdateRequest;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SalesOrchidGroupSnapshotIntegrationTest {

	@Autowired SalesSlipCreationService creationService;
	@Autowired SalesSlipStatusService statusService;
	@Autowired SalesQueryService queryService;
	@Autowired HouseRepository houseRepository;
	@Autowired VarietyRepository varietyRepository;
	@Autowired OrchidGroupRepository orchidGroupRepository;
	@Autowired BusinessPartnerRepository partnerRepository;
	@Autowired AuctionShipmentRepository shipmentRepository;
	@Autowired EntityManager entityManager;

	@ParameterizedTest
	@EnumSource(SalesType.class)
	void preservesCreationAndPreOutboundOrchidGroupStatesInTheSalesTransaction(SalesType salesType) {
		House house = new House(990, "스냅샷 테스트동");
		PhysicalBed bed = new PhysicalBed(7, 1);
		BedZone zone = new BedZone("출하 구역", BedZoneSide.LEFT, 1);
		bed.addBedZone(zone);
		house.addPhysicalBed(bed);
		houseRepository.save(house);

		Variety variety = varietyRepository.save(new Variety(
				"SNAPSHOT-VARIETY", "팔레놉시스", "스냅샷 품종", null, "3.5치", true, true, null, null));
		OrchidGroup group = new OrchidGroup(
				zone,
				variety.getGenus(),
				variety.getName(),
				20,
				"3.5치",
				2,
				"정상",
				1,
				BigDecimal.ONE,
				BigDecimal.TEN);
		group.assignVariety(variety);
		group = orchidGroupRepository.save(group);
		BusinessPartner partner = partnerRepository.save(new BusinessPartner(
				"스냅샷 거래처", salesType == SalesType.DIRECT ? PartnerType.WHOLESALE : PartnerType.AUCTION_HOUSE,
				null, null, null, null));

		var created = creationService.create(new SalesSlipCommand(
				LocalDate.of(2026, 8, 12),
				salesType,
				partner.getId(),
				null,
				"미입금",
				SalesSlip.STATUS_DRAFT,
				null,
				null,
				List.of(new SalesSlipItemInput(
						variety.getName(),
						variety.getGenus(),
						null,
						2,
						10_000,
						null,
						List.of(new SalesSlipAllocationInput(group.getId(), 2))))));

		var creationSnapshot = created.items().getFirst().allocations().getFirst().creationSnapshot();
		assertThat(creationSnapshot.snapshotType()).isEqualTo(SalesOrchidSnapshotType.CREATION);
		assertThat(creationSnapshot.captureSource()).isEqualTo(SalesOrchidSnapshotSource.LIVE);
		assertThat(creationSnapshot.quantity()).isEqualTo(20);
		assertThat(creationSnapshot.reservedQuantity()).isZero();
		assertThat(creationSnapshot.status()).isEqualTo("정상");
		assertThat(creationSnapshot.houseNumber()).isEqualTo(990);
		assertThat(created.items().getFirst().allocations().getFirst().outboundSnapshot()).isNull();

		group.updateDetails(
				group.getGenus(),
				group.getVarietyName(),
				18,
				group.getPotSize(),
				3,
				"출하 준비",
				group.getPlacementType(),
				group.getTrayCount(),
				group.getSplitPlacementAllowed(),
				group.getStartPosition(),
				group.getEndPosition(),
				group.getMemo());

		var completed = statusService.updateStatus(
				created.id(),
				new SalesSlipStatusUpdateRequest(salesType == SalesType.DIRECT
						? SalesSlip.STATUS_DIRECT_OUTBOUND_COMPLETED : SalesSlip.STATUS_AUCTION_SHIPMENT_COMPLETED, null));
		var snapshots = completed.items().getFirst().allocations().getFirst();

		assertThat(snapshots.creationSnapshot().quantity()).isEqualTo(20);
		assertThat(snapshots.creationSnapshot().status()).isEqualTo("정상");
		assertThat(snapshots.outboundSnapshot().snapshotType()).isEqualTo(SalesOrchidSnapshotType.OUTBOUND);
		assertThat(snapshots.outboundSnapshot().captureSource()).isEqualTo(SalesOrchidSnapshotSource.LIVE);
		assertThat(snapshots.outboundSnapshot().quantity()).isEqualTo(18);
		assertThat(snapshots.outboundSnapshot().reservedQuantity()).isEqualTo(2);
		assertThat(snapshots.outboundSnapshot().status()).isEqualTo("출하 준비");
		assertThat(orchidGroupRepository.findById(group.getId()).orElseThrow().getQuantity()).isEqualTo(16);
		entityManager.flush();
		entityManager.clear();
		var persistedSnapshots = queryService.getSalesSlip(completed.id()).items().getFirst().allocations().getFirst();
		House nextHouse = new House(991, "이동한 테스트동");
		PhysicalBed nextBed = new PhysicalBed(8, 1);
		BedZone nextZone = new BedZone("이동 구역", BedZoneSide.LEFT, 1);
		nextBed.addBedZone(nextZone);
		nextHouse.addPhysicalBed(nextBed);
		houseRepository.save(nextHouse);
		orchidGroupRepository.findById(group.getId()).orElseThrow().moveTo(nextZone, 1, BigDecimal.TWO, BigDecimal.TEN);
		entityManager.flush();
		entityManager.clear();
		var reloaded = queryService.getSalesSlip(completed.id()).items().getFirst().allocations().getFirst();
		assertThat(reloaded.houseNumber()).isEqualTo(991);
		assertThat(reloaded.bedZoneName()).isEqualTo("이동 구역");
		assertThat(reloaded.creationSnapshot()).isEqualTo(persistedSnapshots.creationSnapshot());
		assertThat(reloaded.outboundSnapshot()).isEqualTo(persistedSnapshots.outboundSnapshot());
		if (salesType == SalesType.AUCTION) {
			entityManager.flush();
			entityManager.clear();
			var shipment = shipmentRepository.findWithLotsById(completed.auctionShipmentId()).orElseThrow();
			assertThat(shipment.getLots()).singleElement().satisfies(lot -> {
				assertThat(lot.getId()).isEqualTo(completed.items().getFirst().auctionShipmentLotId());
				assertThat(lot.getShippedQuantity()).isEqualTo(2);
				assertThat(lot.getVarietyName()).isEqualTo(variety.getName());
				assertThat(lot.getBoxes()).isNull();
			});
			statusService.updateStatus(completed.id(), new SalesSlipStatusUpdateRequest(SalesSlip.STATUS_CANCELED, null));
			entityManager.flush();
			entityManager.clear();
			assertThat(shipmentRepository.findById(completed.auctionShipmentId())).isEmpty();
			assertThat(orchidGroupRepository.findById(group.getId()).orElseThrow().getQuantity()).isEqualTo(18);
		}
	}
}
