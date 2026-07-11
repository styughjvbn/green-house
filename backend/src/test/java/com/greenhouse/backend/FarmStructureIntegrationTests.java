package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Seed data is currently disabled; re-enable after deterministic test fixtures are restored.")
class FarmStructureIntegrationTests extends AbstractBackendIntegrationTest {

	@Test
	void contextLoads() {
	}

	@Test
	void createsInitialFarmStructureSeedData() {
		assertThat(houseRepository.count()).isEqualTo(15);
		assertThat(physicalBedRepository.count()).isEqualTo(45);
		assertThat(bedZoneRepository.count()).isEqualTo(90);
		assertThat(workTypeRepository.count()).isGreaterThanOrEqualTo(11);
		assertThat(varietyRepository.count()).isGreaterThanOrEqualTo(3);
		assertThat(materialRepository.count()).isGreaterThanOrEqualTo(3);
	}

	@Test
	void createsThreePhysicalBedsPerHouseWithLeftToRightDisplayOrder() {
		var houses = houseRepository.findAll();

		assertThat(houses).hasSize(15);
		for (var house : houses) {
			var beds = physicalBedRepository.findByHouseIdOrderByDisplayOrderAsc(house.getId());
			assertThat(beds).hasSize(3);
			assertThat(beds).extracting("displayOrder").containsExactly(1, 2, 3);
			assertThat(bedZoneRepository.findByHouseId(house.getId())).hasSize(6);
		}
	}

	@Test
	void returnsFarmStructureApisWithCommonResponseShape() throws Exception {
		var sampleHouse = houseRepository.findAll().stream()
				.filter(house -> house.getNumber() == 3)
				.findFirst()
				.orElseThrow();
		var sampleBed = physicalBedRepository.findByHouseIdOrderByDisplayOrderAsc(sampleHouse.getId()).get(1);
		var sampleZone = bedZoneRepository.findByPhysicalBedIdOrderBySortOrderAsc(sampleBed.getId()).getFirst();

		mockMvc.perform(get("/api/houses"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").doesNotExist())
				.andExpect(jsonPath("$.data", hasSize(15)));

		mockMvc.perform(get("/api/houses/{houseId}", sampleHouse.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.number").value(3))
				.andExpect(jsonPath("$.data.physicalBeds", hasSize(3)));

		mockMvc.perform(get("/api/physical-beds").param("houseId", sampleHouse.getId().toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(3)));

		mockMvc.perform(get("/api/physical-beds/{physicalBedId}", sampleBed.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.number").value(2));

		mockMvc.perform(get("/api/bed-zones").param("houseId", sampleHouse.getId().toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(6)));

		mockMvc.perform(get("/api/bed-zones/{bedZoneId}", sampleZone.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.name").value("2다이 좌"));

		mockMvc.perform(get("/api/orchid-groups")
				.param("houseId", sampleHouse.getId().toString())
				.param("physicalBedId", sampleBed.getId().toString())
				.param("bedZoneId", sampleZone.getId().toString())
				.param("status", "정상"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(1)))
				.andExpect(jsonPath("$.data[0].varietyName").value("카틀레야 A"));
	}

	@Test
	void returnsCommonErrorShapeForMissingResource() throws Exception {
		mockMvc.perform(get("/api/houses/{houseId}", 999999))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
				.andExpect(jsonPath("$.error.message").value("동을 찾을 수 없습니다."))
				.andExpect(jsonPath("$.error.details", hasSize(0)));
	}

	@Test
	void returnsDashboardSummary() throws Exception {
		mockMvc.perform(get("/api/dashboard/summary"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.houseCount").value(15))
				.andExpect(jsonPath("$.data.physicalBedCount").value(45))
				.andExpect(jsonPath("$.data.bedZoneCount").value(90))
				.andExpect(jsonPath("$.data.orchidGroupCount").value(3))
				.andExpect(jsonPath("$.data.warningCount").value(0))
				.andExpect(jsonPath("$.data.repotDueCount").value(0));
	}

	@Test
	void returnsFarmStatusMapInHouseNumberOrder() throws Exception {
		mockMvc.perform(get("/api/farm-status/map"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.houses", hasSize(15)))
				.andExpect(jsonPath("$.data.houses[0].houseNumber").value(1))
				.andExpect(jsonPath("$.data.houses[0].physicalBeds", hasSize(3)))
				.andExpect(jsonPath("$.data.houses[0].physicalBeds[0].positionUnitCount").exists())
				.andExpect(jsonPath("$.data.houses[2].houseNumber").value(3))
				.andExpect(jsonPath("$.data.houses[2].orchidGroupCount").value(3));
	}

	@Test
	void returnsFarmStatusOrchidGroupsByTargetScope() throws Exception {
		var sampleHouse = houseRepository.findAll().stream()
				.filter(house -> house.getNumber() == 3)
				.findFirst()
				.orElseThrow();
		var sampleBed = physicalBedRepository.findByHouseIdOrderByDisplayOrderAsc(sampleHouse.getId()).get(1);
		var sampleZone = bedZoneRepository.findByPhysicalBedIdOrderBySortOrderAsc(sampleBed.getId()).getFirst();

		mockMvc.perform(get("/api/farm-status/orchid-groups")
				.param("targetType", "HOUSE")
				.param("targetId", sampleHouse.getId().toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.targetName").value("3동"))
				.andExpect(jsonPath("$.data.items", hasSize(3)));

		mockMvc.perform(get("/api/farm-status/orchid-groups")
				.param("targetType", "PHYSICAL_BED")
				.param("targetId", sampleBed.getId().toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.targetName").value("3동 2다이"))
				.andExpect(jsonPath("$.data.items", hasSize(3)));

		mockMvc.perform(get("/api/farm-status/orchid-groups")
				.param("targetType", "BED_ZONE")
				.param("targetId", sampleZone.getId().toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.targetName").value("3동 2다이 2다이 좌"))
				.andExpect(jsonPath("$.data.items", hasSize(3)))
				.andExpect(jsonPath("$.data.items[0].physicalBedName").value("2다이"));
	}

	@Test
	void returnsFarmStatusZoomData() throws Exception {
		var sampleHouse = houseRepository.findAll().stream()
				.filter(house -> house.getNumber() == 3)
				.findFirst()
				.orElseThrow();
		var sampleBed = physicalBedRepository.findByHouseIdOrderByDisplayOrderAsc(sampleHouse.getId()).get(1);

		mockMvc.perform(get("/api/farm-status/zoom")
				.param("level", "HOUSE")
				.param("houseId", sampleHouse.getId().toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.level").value("HOUSE"))
				.andExpect(jsonPath("$.data.physicalBeds", hasSize(3)));

		mockMvc.perform(get("/api/farm-status/zoom")
				.param("level", "BED_ZONE")
				.param("physicalBedId", sampleBed.getId().toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.level").value("BED_ZONE"))
				.andExpect(jsonPath("$.data.bedZones", hasSize(2)));
	}

	@Test
	void returnsValidationErrorsForInvalidFarmStatusRequests() throws Exception {
		mockMvc.perform(get("/api/farm-status/orchid-groups")
				.param("targetType", "INVALID")
				.param("targetId", "1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

		mockMvc.perform(get("/api/farm-status/orchid-groups")
				.param("targetType", "HOUSE"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

		mockMvc.perform(get("/api/farm-status/orchid-groups")
				.param("targetType", "HOUSE")
				.param("targetId", "999999"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

		mockMvc.perform(get("/api/farm-status/zoom")
				.param("level", "HOUSE"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
	}
}
