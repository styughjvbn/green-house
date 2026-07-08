package com.greenhouse.backend;

import com.greenhouse.backend.farm.dto.OrchidGroupResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

class OrchidGroupIntegrationTests extends AbstractBackendIntegrationTest {

	@Test
	void createsUpdatesAndDeletesOrchidGroup() throws Exception {
		var sampleHouse = houseRepository.findAll().stream()
				.filter(house -> house.getNumber() == 3)
				.findFirst()
				.orElseThrow();
		var sampleBed = physicalBedRepository.findByHouseIdOrderByDisplayOrderAsc(sampleHouse.getId()).get(1);
		var sampleZone = bedZoneRepository.findByPhysicalBedIdOrderBySortOrderAsc(sampleBed.getId()).getFirst();
		var sampleVariety = varietyRepository.findAll().stream()
				.findFirst()
				.orElseThrow();
		var updateVariety = varietyRepository.findAll().stream()
				.skip(1)
				.findFirst()
				.orElseThrow();
		var beforeCount = orchidGroupRepository.search(null, null, null, sampleZone.getId(), null).size();

		var createResult = mockMvc.perform(post("/api/orchid-groups")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "bedZoneId": %d,
						  "varietyId": %d,
						  "quantity": 15,
						  "potSize": "4치",
						  "ageYear": 2,
						  "status": "정상",
						  "startPosition": 21,
						  "endPosition": 22,
						  "placementType": "TRAY",
						  "trayCount": 1,
						  "memo": "테스트 생성"
						}
						""".formatted(sampleZone.getId(), sampleVariety.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.bedZoneId").value(sampleZone.getId()))
				.andExpect(jsonPath("$.data.varietyId").value(sampleVariety.getId()))
				.andExpect(jsonPath("$.data.varietyName").value(sampleVariety.getName()))
				.andExpect(jsonPath("$.data.quantity").value(15))
				.andExpect(jsonPath("$.data.sortOrder").value(beforeCount + 1))
				.andReturn();

		var responseBody = createResult.getResponse().getContentAsString();
		var createdId = Long.valueOf(responseBody.replaceAll(".*\\\"id\\\":(\\d+).*", "$1"));

		mockMvc.perform(patch("/api/orchid-groups/{orchidGroupId}", createdId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "varietyId": %d,
						  "quantity": 22,
						  "potSize": "5치",
						  "ageYear": 3,
						  "status": "주의",
						  "startPosition": 22,
						  "endPosition": 23,
						  "placementType": "BENCH",
						  "trayCount": 2,
						  "memo": "테스트 수정"
						}
						""".formatted(updateVariety.getId())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(createdId))
				.andExpect(jsonPath("$.data.bedZoneId").value(sampleZone.getId()))
				.andExpect(jsonPath("$.data.varietyId").value(updateVariety.getId()))
				.andExpect(jsonPath("$.data.varietyName").value(updateVariety.getName()))
				.andExpect(jsonPath("$.data.quantity").value(22))
				.andExpect(jsonPath("$.data.status").value("주의"));

