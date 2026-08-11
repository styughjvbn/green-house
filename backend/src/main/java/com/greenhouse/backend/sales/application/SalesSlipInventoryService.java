package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.farm.application.orchid.OrchidGroupReader;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.sales.domain.SalesInventoryMovement;
import com.greenhouse.backend.sales.domain.SalesInventoryMovementType;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesSlipItem;
import com.greenhouse.backend.sales.repository.SalesInventoryMovementRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SalesSlipInventoryService {

	private final SalesInventoryMovementRepository salesInventoryMovementRepository;
	private final EntityManager entityManager;
	private final OrchidGroupReader orchidGroupReader;

	public void reserve(SalesSlip salesSlip) {
		SalesSlipAllocationBatch allocations = lockForUpdate(salesSlip);
		for (SalesSlipAllocationBatch.Line line : allocations.lines()) {
			OrchidGroup orchidGroup = line.orchidGroup();
			orchidGroup.reserve(line.allocatedQuantity());
			salesInventoryMovementRepository.save(buildMovement(
					orchidGroup,
					salesSlip,
					line.item(),
					SalesInventoryMovementType.SALES_RESERVE,
					line.allocatedQuantity()));
		}
	}

	public void release(SalesSlip salesSlip) {
		SalesSlipAllocationBatch allocations = lockForUpdate(salesSlip);
		for (SalesSlipAllocationBatch.Line line : allocations.lines()) {
			OrchidGroup orchidGroup = line.orchidGroup();
			orchidGroup.releaseReserved(line.allocatedQuantity());
			salesInventoryMovementRepository.save(buildMovement(
					orchidGroup,
					salesSlip,
					line.item(),
					SalesInventoryMovementType.SALES_RELEASE,
					-line.allocatedQuantity()));
		}
	}

	public void cancelReserve(SalesSlip salesSlip) {
		SalesSlipAllocationBatch allocations = lockForUpdate(salesSlip);
		for (SalesSlipAllocationBatch.Line line : allocations.lines()) {
			OrchidGroup orchidGroup = line.orchidGroup();
			orchidGroup.releaseReserved(line.allocatedQuantity());
			salesInventoryMovementRepository.save(buildMovement(
					orchidGroup,
					salesSlip,
					line.item(),
					SalesInventoryMovementType.SALES_CANCEL_RESERVE,
					-line.allocatedQuantity()));
		}
	}

	public void releaseForEdit(SalesSlip salesSlip) {
		release(salesSlip);
	}

	void outbound(SalesSlipAllocationBatch allocations) {
		for (SalesSlipAllocationBatch.Line line : allocations.lines()) {
			OrchidGroup orchidGroup = line.orchidGroup();
			orchidGroup.outboundReserved(line.allocatedQuantity());
			salesInventoryMovementRepository.save(buildMovement(
					orchidGroup,
					allocations.salesSlip(),
					line.item(),
					SalesInventoryMovementType.SALES_OUTBOUND,
					-line.allocatedQuantity()));
		}
	}

	public void cancelOutbound(SalesSlip salesSlip) {
		SalesSlipAllocationBatch allocations = lockForUpdate(salesSlip);
		for (SalesSlipAllocationBatch.Line line : allocations.lines()) {
			OrchidGroup orchidGroup = line.orchidGroup();
			orchidGroup.restoreOutbound(line.allocatedQuantity());
			salesInventoryMovementRepository.save(buildMovement(
					orchidGroup,
					salesSlip,
					line.item(),
					SalesInventoryMovementType.SALES_CANCEL_OUTBOUND,
					line.allocatedQuantity()));
		}
	}

	private SalesInventoryMovement buildMovement(
			OrchidGroup orchidGroup,
			SalesSlip salesSlip,
			SalesSlipItem salesSlipItem,
			SalesInventoryMovementType changeType,
			Integer quantityDelta) {
		var groupRef = entityManager.getReference(OrchidGroup.class, orchidGroup.getId());
		var slipRef = entityManager.getReference(SalesSlip.class, salesSlip.getId());
		var itemRef = entityManager.getReference(SalesSlipItem.class, salesSlipItem.getId());
		return new SalesInventoryMovement(groupRef, slipRef, itemRef, changeType, quantityDelta, salesSlip.getMemo());
	}

	public SalesSlipAllocationBatch lockForUpdate(SalesSlip salesSlip) {
		SalesSlipAllocationBatch allocations = SalesSlipAllocationBatch.from(salesSlip);
		orchidGroupReader.findAllForUpdateByIds(allocations.orchidGroupIds());
		return allocations;
	}
}
