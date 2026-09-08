package com.greenhouse.backend.farm.support;

import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutation;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntry;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSource;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSourceDomain;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationType;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupStateSnapshot;
import com.greenhouse.backend.farm.domain.structure.BedZone;
import com.greenhouse.backend.farm.domain.structure.BedZoneSide;
import com.greenhouse.backend.farm.domain.structure.House;
import com.greenhouse.backend.farm.domain.structure.PhysicalBed;
import com.greenhouse.backend.farm.domain.variety.Variety;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

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
		baseline(entityManager, group);
		return group;
	}

	/**
	 * Explicit ledger fixture for tests that exercise the Engine, without cutover
	 * coverage.
	 */
	public static void baseline(EntityManager entityManager, OrchidGroup group) {
		if (group.getStateRevision() != null) {
			throw new IllegalArgumentException("이미 원장을 가진 테스트 난 묶음입니다.");
		}
		group.establishBaselineRevision();
		var mutation = new OrchidGroupMutation(OrchidGroupMutationType.BASELINE_IMPORT,
				new OrchidGroupMutationSource(OrchidGroupMutationSourceDomain.MIGRATION, "TEST_FIXTURE",
						group.getId().toString(), "BASELINE", UUID.randomUUID()),
				"f".repeat(64), Instant.parse("2026-07-01T00:00:00Z"), LocalDate.of(2026, 7, 1), "Test fixture", 1);
		entityManager.persist(mutation);
		entityManager
			.persist(OrchidGroupMutationEntry.baseline(mutation, group.getId(), OrchidGroupStateSnapshot.from(group)));
	}

	public record Layout(House house, PhysicalBed bed, BedZone left, BedZone right) {
	}

}
