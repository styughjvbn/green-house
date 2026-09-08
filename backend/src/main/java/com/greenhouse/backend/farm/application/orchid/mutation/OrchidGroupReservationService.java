package com.greenhouse.backend.farm.application.orchid.mutation;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import java.util.List;
import java.util.function.BiConsumer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * ORCHID-CUTOVER: LEGACY_RETIRE — owns reservation routing and the remaining direct stock
 * changes. Removal gate: 운영 ACTIVE 안정화 및 writer inventory 승인. Returns no Mutation for
 * Legacy writes; callers retain their business history in either mode.
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class OrchidGroupReservationService {

	private final OrchidGroupRepository orchidGroupRepository;

	private final OrchidGroupMutationEngine mutationEngine;

	private final OrchidGroupMutationRoutingPolicy routingPolicy;

	public OrchidGroupMutationResult reserve(ReserveOrchidGroupsMutationCommand command) {
		if (routingPolicy.routesToEngine()) {
			return mutationEngine.reserve(command);
		}
		applyLegacy(command.items(), (group, quantity) -> group.reserve(quantity));
		return null;
	}

	public OrchidGroupMutationResult releaseReservation(ReleaseOrchidGroupReservationsMutationCommand command) {
		if (routingPolicy.routesToEngine()) {
			return mutationEngine.releaseReservation(command);
		}
		applyLegacy(command.items(), (group, quantity) -> group.releaseReserved(quantity));
		return null;
	}

	public OrchidGroupMutationResult consumeReservation(ConsumeOrchidGroupReservationsMutationCommand command) {
		if (routingPolicy.routesToEngine()) {
			return mutationEngine.consumeReservation(command);
		}
		applyLegacy(command.items(), (group, quantity) -> group.outboundReserved(quantity));
		return null;
	}

	public OrchidGroupMutationResult restoreOutbound(RestoreOutboundOrchidGroupsMutationCommand command) {
		if (routingPolicy.routesToEngine()) {
			return mutationEngine.restoreOutbound(command);
		}
		applyLegacy(command.items(), (group, quantity) -> group.restoreOutbound(quantity));
		return null;
	}

	private void applyLegacy(List<OrchidGroupQuantityMutationItem> items, BiConsumer<OrchidGroup, Integer> change) {
		// Typed commands contain distinct IDs in ascending order; lock all rows before
		// changing any.
		var groups = orchidGroupRepository
			.findAllForUpdateByIdIn(items.stream().map(OrchidGroupQuantityMutationItem::orchidGroupId).toList());
		if (groups.size() != items.size()) {
			throw new NotFoundException("난 묶음을 찾을 수 없습니다.");
		}
		for (int index = 0; index < items.size(); index++) {
			change.accept(groups.get(index), items.get(index).quantity());
		}
	}

}
