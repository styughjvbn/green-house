package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.farm.application.orchid.mutation.ConsumeOrchidGroupReservationsMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationEngine;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationResult;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationSources;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupQuantityMutationItem;
import com.greenhouse.backend.farm.application.orchid.mutation.RelatedOrchidGroupMutations;
import com.greenhouse.backend.farm.application.orchid.mutation.ReleaseOrchidGroupReservationsMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.ReserveOrchidGroupsMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.RestoreOutboundOrchidGroupsMutationCommand;
import com.greenhouse.backend.sales.domain.SalesInventoryMovement;
import com.greenhouse.backend.sales.domain.SalesInventoryMovementType;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.repository.SalesInventoryMovementRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SalesSlipInventoryService {

	private final SalesInventoryMovementRepository salesInventoryMovementRepository;

	private final OrchidGroupMutationEngine mutationEngine;

	public void reserve(SalesSlip salesSlip) {
		var allocations = SalesSlipAllocationBatch.from(salesSlip);
		if (allocations.lines().isEmpty()) {
			return;
		}
		var mutation = mutationEngine.reserve(new ReserveOrchidGroupsMutationCommand(
				OrchidGroupMutationSources.sales(salesSlip.getId(), "RESERVE:" + salesSlip.getVersion()),
				mutationItems(allocations), salesSlip.getSaleDate(), salesSlip.getMemo()));
		recordMovements(allocations, SalesInventoryMovementType.SALES_RESERVE, 1, mutation);
	}

	public void releaseForEdit(SalesSlip salesSlip) {
		release(salesSlip, "RELEASE_EDIT:", SalesInventoryMovementType.SALES_RELEASE);
	}

	public void cancelReserve(SalesSlip salesSlip) {
		release(salesSlip, "CANCEL_RESERVE:", SalesInventoryMovementType.SALES_CANCEL_RESERVE);
	}

	private void release(SalesSlip salesSlip, String operation, SalesInventoryMovementType type) {
		var allocations = SalesSlipAllocationBatch.from(salesSlip);
		if (allocations.lines().isEmpty()) {
			return;
		}
		var mutation = mutationEngine.releaseReservation(new ReleaseOrchidGroupReservationsMutationCommand(
				OrchidGroupMutationSources.sales(salesSlip.getId(), operation + salesSlip.getVersion()),
				mutationItems(allocations), salesSlip.getSaleDate(), salesSlip.getMemo()));
		recordMovements(allocations, type, -1, mutation);
	}

	void outbound(SalesSlipAllocationBatch allocations) {
		if (allocations.lines().isEmpty()) {
			return;
		}
		var salesSlip = allocations.salesSlip();
		var mutation = mutationEngine.consumeReservation(new ConsumeOrchidGroupReservationsMutationCommand(
				OrchidGroupMutationSources.sales(salesSlip.getId(), "OUTBOUND:" + salesSlip.getVersion()),
				mutationItems(allocations), salesSlip.getSaleDate(), salesSlip.getMemo()));
		recordMovements(allocations, SalesInventoryMovementType.SALES_OUTBOUND, -1, mutation);
	}

	public void cancelOutbound(SalesSlip salesSlip) {
		var allocations = SalesSlipAllocationBatch.from(salesSlip);
		if (allocations.lines().isEmpty()) {
			return;
		}
		var mutation = mutationEngine.restoreOutbound(
				new RestoreOutboundOrchidGroupsMutationCommand(
						OrchidGroupMutationSources.sales(salesSlip.getId(),
								"CANCEL_OUTBOUND:" + salesSlip.getVersion()),
						mutationItems(allocations), outboundMutationReferences(salesSlip), salesSlip.getSaleDate(),
						salesSlip.getMemo()));
		recordMovements(allocations, SalesInventoryMovementType.SALES_CANCEL_OUTBOUND, 1, mutation);
	}

	private List<OrchidGroupQuantityMutationItem> mutationItems(SalesSlipAllocationBatch allocations) {
		return allocations.lines()
			.stream()
			.collect(Collectors.groupingBy(SalesSlipAllocationBatch.Line::orchidGroupId,
					Collectors.summingInt(SalesSlipAllocationBatch.Line::allocatedQuantity)))
			.entrySet()
			.stream()
			.sorted(Map.Entry.comparingByKey())
			.map(entry -> new OrchidGroupQuantityMutationItem(entry.getKey(), entry.getValue()))
			.toList();
	}

	private RelatedOrchidGroupMutations outboundMutationReferences(SalesSlip salesSlip) {
		List<SalesInventoryMovement> outboundMovements = salesInventoryMovementRepository
			.findBySalesSlipIdAndChangeType(salesSlip.getId(), SalesInventoryMovementType.SALES_OUTBOUND);
		if (outboundMovements.isEmpty()
				|| outboundMovements.stream().anyMatch(movement -> movement.getMutationId() == null)) {
			return RelatedOrchidGroupMutations.legacy();
		}
		return RelatedOrchidGroupMutations.current(
				outboundMovements.stream().map(SalesInventoryMovement::getMutationId).distinct().sorted().toList());
	}

	private void recordMovements(SalesSlipAllocationBatch allocations, SalesInventoryMovementType type, int direction,
			OrchidGroupMutationResult mutation) {
		var salesSlip = allocations.salesSlip();
		for (var line : allocations.lines()) {
			var movement = new SalesInventoryMovement(line.orchidGroupId(), salesSlip, line.item(), type,
					direction * line.allocatedQuantity(), salesSlip.getMemo());
			movement.linkMutation(mutation.mutationId(), mutation.correlationId());
			salesInventoryMovementRepository.save(movement);
		}
	}

}
