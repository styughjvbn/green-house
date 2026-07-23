package com.greenhouse.backend;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.greenhouse.backend.farm.domain.structure.BedZone;
import com.greenhouse.backend.farm.domain.structure.BedZoneSide;
import com.greenhouse.backend.farm.domain.structure.House;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.structure.PhysicalBed;
import com.greenhouse.backend.farm.domain.variety.Variety;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class VarietyQueryIntegrationTests extends AbstractBackendIntegrationTest {

	@Test
	@Transactional
	void returnsVarietySummariesFromBatchLoadedOrchidGroups() throws Exception {
		var firstVariety = varietyRepository.save(new Variety(
				"BATCH-001", "테스트속", "배치품종A", null, "2치", true, true, null, null));
		var secondVariety = varietyRepository.save(new Variety(
				"BATCH-002", "테스트속", "배치품종B", null, "3치", true, true, null, null));
		var house = new House(99, "배치 테스트동");
		var bed = new PhysicalBed(1, 1);
		var zone = new BedZone("테스트 구역", BedZoneSide.LEFT, 1);
		bed.addBedZone(zone);
		house.addPhysicalBed(bed);
		houseRepository.save(house);

		orchidGroupRepository.save(createGroup(zone, firstVariety, 12, 1));
		orchidGroupRepository.save(createGroup(zone, secondVariety, 7, 2));

		mockMvc.perform(get("/api/varieties")
				.param("keyword", "배치품종")
				.param("page", "0")
				.param("size", "10"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content", hasSize(2)))
				.andExpect(jsonPath("$.data.content[0].totalQuantity").value(12))
				.andExpect(jsonPath("$.data.content[1].totalQuantity").value(7));
	}

	private OrchidGroup createGroup(BedZone zone, Variety variety, int quantity, int sortOrder) {
		var group = new OrchidGroup(
				zone,
				variety.getGenus(),
				variety.getName(),
				quantity,
				variety.getDefaultPotSize(),
				1,
				"정상",
				sortOrder,
				null,
				null);
		group.assignVariety(variety);
		return group;
	}
}
