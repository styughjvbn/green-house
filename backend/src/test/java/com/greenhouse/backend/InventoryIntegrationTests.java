package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

class InventoryIntegrationTests extends FarmFixtureIntegrationTest {

	@Test
	void returnsVarietiesAndConnectedOrchidGroups() throws Exception {
		var sampleVariety = varietyRepository.findAll()
			.stream()
			.filter(variety -> variety.getName().equals("카틀레야 A"))
			.findFirst()
			.orElseThrow();

		mockMvc.perform(get("/api/varieties"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.content.length()").value(greaterThanOrEqualTo(3)))
			.andExpect(jsonPath("$.data.content[0].code").exists())
			.andExpect(jsonPath("$.data.page").value(0))
			.andExpect(jsonPath("$.data.size").value(10));

		mockMvc.perform(get("/api/varieties/{varietyId}", sampleVariety.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.name").value("카틀레야 A"))
			.andExpect(jsonPath("$.data.connectedGroupCount").value(greaterThanOrEqualTo(1)))
			.andExpect(jsonPath("$.data.totalQuantity").value(greaterThanOrEqualTo(120)));

		mockMvc.perform(get("/api/varieties/{varietyId}/orchid-groups", sampleVariety.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(1)))
			.andExpect(jsonPath("$.data[0].location").value("3동-2다이 2다이 좌"));
	}

	@Test
	void createsAndDeactivatesVariety() throws Exception {
		mockMvc.perform(post("/api/varieties").contentType(MediaType.APPLICATION_JSON).content("""
				{
				  "genus": "심비디움",
				  "name": "심비디움 골드",
				  "alias": "골드",
				  "defaultPotSize": "5치",
				  "saleEnabled": true,
				  "description": "테스트 품종",
				  "memo": "메모"
				}
				"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.code").exists())
			.andExpect(jsonPath("$.data.name").value("심비디움 골드"))
			.andExpect(jsonPath("$.data.active").value(true));

		var created = varietyRepository.findAll()
			.stream()
			.filter(variety -> variety.getName().equals("심비디움 골드"))
			.findFirst()
			.orElseThrow();

		mockMvc.perform(patch("/api/varieties/{varietyId}/deactivate", created.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.active").value(false));
	}

	@Test
	void updatesVariety() throws Exception {
		var sampleVariety = varietyRepository.findAll()
			.stream()
			.filter(variety -> variety.getName().equals("카틀레야 A"))
			.findFirst()
			.orElseThrow();

		mockMvc
			.perform(patch("/api/varieties/{varietyId}", sampleVariety.getId()).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "genus": "카틀레야",
						  "name": "카틀레야 A 수정",
						  "alias": "수정 별칭",
						  "defaultPotSize": "4.5치",
						  "saleEnabled": false,
						  "description": "수정 설명",
						  "memo": "수정 메모"
						}
						"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.name").value("카틀레야 A 수정"))
			.andExpect(jsonPath("$.data.alias").value("수정 별칭"))
			.andExpect(jsonPath("$.data.saleEnabled").value(false));
	}

	@Test
	@Transactional
	void createsListsPotsAndCancelsInboundRecords() throws Exception {
		var sampleVariety = varietyRepository.findAll()
			.stream()
			.filter(variety -> variety.getName().equals("카틀레야 A"))
			.findFirst()
			.orElseThrow();
		var sampleHouse = houseRepository.findAll()
			.stream()
			.filter(house -> house.getNumber() == 3)
			.findFirst()
			.orElseThrow();
		var sampleBed = physicalBedRepository.findByHouseIdOrderByDisplayOrderAsc(sampleHouse.getId()).get(1);
		var sampleZone = bedZoneRepository.findByPhysicalBedIdOrderBySortOrderAsc(sampleBed.getId()).getFirst();

		var flaskCreateResult = mockMvc
			.perform(post("/api/inbound-records").contentType(MediaType.APPLICATION_JSON).content("""
					{
					  "inboundDate": "2026-07-05",
					  "inboundType": "FLASK_SEEDLING",
					  "varietyId": %d,
					  "estimatedQuantity": 250,
					  "tempLocation": "작업장 선반",
					  "pottingDueDate": "2026-07-12",
					  "worker": "관리자",
					  "memo": "유리병 입고"
					}
					""".formatted(sampleVariety.getId())))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.status").value("POTTING_PENDING"))
			.andExpect(jsonPath("$.data.editable").value(true))
			.andExpect(jsonPath("$.data.createdOrchidGroups").isEmpty())
			.andReturn();
		var flaskRecordId = Long
			.valueOf(flaskCreateResult.getResponse().getContentAsString().replaceAll(".*\\\"id\\\":(\\d+).*", "$1"));

		mockMvc
			.perform(post("/api/inbound-records/{inboundRecordId}/potting", flaskRecordId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(
						"""
								{
								  "pottingDate": "2026-07-12",
								  "results": [{"quantity": 210, "bedZoneId": %d, "startPosition": 21, "endPosition": 22, "potSize": "3.5치", "ageYear": 1}],
								  "worker": "포트 담당",
								  "memo": "포트 작업 완료"
								}
								"""
							.formatted(sampleZone.getId())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("PLACED"))
			.andExpect(jsonPath("$.data.editable").value(false))
			.andExpect(jsonPath("$.data.pottingDate").value("2026-07-12"))
			.andExpect(jsonPath("$.data.worker").value("관리자"))
			.andExpect(jsonPath("$.data.memo").value("유리병 입고"))
			.andExpect(jsonPath("$.data.createdOrchidGroups[0].quantity").value(210))
			.andExpect(jsonPath("$.data.createdOrchidGroups[0].id").isNumber());

		mockMvc
			.perform(patch("/api/inbound-records/{inboundRecordId}", flaskRecordId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "inboundDate": "2026-07-06",
						  "estimatedQuantity": 260,
						  "tempLocation": "수정 시도"
						}
						"""))
			.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/inbound-records").contentType(MediaType.APPLICATION_JSON).content("""
				{
				  "inboundDate": "2026-07-04",
				  "inboundType": "PRODUCT_POT",
				  "varietyId": %d,
				  "placement": {
				    "quantity": 60,
				    "potSize": "4치",
				    "ageYear": 2,
				    "placementType": "TRAY",
				    "trayCount": 2,
				    "bedZoneId": %d
				  },
				  "worker": "관리자",
				  "memo": "상품분 입고"
				}
				""".formatted(sampleVariety.getId(), sampleZone.getId())))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.status").value("PLACED"))
			.andExpect(jsonPath("$.data.editable").value(false))
			.andExpect(jsonPath("$.data.createdOrchidGroups[0].id").isNumber());

		mockMvc.perform(get("/api/inbound-records").param("status", "PLACED"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.content.length()").value(greaterThanOrEqualTo(2)))
			.andExpect(jsonPath("$.data.page").value(0));

		mockMvc.perform(post("/api/inbound-records").contentType(MediaType.APPLICATION_JSON).content("""
				{
				  "inboundDate": "2026-07-03",
				  "inboundType": "FLASK_SEEDLING",
				  "newVariety": {
				    "genus": "온시디움",
				    "name": "온시디움 허니",
				    "defaultPotSize": "3치",
				    "memo": "간이 등록"
				  },
				  "estimatedQuantity": 80,
				  "tempLocation": "선반 B",
				  "worker": "관리자"
				}
				""")).andExpect(status().isCreated()).andExpect(jsonPath("$.data.varietyName").value("온시디움 허니"));

		var cancelResult = mockMvc
			.perform(post("/api/inbound-records").contentType(MediaType.APPLICATION_JSON).content("""
					{
					  "inboundDate": "2026-07-02",
					  "inboundType": "FLASK_SEEDLING",
					  "varietyId": %d,
					  "estimatedQuantity": 40,
					  "tempLocation": "임시 선반"
					}
					""".formatted(sampleVariety.getId())))
			.andExpect(status().isCreated())
			.andReturn();
		var cancelTargetId = Long
			.valueOf(cancelResult.getResponse().getContentAsString().replaceAll(".*\\\"id\\\":(\\d+).*", "$1"));

		mockMvc
			.perform(post("/api/inbound-records/{inboundRecordId}/cancel", cancelTargetId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "memo": "오입력 취소"
						}
						"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("CANCELED"));

		assertThat(inboundRecordRepository.count()).isGreaterThanOrEqualTo(4);
	}

	@Test
	void updatesInboundRecord() throws Exception {
		var sampleVariety = varietyRepository.findAll()
			.stream()
			.filter(variety -> variety.getName().equals("카틀레야 A"))
			.findFirst()
			.orElseThrow();

		var createResult = mockMvc
			.perform(post("/api/inbound-records").contentType(MediaType.APPLICATION_JSON).content("""
					{
					  "inboundDate": "2026-07-01",
					  "inboundType": "FLASK_SEEDLING",
					  "varietyId": %d,
					  "estimatedQuantity": 120,
					  "tempLocation": "기존 위치"
					}
					""".formatted(sampleVariety.getId())))
			.andExpect(status().isCreated())
			.andReturn();

		var inboundRecordId = Long
			.valueOf(createResult.getResponse().getContentAsString().replaceAll(".*\\\"id\\\":(\\d+).*", "$1"));

		mockMvc
			.perform(patch("/api/inbound-records/{inboundRecordId}", inboundRecordId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "inboundDate": "2026-07-02",
						  "estimatedQuantity": 150,
						  "tempLocation": "수정 위치",
						  "pottingDueDate": "2026-07-10",
						  "worker": "관리자",
						  "memo": "수정 메모"
						}
						"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.inboundDate").value("2026-07-02"))
			.andExpect(jsonPath("$.data.estimatedQuantity").value(150))
			.andExpect(jsonPath("$.data.tempLocation").value("수정 위치"));
	}

	@Test
	void deletesCanceledInboundRecord() throws Exception {
		var sampleVariety = varietyRepository.findAll()
			.stream()
			.filter(variety -> variety.getName().equals("카틀레야 A"))
			.findFirst()
			.orElseThrow();

		var createResult = mockMvc
			.perform(post("/api/inbound-records").contentType(MediaType.APPLICATION_JSON).content("""
					{
					  "inboundDate": "2026-07-08",
					  "inboundType": "FLASK_SEEDLING",
					  "varietyId": %d,
					  "estimatedQuantity": 120,
					  "tempLocation": "삭제 테스트"
					}
					""".formatted(sampleVariety.getId())))
			.andExpect(status().isCreated())
			.andReturn();

		var inboundRecordId = Long
			.valueOf(createResult.getResponse().getContentAsString().replaceAll(".*\\\"id\\\":(\\d+).*", "$1"));

		mockMvc
			.perform(post("/api/inbound-records/{inboundRecordId}/cancel", inboundRecordId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "memo": "오입력"
						}
						"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("CANCELED"));

		mockMvc.perform(delete("/api/inbound-records/{inboundRecordId}", inboundRecordId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").doesNotExist());

		assertThat(inboundRecordRepository.existsById(inboundRecordId)).isFalse();
	}

	@Test
	void createsUpdatesAndDeactivatesMaterial() throws Exception {
		mockMvc.perform(get("/api/materials"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.content.length()").value(greaterThanOrEqualTo(3)))
			.andExpect(jsonPath("$.data.content[0].code").exists())
			.andExpect(jsonPath("$.data.page").value(0))
			.andExpect(jsonPath("$.data.size").value(10));

		var createResult = mockMvc.perform(post("/api/materials").contentType(MediaType.APPLICATION_JSON).content("""
				{
				  "category": "자재",
				  "name": "새 자재",
				  "manufacturer": "테스트 제조사",
				  "specification": "10개입",
				  "stockQuantity": "5개",
				  "storageLocation": "창고 Z",
				  "usage": "테스트 사용"
				}
				"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.name").value("새 자재"))
			.andExpect(jsonPath("$.data.active").value(true))
			.andReturn();

		var materialId = Long
			.valueOf(createResult.getResponse().getContentAsString().replaceAll(".*\\\"id\\\":(\\d+).*", "$1"));

		mockMvc
			.perform(
					patch("/api/materials/{materialId}", materialId).contentType(MediaType.APPLICATION_JSON).content("""
							{
							  "category": "농약",
							  "name": "수정 자재",
							  "manufacturer": "수정 제조사",
							  "specification": "20개입",
							  "stockQuantity": "8개",
							  "storageLocation": "창고 Y",
							  "usage": "수정 사용"
							}
							"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.category").value("농약"))
			.andExpect(jsonPath("$.data.name").value("수정 자재"))
			.andExpect(jsonPath("$.data.stockQuantity").value("8개"));

		mockMvc.perform(patch("/api/materials/{materialId}/deactivate", materialId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.active").value(false));
	}

	@Test
	void deletesUnreferencedVarietyAndRejectsReferencedVarietyDelete() throws Exception {
		var referencedVariety = varietyRepository.findAll()
			.stream()
			.filter(variety -> variety.getName().equals("카틀레야 A"))
			.findFirst()
			.orElseThrow();

		mockMvc.perform(delete("/api/varieties/{varietyId}", referencedVariety.getId()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

		var createResult = mockMvc.perform(post("/api/varieties").contentType(MediaType.APPLICATION_JSON).content("""
				{
				  "genus": "호접란",
				  "name": "삭제 테스트 품종"
				}
				""")).andExpect(status().isCreated()).andReturn();

		var varietyId = Long
			.valueOf(createResult.getResponse().getContentAsString().replaceAll(".*\\\"id\\\":(\\d+).*", "$1"));

		mockMvc.perform(delete("/api/varieties/{varietyId}", varietyId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").doesNotExist());

		assertThat(varietyRepository.existsById(varietyId)).isFalse();
	}

	@Test
	void deletesMaterial() throws Exception {
		var createResult = mockMvc.perform(post("/api/materials").contentType(MediaType.APPLICATION_JSON).content("""
				{
				  "category": "비료",
				  "name": "삭제 테스트 자재"
				}
				""")).andExpect(status().isCreated()).andReturn();

		var materialId = Long
			.valueOf(createResult.getResponse().getContentAsString().replaceAll(".*\\\"id\\\":(\\d+).*", "$1"));

		mockMvc.perform(delete("/api/materials/{materialId}", materialId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").doesNotExist());

		assertThat(materialRepository.existsById(materialId)).isFalse();
	}

}
