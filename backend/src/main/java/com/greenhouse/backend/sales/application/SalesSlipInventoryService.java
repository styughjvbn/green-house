package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.farm.domain.OrchidGroup;
import com.greenhouse.backend.sales.domain.SalesInventoryMovement;
import com.greenhouse.backend.sales.domain.SalesInventoryMovementType;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesSlipItem;
import com.greenhouse.backend.sales.domain.SalesSlipItemAllocation;
import com.greenhouse.backend.sales.repository.SalesInventoryMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SalesSlipInventoryService {

	private final SalesInventoryMovementRepository salesInventoryMovementRepository;

	public void reserve(SalesSlip salesSlip) {
		for (SalesSlipItem item : salesSlip.getItems()) {
			for (SalesSlipItemAllocation allocation : item.getAllocations()) {
				OrchidGroup orchidGroup = allocation.getOrchidGroup();
				orchidGroup.reserve(allocation.getAllocatedQuantity());
				salesInventoryMovementRepository.save(new SalesInventoryMovement(
						orchidGroup,
						salesSlip,
						item,
						SalesInventoryMovementType.SALES_RESERVE,
						allocation.getAllocatedQuantity(),
						salesSlip.getMemo()));
			}
		}
	}

	public void release(SalesSlip salesSlip) {
		for (SalesSlipItem item : salesSlip.getItems()) {
			for (SalesSlipItemAllocation allocation : item.getAllocations()) {
				OrchidGroup orchidGroup = allocation.getOrchidGroup();
				orchidGroup.releaseReserved(allocation.getAllocatedQuantity());
				salesInventoryMovementRepository.save(new SalesInventoryMovement(
						orchidGroup,
						salesSlip,
						item,
						SalesInventoryMovementType.SALES_RELEASE,
						-allocation.getAllocatedQuantity(),
						salesSlip.getMemo()));
			}
		}
	}

	public void outbound(SalesSlip salesSlip) {
		for (SalesSlipItem item : salesSlip.getItems()) {
			for (SalesSlipItemAllocation allocation : item.getAllocations()) {
				OrchidGroup orchidGroup = allocation.getOrchidGroup();
				orchidGroup.outboundReserved(allocation.getAllocatedQuantity());
				salesInventoryMovementRepository.save(new SalesInventoryMovement(
						orchidGroup,
						salesSlip,
						item,
						SalesInventoryMovementType.SALES_OUTBOUND,
						-allocation.getAllocatedQuantity(),
						salesSlip.getMemo()));
			}
		}
	}
}
