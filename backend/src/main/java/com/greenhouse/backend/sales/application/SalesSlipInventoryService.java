package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.farm.application.orchid.OrchidGroupReader;
import com.greenhouse.backend.farm.application.orchid.mutation.ConsumeOrchidGroupReservationsMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationEngine;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationResult;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationRoutingPolicy;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationShadowService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationSources;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupQuantityMutationItem;
import com.greenhouse.backend.farm.application.orchid.mutation.RelatedOrchidGroupMutations;
import com.greenhouse.backend.farm.application.orchid.mutation.ReleaseOrchidGroupReservationsMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.ReserveOrchidGroupsMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.RestoreOutboundOrchidGroupsMutationCommand;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.sales.domain.SalesInventoryMovement;
import com.greenhouse.backend.sales.domain.SalesInventoryMovementType;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesSlipItem;
import com.greenhouse.backend.sales.repository.SalesInventoryMovementRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * ORCHID-CUTOVER: LEGACY_RETIRE — Engine 경로와 전환 후 제거할 직접 재고 변경 분기를 함께 가진다.
 * Removal gate: 운영 ACTIVE 안정화 및 writer inventory 승인.
 */
@Service
@RequiredArgsConstructor
public class SalesSlipInventoryService {

	private final SalesInventoryMovementRepository salesInventoryMovementRepository;
	private final EntityManager entityManager;
	private final OrchidGroupReader orchidGroupReader;
	private final OrchidGroupMutationEngine mutationEngine;
	private final OrchidGroupMutationRoutingPolicy mutationRoutingPolicy;
	private final OrchidGroupMutationShadowService mutationShadowService;

	public void reserve(SalesSlip salesSlip) {
		SalesSlipAllocationBatch allocations = lockForUpdate(salesSlip);
		var command = mutationRoutingPolicy.usesMutationContract()
				? new ReserveOrchidGroupsMutationCommand(
				OrchidGroupMutationSources.sales(
						salesSlip.getId(), "RESERVE:" + salesSlip.getVersion()),
				mutationItems(allocations),
				salesSlip.getSaleDate(),
				salesSlip.getMemo())
				: null;
		var shadowPlan = mutationShadowService.prepare(command);
		OrchidGroupMutationResult mutation = mutationRoutingPolicy.routesToEngine()
				? mutationEngine.reserve(command)
				: null;
		for (SalesSlipAllocationBatch.Line line : allocations.lines()) {
			OrchidGroup orchidGroup = line.orchidGroup();
			if (mutation == null) {
				orchidGroup.reserve(line.allocatedQuantity());
			}
			salesInventoryMovementRepository.save(linkMutation(buildMovement(
					orchidGroup,
					salesSlip,
					line.item(),
					SalesInventoryMovementType.SALES_RESERVE,
					line.allocatedQuantity()), mutation));
		}
		mutationShadowService.complete(shadowPlan);
	}

	public void release(SalesSlip salesSlip) {
		SalesSlipAllocationBatch allocations = lockForUpdate(salesSlip);
		var command = mutationRoutingPolicy.usesMutationContract()
				? new ReleaseOrchidGroupReservationsMutationCommand(
				OrchidGroupMutationSources.sales(
						salesSlip.getId(), "RELEASE_EDIT:" + salesSlip.getVersion()),
				mutationItems(allocations),
				salesSlip.getSaleDate(),
				salesSlip.getMemo())
				: null;
		var shadowPlan = mutationShadowService.prepare(command);
		OrchidGroupMutationResult mutation = mutationRoutingPolicy.routesToEngine()
				? mutationEngine.releaseReservation(command)
				: null;
		for (SalesSlipAllocationBatch.Line line : allocations.lines()) {
			OrchidGroup orchidGroup = line.orchidGroup();
			if (mutation == null) {
				orchidGroup.releaseReserved(line.allocatedQuantity());
			}
			salesInventoryMovementRepository.save(linkMutation(buildMovement(
					orchidGroup,
					salesSlip,
					line.item(),
					SalesInventoryMovementType.SALES_RELEASE,
					-line.allocatedQuantity()), mutation));
		}
		mutationShadowService.complete(shadowPlan);
	}

	public void cancelReserve(SalesSlip salesSlip) {
		SalesSlipAllocationBatch allocations = lockForUpdate(salesSlip);
		var command = mutationRoutingPolicy.usesMutationContract()
				? new ReleaseOrchidGroupReservationsMutationCommand(
				OrchidGroupMutationSources.sales(
						salesSlip.getId(), "CANCEL_RESERVE:" + salesSlip.getVersion()),
				mutationItems(allocations),
				salesSlip.getSaleDate(),
				salesSlip.getMemo())
				: null;
		var shadowPlan = mutationShadowService.prepare(command);
		OrchidGroupMutationResult mutation = mutationRoutingPolicy.routesToEngine()
				? mutationEngine.releaseReservation(command)
				: null;
		for (SalesSlipAllocationBatch.Line line : allocations.lines()) {
			OrchidGroup orchidGroup = line.orchidGroup();
			if (mutation == null) {
				orchidGroup.releaseReserved(line.allocatedQuantity());
			}
			salesInventoryMovementRepository.save(linkMutation(buildMovement(
					orchidGroup,
					salesSlip,
					line.item(),
					SalesInventoryMovementType.SALES_CANCEL_RESERVE,
					-line.allocatedQuantity()), mutation));
		}
		mutationShadowService.complete(shadowPlan);
	}

