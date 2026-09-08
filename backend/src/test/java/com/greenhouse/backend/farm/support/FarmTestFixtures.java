package com.greenhouse.backend.farm.support;

import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.structure.BedZone;
import com.greenhouse.backend.farm.domain.structure.BedZoneSide;
import com.greenhouse.backend.farm.domain.structure.House;
import com.greenhouse.backend.farm.domain.structure.PhysicalBed;
import com.greenhouse.backend.farm.domain.variety.Variety;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;

/**
 * Small, caller-owned fixtures; no production seeds, fixed IDs, cleanup or committed
 * writes.
 */
public final class FarmTestFixtures {

	private final EntityManager entityManager;

	public FarmTestFixtures(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	public Layout layout(int houseNumber) {
		var house = new House(houseNumber, houseNumber + "동");
		var bed = new PhysicalBed(1, 1);
		bed.updatePositionUnits(BigDecimal.valueOf(60), "칸");
		var left = new BedZone("좌", BedZoneSide.LEFT, 1);
		var right = new BedZone("우", BedZoneSide.RIGHT, 2);
		bed.addBedZone(left);
		bed.addBedZone(right);
		house.addPhysicalBed(bed);
		entityManager.persist(house);
		return new Layout(house, bed, left, right);
	}

	public OrchidGroup orchidGroup(BedZone zone, String varietyCode, int quantity) {
		var variety = new Variety(varietyCode, "난", varietyCode, null, "3치", true, true, null, null);
		entityManager.persist(variety);
		var group = new OrchidGroup(zone, variety.getGenus(), variety.getName(), quantity, "3치", 1, "정상", 1,
				BigDecimal.ZERO, BigDecimal.TEN);
		group.assignVariety(variety);
		entityManager.persist(group);
		return group;
	}

	public record Layout(House house, PhysicalBed bed, BedZone left, BedZone right) {
	}

}
