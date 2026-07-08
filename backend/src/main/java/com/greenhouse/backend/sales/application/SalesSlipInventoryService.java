package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.farm.domain.OrchidGroup;
import com.greenhouse.backend.sales.domain.SalesInventoryMovement;
import com.greenhouse.backend.sales.domain.SalesInventoryMovementType;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesSlipItem;
import com.greenhouse.backend.sales.domain.SalesSlipItemAllocation;
import com.greenhouse.backend.sales.repository.SalesInventoryMovementRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SalesSlipInventoryService {

	private final SalesInventoryMovementRepository salesInventoryMovementRepository;
	private final EntityManager entityManager;

	public void reserve(SalesSlip salesSlip) {
		for (SalesSlipItem item : salesSlip.getItems()) {
			for (SalesSlipItemAllocation allocation : item.getAllocations()) {
				OrchidGroup orchidGroup = allocation.getOrchidGroup();
				orchidGroup.reserve(allocation.getAllocatedQuantity());
				salesInventoryMovementRepository.save(buildMovement(
						orchidGroup,
						salesSlip,
						item,
						SalesInventoryMovementType.SALES_RESERVE,
						allocation.getAllocatedQuantity()));
			}
		}
	}

	public void release(SalesSlip salesSlip) {
		for (SalesSlipItem item : salesSlip.getItems()) {
			for (SalesSlipItemAllocation allocation : item.getAllocations()) {
				OrchidGroup orchidGroup = allocation.getOrchidGroup();
				orchidGroup.releaseReserved(allocation.getAllocatedQuantity());
				salesInventoryMovementRepository.save(buildMovement(
						orchidGroup,
						salesSlip,
						item,
						SalesInventoryMovementType.SALES_RELEASE,
						-allocation.getAllocatedQuantity()));
			}
		}
	}

	public void releaseForEdit(SalesSlip salesSlip) {
		for (SalesSlipItem item : salesSlip.getItems()) {
			for (SalesSlipItemAllocation allocation : item.getAllocations()) {
				allocation.getOrchidGroup().releaseReserved(allocation.getAllocatedQuantity());
			}
		}
	}

	public void outbound(SalesSlip salesSlip) {
		for (SalesSlipItem item : salesSlip.getItems()) {
			for (SalesSlipItemAllocation allocation : item.getAllocations()) {
				OrchidGroup orchidGroup = allocation.getOrchidGroup();
				orchidGroup.outboundReserved(allocation.getAllocatedQuantity());
				salesInventoryMovementRepository.save(buildMovement(
						orchidGroup,
						salesSlip,
						item,
						SalesInventoryMovementType.SALES_OUTBOUND,
						-allocation.getAllocatedQuantity()));
			}
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
}