	public void releaseForEdit(SalesSlip salesSlip) {
		release(salesSlip);
	}

	void outbound(SalesSlipAllocationBatch allocations) {
		SalesSlip salesSlip = allocations.salesSlip();
		var command = mutationRoutingPolicy.usesMutationContract()
				? new ConsumeOrchidGroupReservationsMutationCommand(
				OrchidGroupMutationSources.sales(
						salesSlip.getId(), "OUTBOUND:" + salesSlip.getVersion()),
				mutationItems(allocations),
				salesSlip.getSaleDate(),
				salesSlip.getMemo())
				: null;
		var shadowPlan = mutationShadowService.prepare(command);
		OrchidGroupMutationResult mutation = mutationRoutingPolicy.routesToEngine()
				? mutationEngine.consumeReservation(command)
				: null;
		for (SalesSlipAllocationBatch.Line line : allocations.lines()) {
			OrchidGroup orchidGroup = line.orchidGroup();
			if (mutation == null) {
				orchidGroup.outboundReserved(line.allocatedQuantity());
			}
			salesInventoryMovementRepository.save(linkMutation(buildMovement(
					orchidGroup,
					allocations.salesSlip(),
					line.item(),
					SalesInventoryMovementType.SALES_OUTBOUND,
					-line.allocatedQuantity()), mutation));
		}
		mutationShadowService.complete(shadowPlan);
	}

	public void cancelOutbound(SalesSlip salesSlip) {
		SalesSlipAllocationBatch allocations = lockForUpdate(salesSlip);
		var command = mutationRoutingPolicy.usesMutationContract()
				? new RestoreOutboundOrchidGroupsMutationCommand(
				OrchidGroupMutationSources.sales(
						salesSlip.getId(), "CANCEL_OUTBOUND:" + salesSlip.getVersion()),
				mutationItems(allocations),
				outboundMutationReferences(salesSlip),
				salesSlip.getSaleDate(),
				salesSlip.getMemo())
				: null;
		var shadowPlan = mutationShadowService.prepare(command);
		OrchidGroupMutationResult mutation = mutationRoutingPolicy.routesToEngine()
				? mutationEngine.restoreOutbound(command)
				: null;
		for (SalesSlipAllocationBatch.Line line : allocations.lines()) {
			OrchidGroup orchidGroup = line.orchidGroup();
			if (mutation == null) {
				orchidGroup.restoreOutbound(line.allocatedQuantity());
			}
			salesInventoryMovementRepository.save(linkMutation(buildMovement(
					orchidGroup,
					salesSlip,
					line.item(),
					SalesInventoryMovementType.SALES_CANCEL_OUTBOUND,
					line.allocatedQuantity()), mutation));
		}
		mutationShadowService.complete(shadowPlan);
	}

	private List<OrchidGroupQuantityMutationItem> mutationItems(
			SalesSlipAllocationBatch allocations) {
		return allocations.lines().stream()
				.collect(Collectors.groupingBy(
						line -> line.orchidGroup().getId(),
						Collectors.summingInt(SalesSlipAllocationBatch.Line::allocatedQuantity)))
				.entrySet().stream()
				.sorted(Map.Entry.comparingByKey())
				.map(entry -> new OrchidGroupQuantityMutationItem(entry.getKey(), entry.getValue()))
				.toList();
	}

	private RelatedOrchidGroupMutations outboundMutationReferences(SalesSlip salesSlip) {
		List<SalesInventoryMovement> outboundMovements = salesInventoryMovementRepository
				.findBySalesSlipIdAndChangeType(
						salesSlip.getId(), SalesInventoryMovementType.SALES_OUTBOUND);
		if (outboundMovements.isEmpty()
				|| outboundMovements.stream().anyMatch(movement -> movement.getMutationId() == null)) {
			return RelatedOrchidGroupMutations.legacy();
		}
		return RelatedOrchidGroupMutations.current(outboundMovements.stream()
				.map(SalesInventoryMovement::getMutationId)
				.distinct()
				.sorted()
				.toList());
	}

	private SalesInventoryMovement linkMutation(
			SalesInventoryMovement movement,
			OrchidGroupMutationResult mutation) {
		if (mutation != null) {
			movement.linkMutation(mutation.mutationId(), mutation.correlationId());
		}
		return movement;
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
		return lockForUpdate(salesSlip, false);
	}

	public SalesSlipAllocationBatch lockForOutbound(SalesSlip salesSlip) {
		return lockForUpdate(salesSlip, true);
	}

	private SalesSlipAllocationBatch lockForUpdate(SalesSlip salesSlip, boolean loadSnapshotDetails) {
		SalesSlipAllocationBatch allocations = SalesSlipAllocationBatch.from(salesSlip);
		List<OrchidGroup> lockedGroups = loadSnapshotDetails
				? orchidGroupReader.findAllDetailsForUpdateByIds(allocations.orchidGroupIds())
				: orchidGroupReader.findAllForUpdateByIds(allocations.orchidGroupIds());
		if (lockedGroups.size() != allocations.orchidGroupIds().size()) {
			throw new NotFoundException("난 묶음을 찾을 수 없습니다.");
		}
		return allocations;
	}
}