		mockMvc.perform(delete("/api/orchid-groups/{orchidGroupId}", createdId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").doesNotExist());

		assertThat(orchidGroupRepository.existsById(createdId)).isFalse();
	}

	@Test
	@Transactional
	void deletesOrchidGroupEvenWhenInboundRecordReferencesIt() throws Exception {
		var sampleVariety = varietyRepository.findAll().stream()
				.findFirst()
				.orElseThrow();
		var sampleHouse = houseRepository.findAll().stream()
				.filter(house -> house.getNumber() == 3)
				.findFirst()
				.orElseThrow();
		var sampleBed = physicalBedRepository.findByHouseIdOrderByDisplayOrderAsc(sampleHouse.getId()).get(1);
		var sampleZone = bedZoneRepository.findByPhysicalBedIdOrderBySortOrderAsc(sampleBed.getId()).getFirst();

		var createResult = mockMvc.perform(post("/api/inbound-records")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "inboundDate": "2026-07-04",
						  "inboundType": "PRODUCT_POT",
						  "varietyId": %d,
						  "actualQuantity": 60,
						  "potSize": "4치",
						  "ageYear": 2,
						  "placementType": "TRAY",
						  "trayCount": 2,
						  "bedZoneId": %d,
						  "worker": "관리자",
						  "memo": "상품분 입고"
						}
						""".formatted(sampleVariety.getId(), sampleZone.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.createdOrchidGroupId").isNumber())
				.andReturn();

		var inboundRecordId = Long
				.valueOf(createResult.getResponse().getContentAsString().replaceAll(".*\\\"id\\\":(\\d+).*", "$1"));
		var createdOrchidGroupId = Long.valueOf(
				createResult.getResponse().getContentAsString().replaceAll(".*\\\"createdOrchidGroupId\\\":(\\d+).*",
						"$1"));

		mockMvc.perform(delete("/api/orchid-groups/{orchidGroupId}", createdOrchidGroupId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").doesNotExist());

		assertThat(orchidGroupRepository.existsById(createdOrchidGroupId)).isFalse();
		assertThat(inboundRecordRepository.findWithDetailsById(inboundRecordId))
				.get()
				.extracting(record -> record.getCreatedOrchidGroup())
				.isNull();
	}

	@Test
	@Transactional
	void calculatesOrchidGroupAgeYearFromInboundDate() throws Exception {
		var sampleVariety = varietyRepository.findAll().stream()
				.findFirst()
				.orElseThrow();
		var sampleHouse = houseRepository.findAll().stream()
				.filter(house -> house.getNumber() == 3)
				.findFirst()
				.orElseThrow();
		var sampleBed = physicalBedRepository.findByHouseIdOrderByDisplayOrderAsc(sampleHouse.getId()).get(1);
		var sampleZone = bedZoneRepository.findByPhysicalBedIdOrderBySortOrderAsc(sampleBed.getId()).getFirst();
		LocalDate inboundDate = LocalDate.now().minusYears(2).minusDays(1);

		var createResult = mockMvc.perform(post("/api/inbound-records")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "inboundDate": "%s",
						  "inboundType": "PRODUCT_POT",
						  "varietyId": %d,
						  "actualQuantity": 40,
						  "potSize": "4인치",
						  "ageYear": 1,
						  "placementType": "TRAY",
						  "trayCount": 1,
						  "bedZoneId": %d,
						  "worker": "관리자"
						}
						""".formatted(inboundDate, sampleVariety.getId(), sampleZone.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.createdOrchidGroupId").isNumber())
				.andReturn();

		var createdOrchidGroupId = Long.valueOf(
				createResult.getResponse().getContentAsString().replaceAll(".*\\\"createdOrchidGroupId\\\":(\\d+).*",
						"$1"));

		assertThat(
				OrchidGroupResponse.from(orchidGroupRepository.findById(createdOrchidGroupId).orElseThrow()).ageYear())
				.isEqualTo(3);
	}

	@Test
	void rejectsOverlappingOrchidGroupPlacement() throws Exception {
		var sampleVariety = varietyRepository.findAll().stream()
				.findFirst()
				.orElseThrow();
		var sampleHouse = houseRepository.findAll().stream()
				.filter(house -> house.getNumber() == 3)
				.findFirst()
				.orElseThrow();
		var sampleBed = physicalBedRepository.findByHouseIdOrderByDisplayOrderAsc(sampleHouse.getId()).get(1);
		var sampleZone = bedZoneRepository.findByPhysicalBedIdOrderBySortOrderAsc(sampleBed.getId()).getFirst();

		mockMvc.perform(post("/api/orchid-groups")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "bedZoneId": %d,
						  "varietyId": %d,
						  "quantity": 10,
						  "status": "?뺤긽",
						  "startPosition": 6,
						  "endPosition": 8
						}
						""".formatted(sampleZone.getId(), sampleVariety.getId())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
	}

	@Test
	void rejectsOrchidGroupPlacementShorterThanOneUnit() throws Exception {
		var sampleVariety = varietyRepository.findAll().stream()
				.findFirst()
				.orElseThrow();
		var sampleHouse = houseRepository.findAll().stream()
				.filter(house -> house.getNumber() == 3)
				.findFirst()
				.orElseThrow();
		var sampleBed = physicalBedRepository.findByHouseIdOrderByDisplayOrderAsc(sampleHouse.getId()).get(1);
		var sampleZone = bedZoneRepository.findByPhysicalBedIdOrderBySortOrderAsc(sampleBed.getId()).getFirst();

		mockMvc.perform(post("/api/orchid-groups")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "bedZoneId": %d,
						  "varietyId": %d,
						  "quantity": 10,
						  "status": "?뺤긽",
						  "startPosition": 21,
						  "endPosition": 21.5
						}
						""".formatted(sampleZone.getId(), sampleVariety.getId())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
	}

	@Test
	@Transactional
	void autoPlacesInboundOrchidGroupIntoFirstAvailableSingleSlot() throws Exception {
		var sampleVariety = varietyRepository.findAll().stream()
				.findFirst()
				.orElseThrow();
		var sampleHouse = houseRepository.findAll().stream()
				.filter(house -> house.getNumber() == 3)
				.findFirst()
				.orElseThrow();
		var sampleBed = physicalBedRepository.findByHouseIdOrderByDisplayOrderAsc(sampleHouse.getId()).get(1);
		var sampleZone = bedZoneRepository.findByPhysicalBedIdOrderBySortOrderAsc(sampleBed.getId()).getFirst();

		var createResult = mockMvc.perform(post("/api/inbound-records")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "inboundDate": "2026-07-04",
						  "inboundType": "PRODUCT_POT",
						  "varietyId": %d,
						  "actualQuantity": 60,
						  "potSize": "4in",
						  "ageYear": 2,
						  "placementType": "TRAY",
						  "trayCount": 2,
						  "bedZoneId": %d,
						  "worker": "愿由ъ옄"
						}
						""".formatted(sampleVariety.getId(), sampleZone.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.createdOrchidGroupId").isNumber())
				.andReturn();

		var createdOrchidGroupId = Long.valueOf(
				createResult.getResponse().getContentAsString().replaceAll(".*\\\"createdOrchidGroupId\\\":(\\d+).*",
						"$1"));
		var created = orchidGroupRepository.findById(createdOrchidGroupId).orElseThrow();

		assertThat(created.getStartPosition()).isEqualByComparingTo("21.00");
		assertThat(created.getEndPosition()).isEqualByComparingTo("22.00");
	}

	@Test
	@Transactional
	void rejectsPottingWhenBedZoneHasNoSingleSlotAvailable() throws Exception {
		var sampleVariety = varietyRepository.findAll().stream()
				.findFirst()
				.orElseThrow();
		var sampleHouse = houseRepository.findAll().stream()
				.filter(house -> house.getNumber() == 3)
				.findFirst()
				.orElseThrow();
		var sampleBed = physicalBedRepository.findByHouseIdOrderByDisplayOrderAsc(sampleHouse.getId()).get(1);
		var sampleZone = bedZoneRepository.findByPhysicalBedIdOrderBySortOrderAsc(sampleBed.getId()).getFirst();

		for (int slot = 21; slot < 24; slot++) {
			mockMvc.perform(post("/api/orchid-groups")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "bedZoneId": %d,
							  "varietyId": %d,
							  "quantity": 5,
							  "status": "?뺤긽",
							  "startPosition": %d,
							  "endPosition": %d
							}
							""".formatted(sampleZone.getId(), sampleVariety.getId(), slot, slot + 1)))
					.andExpect(status().isCreated());
		}

