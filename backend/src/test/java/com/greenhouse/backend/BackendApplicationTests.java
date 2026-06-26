package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.greenhouse.backend.farm.repository.BedZoneRepository;
import com.greenhouse.backend.farm.repository.HouseRepository;
import com.greenhouse.backend.farm.repository.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.PhysicalBedRepository;
import com.greenhouse.backend.work.repository.WorkRecordRepository;
import com.greenhouse.backend.work.repository.WorkTypeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BackendApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private HouseRepository houseRepository;

	@Autowired
	private PhysicalBedRepository physicalBedRepository;

	@Autowired
	private BedZoneRepository bedZoneRepository;

	@Autowired
	private OrchidGroupRepository orchidGroupRepository;

	@Autowired
	private WorkRecordRepository workRecordRepository;

	@Autowired
	private WorkTypeRepository workTypeRepository;

	@Test
	void contextLoads() {
	}

	@Test
	void createsInitialFarmStructureSeedData() {
		assertThat(houseRepository.count()).isEqualTo(15);
		assertThat(physicalBedRepository.count()).isEqualTo(45);
		assertThat(bedZoneRepository.count()).isEqualTo(90);
		assertThat(workTypeRepository.count()).isGreaterThanOrEqualTo(9);
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
			.andExpect(jsonPath("$.data.name").value("2배드 좌"));

		mockMvc.perform(get("/api/orchid-groups")
				.param("houseId", sampleHouse.getId().toString())
				.param("physicalBedId", sampleBed.getId().toString())
				.param("bedZoneId", sampleZone.getId().toString())
				.param("status", "정상"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data", hasSize(1)))
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
			.andExpect(jsonPath("$.data.targetName").value("3동 2배드"))
			.andExpect(jsonPath("$.data.items", hasSize(3)));

		mockMvc.perform(get("/api/farm-status/orchid-groups")
				.param("targetType", "BED_ZONE")
				.param("targetId", sampleZone.getId().toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.targetName").value("3동 2배드 2배드 좌"))
			.andExpect(jsonPath("$.data.items", hasSize(3)))
			.andExpect(jsonPath("$.data.items[0].physicalBedName").value("2배드"));
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

	@Test
	void createsUpdatesAndDeletesOrchidGroup() throws Exception {
		var sampleHouse = houseRepository.findAll().stream()
			.filter(house -> house.getNumber() == 3)
			.findFirst()
			.orElseThrow();
		var sampleBed = physicalBedRepository.findByHouseIdOrderByDisplayOrderAsc(sampleHouse.getId()).get(1);
		var sampleZone = bedZoneRepository.findByPhysicalBedIdOrderBySortOrderAsc(sampleBed.getId()).getFirst();
		var beforeCount = orchidGroupRepository.search(null, null, sampleZone.getId(), null).size();

		var createResult = mockMvc.perform(post("/api/orchid-groups")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "bedZoneId": %d,
					  "genus": "카틀레야",
					  "varietyName": "카틀레야 신규",
					  "quantity": 15,
					  "potSize": "4치",
					  "ageYear": 2,
					  "status": "정상",
					  "placementType": "TRAY",
					  "trayCount": 1,
					  "memo": "테스트 생성"
					}
					""".formatted(sampleZone.getId())))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.bedZoneId").value(sampleZone.getId()))
			.andExpect(jsonPath("$.data.varietyName").value("카틀레야 신규"))
			.andExpect(jsonPath("$.data.quantity").value(15))
			.andExpect(jsonPath("$.data.sortOrder").value(beforeCount + 1))
			.andReturn();

		var responseBody = createResult.getResponse().getContentAsString();
		var createdId = Long.valueOf(responseBody.replaceAll(".*\\\"id\\\":(\\d+).*", "$1"));

		mockMvc.perform(patch("/api/orchid-groups/{orchidGroupId}", createdId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "genus": "덴드로비움",
					  "varietyName": "덴드로비움 수정",
					  "quantity": 22,
					  "potSize": "5치",
					  "ageYear": 3,
					  "status": "주의",
					  "placementType": "BENCH",
					  "trayCount": 2,
					  "memo": "테스트 수정"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(createdId))
			.andExpect(jsonPath("$.data.bedZoneId").value(sampleZone.getId()))
			.andExpect(jsonPath("$.data.varietyName").value("덴드로비움 수정"))
			.andExpect(jsonPath("$.data.quantity").value(22))
			.andExpect(jsonPath("$.data.status").value("주의"));

		mockMvc.perform(delete("/api/orchid-groups/{orchidGroupId}", createdId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").doesNotExist());

		assertThat(orchidGroupRepository.existsById(createdId)).isFalse();
	}

	@Test
	void returnsValidationErrorsForInvalidOrchidGroupMutations() throws Exception {
		mockMvc.perform(post("/api/orchid-groups")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "bedZoneId": 999999,
					  "varietyName": "없는 구역",
					  "quantity": 10,
					  "status": "정상"
					}
					"""))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

		mockMvc.perform(post("/api/orchid-groups")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "bedZoneId": 1,
					  "varietyName": "",
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
					  "varietyName": "없는 난 묶음",
					  "quantity": 1,
					  "status": "정상"
					}
					"""))
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
		var targetBeforeCount = orchidGroupRepository.search(null, null, targetZone.getId(), null).size();

		var createResult = mockMvc.perform(post("/api/orchid-groups")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "bedZoneId": %d,
					  "varietyName": "이동 테스트",
					  "quantity": 5,
					  "status": "정상"
					}
					""".formatted(sourceZone.getId())))
			.andExpect(status().isCreated())
			.andReturn();
		var createdId = Long.valueOf(createResult.getResponse().getContentAsString().replaceAll(".*\\\"id\\\":(\\d+).*", "$1"));

		mockMvc.perform(patch("/api/orchid-groups/{orchidGroupId}/move", createdId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "toBedZoneId": %d,
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
				workTypeRepository.findByCode("MOVEMENT").orElseThrow().getName()
			)
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
		var sampleGroup = orchidGroupRepository.search(null, null, sampleZone.getId(), null).getFirst();

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

		var createdId = Long.valueOf(createResult.getResponse().getContentAsString().replaceAll(".*\\\"id\\\":(\\d+).*", "$1"));

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
			.andExpect(jsonPath("$.data[0].name").value("농약"))
			.andExpect(jsonPath("$.data[0].template").value("PESTICIDE"));

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
		var workTypeId = Long.valueOf(createResult.getResponse().getContentAsString().replaceAll(".*\\\"id\\\":(\\d+).*", "$1"));

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

	@Test
	void createsCustomersAndSalesSlipsWithCalculatedAmounts() throws Exception {
		var customerResult = mockMvc.perform(post("/api/customers")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "테스트 거래처",
					  "ownerName": "대표",
					  "phone": "010-0000-0000",
					  "address": "주소",
					  "memo": "거래처 메모"
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.name").value("테스트 거래처"))
			.andReturn();
		var customerId = Long.valueOf(customerResult.getResponse().getContentAsString().replaceAll(".*\\\"id\\\":(\\d+).*", "$1"));

		mockMvc.perform(get("/api/customers").param("keyword", "테스트"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[0].id").value(customerId));

		var slipResult = mockMvc.perform(post("/api/sales-slips")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "saleDate": "2026-06-24",
					  "customerId": %d,
					  "paymentStatus": "미입금",
					  "salesStatus": "작성중",
					  "paymentMethod": "현금",
					  "memo": "전표 메모",
					  "items": [
					    {
					      "itemName": "카틀레야 A",
					      "genus": "카틀레야",
					      "spec": "4치",
					      "quantity": 2,
					      "unitPrice": 15000,
					      "memo": "품목1"
					    },
					    {
					      "itemName": "덴드로비움 B",
					      "quantity": 3,
					      "unitPrice": 10000
					    }
					  ]
					}
					""".formatted(customerId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.customer.id").value(customerId))
			.andExpect(jsonPath("$.data.totalAmount").value(60000))
			.andExpect(jsonPath("$.data.items", hasSize(2)))
			.andExpect(jsonPath("$.data.items[0].amount").value(30000))
			.andReturn();
		var salesSlipId = Long.valueOf(slipResult.getResponse().getContentAsString().replaceFirst(".*?\\\"id\\\":(\\d+).*", "$1"));

		mockMvc.perform(get("/api/sales-slips/{salesSlipId}", salesSlipId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(salesSlipId))
			.andExpect(jsonPath("$.data.totalAmount").value(60000));

		mockMvc.perform(get("/api/sales-slips/{salesSlipId}/print", salesSlipId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(salesSlipId))
			.andExpect(jsonPath("$.data.slipNumber").exists())
			.andExpect(jsonPath("$.data.customer.id").value(customerId))
			.andExpect(jsonPath("$.data.items", hasSize(2)))
			.andExpect(jsonPath("$.data.totalAmount").value(60000));

		mockMvc.perform(get("/api/sales-slips")
				.param("customerId", customerId.toString())
				.param("from", "2026-06-01")
				.param("to", "2026-06-30"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[0].id").value(salesSlipId));
	}

	@Test
	void returnsValidationErrorsForInvalidSalesRequests() throws Exception {
		mockMvc.perform(post("/api/customers")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

		mockMvc.perform(post("/api/sales-slips")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "saleDate": "2026-06-24",
					  "customerId": 999999,
					  "items": [
					    {
					      "itemName": "없는 거래처",
					      "quantity": 1,
					      "unitPrice": 1000
					    }
					  ]
					}
					"""))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

		mockMvc.perform(post("/api/sales-slips")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "saleDate": "2026-06-24",
					  "customerId": 1,
					  "items": []
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
	}
}
