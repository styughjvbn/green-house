package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.structure.BedZone;
import com.greenhouse.backend.farm.domain.structure.BedZoneSide;
import com.greenhouse.backend.farm.domain.structure.House;
import com.greenhouse.backend.farm.domain.structure.PhysicalBed;
import com.greenhouse.backend.farm.domain.variety.Variety;
import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.partner.domain.PartnerType;
import com.greenhouse.backend.partner.repository.BusinessPartnerRepository;
import com.greenhouse.backend.sales.application.SalesSlipCreationService;
import com.greenhouse.backend.sales.application.SalesSlipStatusService;
import com.greenhouse.backend.sales.domain.SalesInventoryMovementType;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesType;
import com.greenhouse.backend.sales.application.command.SalesSlipCommand;
import com.greenhouse.backend.sales.application.command.SalesSlipAllocationInput;
import com.greenhouse.backend.sales.application.command.SalesSlipItemInput;
import com.greenhouse.backend.sales.application.document.SalesSlipDocument;
import com.greenhouse.backend.sales.dto.SalesSlipStatusUpdateRequest;
import com.greenhouse.backend.sales.repository.SalesInventoryMovementRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class SalesInventoryMutationContractIntegrationTest extends AbstractBackendIntegrationTest {

	@Autowired private SalesSlipCreationService creationService;
	@Autowired private SalesSlipStatusService statusService;
	@Autowired private SalesInventoryMovementRepository movementRepository;
	@Autowired private BusinessPartnerRepository partnerRepository;

	@Test
	void reservationOutboundReplayAndCancellationPreserveInventoryAndMovementHistory() {
		Fixture fixture = createFixture("출고 수명주기");
		var created = createDraft(fixture, 2);

		assertGroupState(fixture.group().getId(), 20, 2);
		assertMovement(created.id(), SalesInventoryMovementType.SALES_RESERVE, 2);

		statusService.updateStatus(created.id(), new SalesSlipStatusUpdateRequest(
				SalesSlip.STATUS_DIRECT_OUTBOUND_COMPLETED, null));

		assertGroupState(fixture.group().getId(), 18, 0);
		assertMovement(created.id(), SalesInventoryMovementType.SALES_OUTBOUND, -2);

		statusService.updateStatus(created.id(), new SalesSlipStatusUpdateRequest(
				SalesSlip.STATUS_DIRECT_OUTBOUND_COMPLETED, null));

		assertGroupState(fixture.group().getId(), 18, 0);
		assertThat(movementRepository.findBySalesSlipIdAndChangeType(
				created.id(), SalesInventoryMovementType.SALES_OUTBOUND)).hasSize(1);

		statusService.updateStatus(created.id(), new SalesSlipStatusUpdateRequest(
				SalesSlip.STATUS_CANCELED, null));

		assertGroupState(fixture.group().getId(), 20, 0);
		assertMovement(created.id(), SalesInventoryMovementType.SALES_CANCEL_OUTBOUND, 2);
	}

	@Test
	void draftCancellationReleasesReservationWithoutChangingStock() {
		Fixture fixture = createFixture("예약 취소");
		var created = createDraft(fixture, 3);

		assertGroupState(fixture.group().getId(), 20, 3);

		statusService.updateStatus(created.id(), new SalesSlipStatusUpdateRequest(
				SalesSlip.STATUS_CANCELED, null));

		assertGroupState(fixture.group().getId(), 20, 0);
		assertMovement(created.id(), SalesInventoryMovementType.SALES_RESERVE, 3);
		assertMovement(created.id(), SalesInventoryMovementType.SALES_CANCEL_RESERVE, -3);
		assertThat(movementRepository.findBySalesSlipIdAndChangeType(
				created.id(), SalesInventoryMovementType.SALES_OUTBOUND)).isEmpty();
	}

	private SalesSlipDocument createDraft(Fixture fixture, int quantity) {
		return creationService.create(new SalesSlipCommand(
				LocalDate.of(2026, 8, 20),
				SalesType.DIRECT,
				fixture.partner().getId(),
				null,
				"미입금",
				SalesSlip.STATUS_DRAFT,
				null,
				"특성 테스트",
				List.of(new SalesSlipItemInput(
						fixture.variety().getName(),
						fixture.variety().getGenus(),
						"4인치",
						quantity,
						10_000,
						null,
						List.of(new SalesSlipAllocationInput(fixture.group().getId(), quantity))))));
	}

	private Fixture createFixture(String suffix) {
		House house = new House(9930, "판매 재고 특성 테스트동");
		PhysicalBed bed = new PhysicalBed(1, 1);
		BedZone zone = new BedZone("출하 구역", BedZoneSide.LEFT, 1);
		bed.addBedZone(zone);
		house.addPhysicalBed(bed);
		houseRepository.saveAndFlush(house);

		Variety variety = varietyRepository.saveAndFlush(new Variety(
				"SALE-CONTRACT-" + System.nanoTime(),
				"팔레놉시스",
				"판매 특성 " + suffix,
				null,
				"4인치",
				true,
				true,
				null,
				null));
		OrchidGroup group = new OrchidGroup(
				zone,
				variety.getGenus(),
				variety.getName(),
				20,
				"4인치",
				2,
				"정상",
				1,
				BigDecimal.ONE,
				BigDecimal.TWO);
		group.assignVariety(variety);
		group = orchidGroupRepository.saveAndFlush(group);
		BusinessPartner partner = partnerRepository.saveAndFlush(new BusinessPartner(
				"판매 특성 거래처 " + suffix,
				PartnerType.WHOLESALE,
				null,
				null,
				null,
				null));
		return new Fixture(variety, group, partner);
	}

	private void assertGroupState(Long orchidGroupId, int quantity, int reservedQuantity) {
		OrchidGroup group = orchidGroupRepository.findById(orchidGroupId).orElseThrow();
		assertThat(group.getQuantity()).isEqualTo(quantity);
		assertThat(group.getReservedQuantity()).isEqualTo(reservedQuantity);
	}

	private void assertMovement(Long salesSlipId, SalesInventoryMovementType type, int quantityDelta) {
		assertThat(movementRepository.findBySalesSlipIdAndChangeType(salesSlipId, type))
				.singleElement()
				.satisfies(movement -> assertThat(movement.getQuantityDelta()).isEqualTo(quantityDelta));
	}

	private record Fixture(Variety variety, OrchidGroup group, BusinessPartner partner) {
	}
}
