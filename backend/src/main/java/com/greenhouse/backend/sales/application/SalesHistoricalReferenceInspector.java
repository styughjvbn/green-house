package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupHistoricalSalesReferenceCounts;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupHistoricalSalesReferenceInspector;
import com.greenhouse.backend.sales.repository.SalesInventoryMovementRepository;
import com.greenhouse.backend.sales.repository.SalesSlipItemAllocationRepository;
import com.greenhouse.backend.sales.repository.SalesSlipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SalesHistoricalReferenceInspector
		implements OrchidGroupHistoricalSalesReferenceInspector {

	private final SalesSlipRepository salesSlipRepository;
	private final SalesSlipItemAllocationRepository allocationRepository;
	private final SalesInventoryMovementRepository movementRepository;

	@Override
	@Transactional(readOnly = true)
	public OrchidGroupHistoricalSalesReferenceCounts inspect() {
		return new OrchidGroupHistoricalSalesReferenceCounts(
				salesSlipRepository.count(),
				salesSlipRepository.countItems(),
				allocationRepository.count(),
				movementRepository.count(),
				salesSlipRepository.countOrchidGroupSnapshots());
	}
}
