package com.greenhouse.backend;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

@Disabled("Seed data is currently disabled; re-enable after deterministic test fixtures are restored.")
class WorkRecordIntegrationTests extends AbstractBackendIntegrationTest {

	@Test
	void createsAndSearchesWorkRecords() throws Exception {
		var sampleHouse = houseRepository.findAll().stream()
				.filter(house -> house.getNumber() == 3)
				.findFirst()
				.orElseThrow();
		var sampleBed = physicalBedRepository.findByHouseIdOrderByDisplayOrderAsc(sampleHouse.getId()).get(1);
		var sampleZone = bedZoneRepository.findByPhysicalBedIdOrderBySortOrderAsc(sampleBed.getId()).getFirst();
		var pesticideType = workTypeRepository.findByCode("PESTICIDE").orElseThrow();

		var createResult = mockMvc.perform(post("/api/work-records")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "workTypeId": %d,
						  "workDate": "2026-06-24",
						  "targetType": "BED_ZONE",
						  "targetId": %d,
						  "materialName": "살균제",
						  "dilutionRatio": "1000배",
						  "quantity": "2L",
						  "worker": "테스터",
						  "memo": "작업 이력 테스트"
						}
						""".formatted(pesticideType.getId(), sampleZone.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.workType").value("농약"))
				.andExpect(jsonPath("$.data.workTypeId").value(pesticideType.getId()))
				.andExpect(jsonPath("$.data.workTypeTemplate").value("PESTICIDE"))
				.andExpect(jsonPath("$.data.workDate").value("2026-06-24"))
				.andExpect(jsonPath("$.data.targetType").value("BED_ZONE"))
				.andExpect(jsonPath("$.data.targetId").value(sampleZone.getId()))
				.andReturn();

		var createdId = Long
				.valueOf(createResult.getResponse().getContentAsString().replaceAll(".*\\\"id\\\":(\\d+).*", "$1"));

		mockMvc.perform(get("/api/work-records")
				.param("targetType", "BED_ZONE")
				.param("targetId", sampleZone.getId().toString())
				.param("workType", "농약")
				.param("from", "2026-06-01")
				.param("to", "2026-06-30"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].id").value(createdId))
				.andExpect(jsonPath("$.data[0].materialName").value("살균제"));
	}

	@Test
	void returnsWorkTypesAndValidationErrorsForWorkRecordRequests() throws Exception {
		var fertilizerType = workTypeRepository.findByCode("FERTILIZER").orElseThrow();

		mockMvc.perform(get("/api/work-types"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(9))))
				.andExpect(jsonPath("$.data[0].name").value("입고"))
				.andExpect(jsonPath("$.data[0].template").value("MEMO"));

		mockMvc.perform(post("/api/work-records")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "workTypeId": 999999,
						  "workDate": "2026-06-24",
						  "targetType": "FARM"
						}
						"""))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

		mockMvc.perform(post("/api/work-records")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "workTypeId": %d,
						  "workDate": "2026-06-24",
						  "targetType": "BED_ZONE",
						  "targetId": 999999
						}
						""".formatted(fertilizerType.getId())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

		mockMvc.perform(post("/api/work-records")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "workTypeId": %d,
						  "workDate": "2026-06-24",
						  "targetType": "BED_ZONE"
						}
						""".formatted(fertilizerType.getId())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
	}

	@Test
	void managesCustomWorkTypesAndRejectsInactiveTypeForCreate() throws Exception {
		var createResult = mockMvc.perform(post("/api/work-types")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "name": "테스트 작업",
						  "template": "MEMO"
						}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.name").value("테스트 작업"))
				.andExpect(jsonPath("$.data.template").value("MEMO"))
				.andExpect(jsonPath("$.data.active").value(true))
				.andReturn();
		var workTypeId = Long
				.valueOf(createResult.getResponse().getContentAsString().replaceAll(".*\\\"id\\\":(\\d+).*", "$1"));

		mockMvc.perform(patch("/api/work-types/{workTypeId}", workTypeId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "name": "테스트 작업 수정",
						  "template": "STATUS",
						  "active": false
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.name").value("테스트 작업 수정"))
				.andExpect(jsonPath("$.data.template").value("STATUS"))
				.andExpect(jsonPath("$.data.active").value(false));

		mockMvc.perform(get("/api/work-types").param("includeInactive", "true"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[?(@.id == %d)]".formatted(workTypeId), hasSize(1)));

		mockMvc.perform(patch("/api/work-types/reorder")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "orderedIds": [%d]
						}
						""".formatted(workTypeId)))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/work-records")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "workTypeId": %d,
						  "workDate": "2026-06-24",
						  "targetType": "FARM"
						}
						""".formatted(workTypeId)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
	}
}
