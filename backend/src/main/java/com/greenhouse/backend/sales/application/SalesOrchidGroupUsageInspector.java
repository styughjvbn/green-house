package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.common.application.OrchidGroupUsage;
import com.greenhouse.backend.common.application.OrchidGroupUsageInspector;
import com.greenhouse.backend.sales.repository.SalesInventoryMovementRepository;
import com.greenhouse.backend.sales.repository.SalesSlipItemAllocationRepository;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class SalesOrchidGroupUsageInspector implements OrchidGroupUsageInspector {

	private final SalesSlipItemAllocationRepository allocationRepository;
	private final SalesInventoryMovementRepository movementRepository;

	public SalesOrchidGroupUsageInspector(
			SalesSlipItemAllocationRepository allocationRepository,
			SalesInventoryMovementRepository movementRepository) {
		this.allocationRepository = allocationRepository;
		this.movementRepository = movementRepository;
	}

	@Override
	public List<OrchidGroupUsage> inspect(Set<Long> orchidGroupIds, Long sourceWorkOperationId) {
		long count = allocationRepository.countByOrchidGroupIdIn(orchidGroupIds)
				+ movementRepository.countByOrchidGroupIdIn(orchidGroupIds);
		return count == 0
				? List.of()
				: List.of(new OrchidGroupUsage("SALES", "판매 또는 재고 이동에 연결된 난 묶음이 있습니다.", count));
	}
}
