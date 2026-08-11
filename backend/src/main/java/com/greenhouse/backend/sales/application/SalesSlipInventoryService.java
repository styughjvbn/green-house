package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.farm.application.orchid.OrchidGroupReader;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.sales.domain.SalesInventoryMovement;
import com.greenhouse.backend.sales.domain.SalesInventoryMovementType;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesSlipItem;
import com.greenhouse.backend.sales.domain.SalesSlipItemAllocation;
import com.greenhouse.backend.sales.repository.SalesInventoryMovementRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SalesSlipInventoryService {

	private final SalesInventoryMovementRepository salesInventoryMovementRepository;
	private final EntityManager entityManager;
	private final OrchidGroupReader orchidGroupReader;

	public void reserve(SalesSlip salesSlip) {
		lockAllocations(salesSlip);
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
		lockAllocations(salesSlip);
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

	public void cancelReserve(SalesSlip salesSlip) {
		lockAllocations(salesSlip);
		for (SalesSlipItem item : salesSlip.getItems()) {
			for (SalesSlipItemAllocation allocation : item.getAllocations()) {
				OrchidGroup orchidGroup = allocation.getOrchidGroup();
				orchidGroup.releaseReserved(allocation.getAllocatedQuantity());
				salesInventoryMovementRepository.save(buildMovement(
						orchidGroup,
						salesSlip,
						item,
						SalesInventoryMovementType.SALES_CANCEL_RESERVE,
						-allocation.getAllocatedQuantity()));
			}
		}
	}

	public void releaseForEdit(SalesSlip salesSlip) {
		release(salesSlip);
	}

	public void outbound(SalesSlip salesSlip) {
		lockAllocations(salesSlip);
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

	public void cancelOutbound(SalesSlip salesSlip) {
		lockAllocations(salesSlip);
		for (SalesSlipItem item : salesSlip.getItems()) {
			for (SalesSlipItemAllocation allocation : item.getAllocations()) {
				OrchidGroup orchidGroup = allocation.getOrchidGroup();
				orchidGroup.restoreOutbound(allocation.getAllocatedQuantity());
				salesInventoryMovementRepository.save(buildMovement(
						orchidGroup,
						salesSlip,
						item,
						SalesInventoryMovementType.SALES_CANCEL_OUTBOUND,
						allocation.getAllocatedQuantity()));
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

	private void lockAllocations(SalesSlip salesSlip) {
		List<Long> orchidGroupIds = salesSlip.getItems().stream()
				.flatMap(item -> item.getAllocations().stream())
				.map(allocation -> allocation.getOrchidGroup().getId())
				.distinct()
				.sorted()
				.toList();
		orchidGroupReader.findAllForUpdateByIds(orchidGroupIds);
	}
}
