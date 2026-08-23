package com.greenhouse.backend.farm.domain.orchid;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupStateSnapshot;
import com.greenhouse.backend.farm.domain.structure.BedZone;
import com.greenhouse.backend.farm.domain.variety.Variety;
import java.math.BigDecimal;

/**
 * ORCHID-CUTOVER: TRANSITION_ONLY — SHADOW 예상 상태 계산에만 사용한다.
 * Removal gate: 운영 ACTIVE 안정화 및 SHADOW 종료.
 */
public final class OrchidGroupStateSimulation {

	private final OrchidGroup group;

	private OrchidGroupStateSimulation(OrchidGroup group) {
		this.group = group;
	}

	public static OrchidGroupStateSimulation from(OrchidGroup source) {
		if (source == null) {
			throw new IllegalArgumentException("Simulation 원본 난 묶음이 필요합니다.");
		}
		return new OrchidGroupStateSimulation(source.copyForSimulation());
	}

	public static OrchidGroupStateSimulation created(
			BedZone bedZone,
			Variety variety,
			Integer quantity,
			String potSize,
			Integer ageYear,
			String status,
			Integer sortOrder,
			BigDecimal startPosition,
			BigDecimal endPosition,
			String placementType,
			Integer trayCount,
			Boolean splitPlacementAllowed,
			String memo) {
		OrchidGroup group = new OrchidGroup(
				bedZone,
				variety.getGenus(),
				variety.getName(),
				quantity,
				potSize,
				ageYear,
				status,
				sortOrder,
				startPosition,
				endPosition);
		group.updateDetails(
				variety.getGenus(),
				variety.getName(),
				quantity,
				potSize,
				ageYear,
				status,
				placementType,
				trayCount,
				splitPlacementAllowed,
				startPosition,
				endPosition,
				memo);
		group.assignVariety(variety);
		return new OrchidGroupStateSimulation(group);
	}

	public OrchidGroupStateSnapshot snapshot() {
		return OrchidGroupStateSnapshot.from(group);
	}

	public void updateDetails(
			Variety variety,
			Integer quantity,
			String potSize,
			Integer ageYear,
			String status,
			String placementType,
			Integer trayCount,
			Boolean splitPlacementAllowed,
			BigDecimal startPosition,
			BigDecimal endPosition,
			String memo) {
		group.updateDetails(
				variety.getGenus(),
				variety.getName(),
				quantity,
				potSize,
				ageYear,
				status,
				placementType,
				trayCount,
				splitPlacementAllowed,
				startPosition,
				endPosition,
				memo);
		group.assignVariety(variety);
	}

	public void moveTo(BedZone bedZone, Integer sortOrder, BigDecimal startPosition, BigDecimal endPosition) {
		group.moveTo(bedZone, sortOrder, startPosition, endPosition);
	}

	public void cancelCreation() {
		group.cancelCreation();
	}

	public void transform(Integer quantity, BigDecimal releasedStartPosition, BigDecimal releasedEndPosition) {
		group.applyTransformation(quantity, releasedStartPosition, releasedEndPosition);
	}

	public void discard(Integer quantity) {
		group.discard(quantity);
	}

	public void reserve(Integer quantity) {
		group.reserve(quantity);
	}

	public void releaseReserved(Integer quantity) {
		group.releaseReserved(quantity);
	}

	public void outboundReserved(Integer quantity) {
		group.outboundReserved(quantity);
	}

	public void restoreOutbound(Integer quantity) {
		group.restoreOutbound(quantity);
	}

	public void correctQuantityAndStatus(Integer quantity, String status) {
		group.correctQuantityAndStatus(quantity, status);
	}
}
