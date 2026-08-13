package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.greenhouse.backend.sales.application.SalesSlipCreationService;
import com.greenhouse.backend.sales.application.SalesSlipStatusService;
import com.greenhouse.backend.sales.domain.SalesOrchidSnapshotSource;
import com.greenhouse.backend.sales.domain.SalesOrchidSnapshotType;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesType;
import com.greenhouse.backend.sales.dto.SalesSlipCreateRequest;
import com.greenhouse.backend.sales.dto.SalesSlipItemAllocationRequest;
import com.greenhouse.backend.sales.dto.SalesSlipItemRequest;
import com.greenhouse.backend.sales.dto.SalesSlipStatusUpdateRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
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
	@Autowired HouseRepository houseRepository;
	@Autowired VarietyRepository varietyRepository;
	@Autowired OrchidGroupRepository orchidGroupRepository;
	@Autowired BusinessPartnerRepository partnerRepository;

	@Test
	void preservesCreationAndPreOutboundOrchidGroupStatesInTheSalesTransaction() {
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
				"스냅샷 거래처", PartnerType.WHOLESALE, null, null, null, null));

		var created = creationService.create(new SalesSlipCreateRequest(
				LocalDate.of(2026, 8, 12),
				SalesType.DIRECT,
				partner.getId(),
				null,
				"미입금",
				SalesSlip.STATUS_DRAFT,
				null,
				null,
				List.of(new SalesSlipItemRequest(
						variety.getName(),
						variety.getGenus(),
						null,
						2,
						10_000,
						null,
						List.of(new SalesSlipItemAllocationRequest(group.getId(), 2))))));

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
				new SalesSlipStatusUpdateRequest(SalesSlip.STATUS_DIRECT_OUTBOUND_COMPLETED, null));
		var snapshots = completed.items().getFirst().allocations().getFirst();

		assertThat(snapshots.creationSnapshot().quantity()).isEqualTo(20);
		assertThat(snapshots.creationSnapshot().status()).isEqualTo("정상");
		assertThat(snapshots.outboundSnapshot().snapshotType()).isEqualTo(SalesOrchidSnapshotType.OUTBOUND);
		assertThat(snapshots.outboundSnapshot().captureSource()).isEqualTo(SalesOrchidSnapshotSource.LIVE);
		assertThat(snapshots.outboundSnapshot().quantity()).isEqualTo(18);
		assertThat(snapshots.outboundSnapshot().reservedQuantity()).isEqualTo(2);
		assertThat(snapshots.outboundSnapshot().status()).isEqualTo("출하 준비");
		assertThat(orchidGroupRepository.findById(group.getId()).orElseThrow().getQuantity()).isEqualTo(16);
	}
}
