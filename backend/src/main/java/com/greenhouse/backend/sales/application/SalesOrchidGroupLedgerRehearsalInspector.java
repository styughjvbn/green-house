package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerReconciliationGroup;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerReconciliationIssue;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerRehearsalInspector;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.repository.SalesInventoryMovementRepository;
import com.greenhouse.backend.sales.repository.SalesSlipItemAllocationRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SalesOrchidGroupLedgerRehearsalInspector
		implements OrchidGroupLedgerRehearsalInspector {

	private static final int BATCH_SIZE = 500;

	private final SalesSlipItemAllocationRepository allocationRepository;
	private final SalesInventoryMovementRepository movementRepository;

	public SalesOrchidGroupLedgerRehearsalInspector(
			SalesSlipItemAllocationRepository allocationRepository,
			SalesInventoryMovementRepository movementRepository) {
		this.allocationRepository = allocationRepository;
		this.movementRepository = movementRepository;
	}

	@Override
	public List<OrchidGroupLedgerReconciliationIssue> inspect(
			List<OrchidGroupLedgerReconciliationGroup> groups) {
		Map<Long, Long> draftReservationsByGroupId = new HashMap<>();
		List<Long> groupIds = groups.stream()
				.map(OrchidGroupLedgerReconciliationGroup::orchidGroupId)
				.toList();
		for (int offset = 0; offset < groupIds.size(); offset += BATCH_SIZE) {
			allocationRepository.sumDraftReservationsByOrchidGroupIdIn(
					groupIds.subList(offset, Math.min(offset + BATCH_SIZE, groupIds.size())),
					SalesSlip.STATUS_DRAFT)
					.forEach(row -> draftReservationsByGroupId.put(
							row.orchidGroupId(), row.allocatedQuantity()));
		}

		List<OrchidGroupLedgerReconciliationIssue> issues = new ArrayList<>();
		for (OrchidGroupLedgerReconciliationGroup group : groups) {
			long expected = draftReservationsByGroupId.getOrDefault(group.orchidGroupId(), 0L);
			Integer actual = group.snapshot().reservedQuantity();
			if (actual == null || actual.longValue() != expected) {
				issues.add(new OrchidGroupLedgerReconciliationIssue(
						"SALES_RESERVATION_MISMATCH",
						"SALES",
						group.orchidGroupId().toString(),
						"작성중 판매 allocation 합계 " + expected
								+ "와 reservedQuantity " + actual + "가 다릅니다."));
			}
		}
		movementRepository.findIdsWithIncompleteMutationLink().forEach(movementId ->
				issues.add(new OrchidGroupLedgerReconciliationIssue(
						"INCOMPLETE_SALES_MUTATION_LINK",
						"SALES",
						movementId.toString(),
						"SalesInventoryMovement의 mutationId와 correlationId가 함께 설정되지 않았습니다.")));
		return issues;
	}
}