		var flaskCreateResult = mockMvc.perform(post("/api/inbound-records")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "inboundDate": "2026-07-10",
						  "inboundType": "FLASK_SEEDLING",
						  "varietyId": %d,
						  "bottleCount": 10,
						  "estimatedQuantity": 100,
						  "tempLocation": "?묒뾽???좊컲",
						  "pottingDueDate": "2026-07-12",
						  "worker": "愿由ъ옄"
						}
						""".formatted(sampleVariety.getId())))
				.andExpect(status().isCreated())
				.andReturn();
		var flaskRecordId = Long.valueOf(
				flaskCreateResult.getResponse().getContentAsString().replaceAll(".*\\\"id\\\":(\\d+).*", "$1"));

		mockMvc.perform(post("/api/inbound-records/{inboundRecordId}/potting", flaskRecordId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "pottingDate": "2026-07-12",
						  "actualQuantity": 20,
						  "potSize": "3_5in",
						  "ageYear": 1,
						  "growthStage": "?좊쵖",
						  "placementType": "TRAY",
						  "trayCount": 1,
						  "bedZoneId": %d,
						  "worker": "愿由ъ옄"
						}
						""".formatted(sampleZone.getId())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
	}

	@Test
	void returnsValidationErrorsForInvalidOrchidGroupMutations() throws Exception {
		var sampleVariety = varietyRepository.findAll().stream()
				.findFirst()
				.orElseThrow();

		mockMvc.perform(post("/api/orchid-groups")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "bedZoneId": 999999,
						  "varietyId": %d,
						  "quantity": 10,
						  "status": "정상"
						}
						""".formatted(sampleVariety.getId())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

		mockMvc.perform(post("/api/orchid-groups")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "bedZoneId": 1,
						  "quantity": 0,
						  "status": ""
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

		mockMvc.perform(patch("/api/orchid-groups/{orchidGroupId}", 999999)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "varietyId": %d,
						  "quantity": 1,
						  "status": "정상"
						}
						""".formatted(sampleVariety.getId())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}

	@Test
	void movesOrchidGroupAndCreatesMovementWorkRecord() throws Exception {
		var sampleHouse = houseRepository.findAll().stream()
				.filter(house -> house.getNumber() == 3)
				.findFirst()
				.orElseThrow();
		var sampleBed = physicalBedRepository.findByHouseIdOrderByDisplayOrderAsc(sampleHouse.getId()).get(1);
		var zones = bedZoneRepository.findByPhysicalBedIdOrderBySortOrderAsc(sampleBed.getId());
		var sourceZone = zones.get(0);
		var targetZone = zones.get(1);
		var sampleVariety = varietyRepository.findAll().stream()
				.findFirst()
				.orElseThrow();
		var targetBeforeCount = orchidGroupRepository.search(null, null, null, targetZone.getId(), null).size();

		var createResult = mockMvc.perform(post("/api/orchid-groups")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "bedZoneId": %d,
						  "varietyId": %d,
						  "quantity": 5,
						  "startPosition": 21,
						  "endPosition": 22,
						  "status": "정상"
						}
						""".formatted(sourceZone.getId(), sampleVariety.getId())))
				.andExpect(status().isCreated())
				.andReturn();
		var createdId = Long
				.valueOf(createResult.getResponse().getContentAsString().replaceAll(".*\\\"id\\\":(\\d+).*", "$1"));

		mockMvc.perform(patch("/api/orchid-groups/{orchidGroupId}/move", createdId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "toBedZoneId": %d,
						  "startPosition": 0,
						  "endPosition": 1,
						  "worker": "테스터",
						  "memo": "이동 테스트 메모"
						}
						""".formatted(targetZone.getId())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(createdId))
				.andExpect(jsonPath("$.data.bedZoneId").value(targetZone.getId()))
				.andExpect(jsonPath("$.data.sortOrder").value(targetBeforeCount + 1));

		var movement = workRecordRepository
				.findTopByTargetTypeAndTargetIdAndWorkTypeOrderByWorkDateDescIdDesc(
						"ORCHID_GROUP",
						createdId,
						workTypeRepository.findByCode("MOVEMENT").orElseThrow().getName())
				.orElseThrow();
		assertThat(movement.getFromBedZoneId()).isEqualTo(sourceZone.getId());
		assertThat(movement.getToBedZoneId()).isEqualTo(targetZone.getId());

		mockMvc.perform(delete("/api/orchid-groups/{orchidGroupId}", createdId))
				.andExpect(status().isOk());
	}

	@Test
	void returnsValidationErrorsForInvalidOrchidGroupMove() throws Exception {
		mockMvc.perform(patch("/api/orchid-groups/{orchidGroupId}/move", 999999)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "toBedZoneId": 1
						}
						"""))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

		var sampleHouse = houseRepository.findAll().stream()
				.filter(house -> house.getNumber() == 3)
				.findFirst()
				.orElseThrow();
		var sampleBed = physicalBedRepository.findByHouseIdOrderByDisplayOrderAsc(sampleHouse.getId()).get(1);
		var sampleZone = bedZoneRepository.findByPhysicalBedIdOrderBySortOrderAsc(sampleBed.getId()).getFirst();
		var sampleGroup = orchidGroupRepository.search(null, null, null, sampleZone.getId(), null).getFirst();

		mockMvc.perform(patch("/api/orchid-groups/{orchidGroupId}/move", sampleGroup.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "toBedZoneId": 999999
						}
						"""))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

		mockMvc.perform(patch("/api/orchid-groups/{orchidGroupId}/move", sampleGroup.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
	}

	@Test
	@Transactional
	void hidesDepletedOrchidGroupFromActiveFarmViews() throws Exception {
		var sampleHouse = houseRepository.findAll().stream()
				.filter(house -> house.getNumber() == 3)
				.findFirst()
				.orElseThrow();
		var sampleBed = physicalBedRepository.findByHouseIdOrderByDisplayOrderAsc(sampleHouse.getId()).get(1);
		var sampleZone = bedZoneRepository.findByPhysicalBedIdOrderBySortOrderAsc(sampleBed.getId()).get(1);
		var sampleVariety = varietyRepository.findAll().stream().findFirst().orElseThrow();

		var createGroupResult = mockMvc.perform(post("/api/orchid-groups")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "bedZoneId": %d,
						  "varietyId": %d,
						  "quantity": 1,
						  "startPosition": 23,
						  "endPosition": 24,
						  "status": "정상"
						}
						""".formatted(sampleZone.getId(), sampleVariety.getId())))
				.andExpect(status().isCreated())
				.andReturn();
		var orchidGroupId = Long.valueOf(
				createGroupResult.getResponse().getContentAsString().replaceFirst(".*?\\\"id\\\":(\\d+).*", "$1"));

		var partnerResult = mockMvc.perform(post("/api/business-partners")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "name": "소진 테스트 거래처",
						  "partnerType": "WHOLESALE"
						}
						"""))
				.andExpect(status().isCreated())
				.andReturn();
		var partnerId = Long
				.valueOf(partnerResult.getResponse().getContentAsString().replaceAll(".*\\\"id\\\":(\\d+).*", "$1"));

		mockMvc.perform(post("/api/sales-slips")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "saleDate": "2026-07-08",
						  "partnerId": %d,
						  "salesStatus": "출고 완료",
						  "items": [
						    {
						      "itemName": "%s",
						      "genus": "%s",
						      "quantity": 1,
						      "unitPrice": 1000,
						      "allocations": [
						        {
						          "orchidGroupId": %d,
						          "quantity": 1
						        }
						      ]
						    }
						  ]
						}
						""".formatted(
						partnerId,
						sampleVariety.getName(),
						sampleVariety.getGenus(),
						orchidGroupId)))
				.andExpect(status().isCreated());

		assertThat(orchidGroupRepository.findById(orchidGroupId).orElseThrow().getQuantity()).isZero();

		mockMvc.perform(get("/api/orchid-groups").param("bedZoneId", sampleZone.getId().toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[?(@.id == %d)]".formatted(orchidGroupId)).isEmpty());

		mockMvc.perform(get("/api/houses/{houseId}", sampleHouse.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.physicalBeds[?(@.id == %d)].bedZones[0].orchidGroups[?(@.id == %d)]"
						.formatted(sampleBed.getId(), orchidGroupId)).doesNotExist());
	}
}
