package com.greenhouse.backend;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.greenhouse.backend.farm.domain.BedZone;
import com.greenhouse.backend.farm.domain.BedZoneSide;
import com.greenhouse.backend.farm.domain.House;
import com.greenhouse.backend.farm.domain.OrchidGroup;
import com.greenhouse.backend.farm.domain.PhysicalBed;
import com.greenhouse.backend.farm.domain.Variety;
import com.greenhouse.backend.work.domain.WorkType;
import com.greenhouse.backend.work.domain.WorkTypeTemplate;
import com.greenhouse.backend.work.repository.WorkOperationRepository;
import com.greenhouse.backend.work.repository.WorkOperationTargetRepository;
import com.greenhouse.backend.work.repository.WorkAppliedEffectRepository;
import com.greenhouse.backend.work.repository.WorkEffectOrchidGroupRepository;
import com.greenhouse.backend.work.repository.WorkTargetExecutionRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class WorkOperationIntegrationTests extends AbstractBackendIntegrationTest {

	@Autowired
	private WorkOperationRepository workOperationRepository;
	@Autowired
	private WorkOperationTargetRepository workOperationTargetRepository;
	@Autowired
	private WorkTargetExecutionRepository workTargetExecutionRepository;
	@Autowired
	private WorkAppliedEffectRepository workAppliedEffectRepository;
	@Autowired
	private WorkEffectOrchidGroupRepository workEffectOrchidGroupRepository;

	private WorkType pesticideType;
	private WorkType movementType;
	private WorkType discardType;
	private OrchidGroup targetGroup;
	private House sourceHouse;
	private BedZone destinationZone;

	@BeforeEach
	void setUp() {
		workEffectOrchidGroupRepository.deleteAll();
		workAppliedEffectRepository.deleteAll();
		workTargetExecutionRepository.deleteAll();
		workOperationTargetRepository.deleteAll();
		workOperationRepository.deleteAll();
		workRecordRepository.deleteAll();
		orchidGroupRepository.deleteAll();
		varietyRepository.deleteAll();
		bedZoneRepository.deleteAll();
		physicalBedRepository.deleteAll();
		houseRepository.deleteAll();
		workTypeRepository.deleteAll();

		pesticideType = workTypeRepository.save(new WorkType(
				"PESTICIDE", "농약", WorkTypeTemplate.PESTICIDE, true, false, true, 1));
		movementType = workTypeRepository.save(new WorkType(
				"MOVEMENT", "위치 이동", WorkTypeTemplate.MOVEMENT, true, true, true, 2));
		discardType = workTypeRepository.save(new WorkType(
				"DISCARD", "폐기", WorkTypeTemplate.DISCARD, true, true, true, 3));

		House house = new House(3, "3동");
		PhysicalBed bed = new PhysicalBed(1, 1);
		BedZone sourceZone = new BedZone("좌측", BedZoneSide.LEFT, 1);
		bed.addBedZone(sourceZone);
		house.addPhysicalBed(bed);
		sourceHouse = houseRepository.save(house);

		House destinationHouse = new House(5, "5동");
		PhysicalBed destinationBed = new PhysicalBed(1, 1);
		destinationZone = new BedZone("우측", BedZoneSide.RIGHT, 1);
		destinationBed.addBedZone(destinationZone);
		destinationHouse.addPhysicalBed(destinationBed);
		houseRepository.save(destinationHouse);

		Variety variety = varietyRepository.save(new Variety(
				"TEST-001", "팔레놉시스", "테스트 난", null, "3.5치", true, true, null, null));
		targetGroup = new OrchidGroup(
				sourceZone,
				variety.getGenus(),
				variety.getName(),
				100,
				"3.5치",
				2,
				"정상",
				1,
				BigDecimal.ONE,
				BigDecimal.TEN);
		targetGroup.assignVariety(variety);
		targetGroup = orchidGroupRepository.save(targetGroup);
	}

	@Test
	void discardsPartialOrFullQuantityAndClosesFullyDiscardedGroup() throws Exception {
		Long partialOperationId = createDiscardOperation("일부 폐기");
		Long partialTargetId = workOperationTargetRepository
				.findByWorkOperationIdAndExcludedAtIsNullOrderByIdAsc(partialOperationId).getFirst().getId();

		mockMvc.perform(post("/api/work-operations/{id}/start", partialOperationId))
				.andExpect(status().isOk());
		mockMvc.perform(post(
				"/api/work-operations/{id}/targets/{targetId}/complete", partialOperationId, partialTargetId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "worker": "폐기 담당자",
						  "resultDetails": {
						    "discardQuantity": 30,
						    "reason": "상태 불량"
						  }
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.targets[0].executionStatus").value("COMPLETED"))
				.andExpect(jsonPath("$.data.targets[0].resultDetails.discardedQuantity").value(30))
				.andExpect(jsonPath("$.data.targets[0].resultDetails.remainingQuantity").value(70));

		OrchidGroup partiallyDiscarded = orchidGroupRepository.findById(targetGroup.getId()).orElseThrow();
		org.assertj.core.api.Assertions.assertThat(partiallyDiscarded.getQuantity()).isEqualTo(70);
		org.assertj.core.api.Assertions.assertThat(partiallyDiscarded.getStatus()).isEqualTo("정상");

		mockMvc.perform(post("/api/work-operations/{id}/complete", partialOperationId))
				.andExpect(status().isOk());

		Long fullOperationId = createDiscardOperation("전량 폐기");
		Long fullTargetId = workOperationTargetRepository
				.findByWorkOperationIdAndExcludedAtIsNullOrderByIdAsc(fullOperationId).getFirst().getId();
		mockMvc.perform(post("/api/work-operations/{id}/start", fullOperationId))
				.andExpect(status().isOk());
		mockMvc.perform(post(
				"/api/work-operations/{id}/targets/{targetId}/complete", fullOperationId, fullTargetId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "resultDetails": {"discardQuantity": 70}
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.targets[0].executionStatus").value("COMPLETED"))
				.andExpect(jsonPath("$.data.targets[0].resultDetails.remainingQuantity").value(0))
				.andExpect(jsonPath("$.data.targets[0].resultDetails.status").value("폐기"));

		OrchidGroup fullyDiscarded = orchidGroupRepository.findById(targetGroup.getId()).orElseThrow();
		org.assertj.core.api.Assertions.assertThat(fullyDiscarded.getQuantity()).isZero();
		org.assertj.core.api.Assertions.assertThat(fullyDiscarded.getStatus()).isEqualTo("폐기");
	}

	private Long createDiscardOperation(String title) throws Exception {
		var result = mockMvc.perform(post("/api/work-operations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "workTypeId": %d,
						  "title": "%s",
						  "plannedStartDate": "2026-07-16",
						  "sourceScopeType": "MANUAL_SELECTION",
						  "sourceOrchidGroupIds": [%d]
						}
						""".formatted(discardType.getId(), title, targetGroup.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.workTypeCode").value("DISCARD"))
				.andReturn();
		return Long.valueOf(result.getResponse().getContentAsString().replaceAll(
				".*?\\\"data\\\":\\{\\\"id\\\":(\\d+).*", "$1"));
	}

	@Test
	void plansAndExecutesMovementPerTargetWithoutLegacyRecord() throws Exception {
		var createResult = mockMvc.perform(post("/api/work-operations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "workTypeId": %d,
						  "title": "5동 자리 이동",
						  "plannedStartDate": "2026-07-16",
						  "sourceScopeType": "MANUAL_SELECTION",
						  "sourceOrchidGroupIds": [%d]
						}
						""".formatted(movementType.getId(), targetGroup.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.workTypeCode").value("MOVEMENT"))
				.andExpect(jsonPath("$.data.targets", hasSize(1)))
				.andReturn();

		Long operationId = Long.valueOf(createResult.getResponse().getContentAsString().replaceAll(
				".*?\\\"data\\\":\\{\\\"id\\\":(\\d+).*", "$1"));
		Long targetId = workOperationTargetRepository
				.findByWorkOperationIdAndExcludedAtIsNullOrderByIdAsc(operationId).getFirst().getId();

		mockMvc.perform(post("/api/work-operations/{id}/start", operationId))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/work-operations/{id}/targets/{targetId}/complete", operationId, targetId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "worker": "이동 담당자",
						  "resultDetails": {
						    "toBedZoneId": %d,
						    "startPosition": 0,
						    "endPosition": 10,
						    "worker": "이동 담당자",
						    "memo": "계획 이동 완료"
						  }
						}
						""".formatted(destinationZone.getId())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.targets[0].executionStatus").value("COMPLETED"))
				.andExpect(jsonPath("$.data.targets[0].resultDetails.toBedZoneId").value(destinationZone.getId()));

		org.assertj.core.api.Assertions.assertThat(
				orchidGroupRepository.findById(targetGroup.getId()).orElseThrow().getBedZone().getId())
				.isEqualTo(destinationZone.getId());
		org.assertj.core.api.Assertions.assertThat(workRecordRepository.count()).isZero();
	}

	@Test
	void preservesHouseTargetSnapshotAfterOrchidGroupMoves() throws Exception {
		mockMvc.perform(post("/api/work-operations/target-preview")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "scopeType": "HOUSE",
						  "scopeId": %d
						}
						""".formatted(sourceHouse.getId())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.orchidGroupCount").value(1))
				.andExpect(jsonPath("$.data.totalQuantity").value(100))
				.andExpect(jsonPath("$.data.targets[0].orchidGroupId").value(targetGroup.getId()))
				.andExpect(jsonPath("$.data.targets[0].locationSnapshot.houseNumber").value(3));

		var createResult = mockMvc.perform(post("/api/work-operations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "workTypeId": %d,
						  "title": "3동 농약 살포",
						  "plannedStartDate": "2026-07-14",
						  "sourceScopeType": "HOUSE",
						  "sourceScopeId": %d,
						  "details": {
						    "materialName": "살균제",
						    "dilutionRatio": "1000배"
						  },
						  "worker": "테스터"
						}
						""".formatted(pesticideType.getId(), sourceHouse.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.status").value("PLANNED"))
				.andExpect(jsonPath("$.data.targets", hasSize(1)))
				.andReturn();

		String response = createResult.getResponse().getContentAsString();
		Long operationId = Long.valueOf(response.replaceAll(
				".*?\\\"data\\\":\\{\\\"id\\\":(\\d+).*", "$1"));
		Long targetId = workOperationTargetRepository
				.findByWorkOperationIdAndExcludedAtIsNullOrderByIdAsc(operationId).getFirst().getId();

		mockMvc.perform(post("/api/work-operations/{id}/start", operationId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
		mockMvc.perform(post("/api/work-operations/{id}/targets/{targetId}/complete", operationId, targetId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "worker": "첫 작업자",
						  "completedDate": "2026-07-15",
						  "resultDetails": {"weather": "맑음"}
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("COMPLETED"))
				.andExpect(jsonPath("$.data.targets[0].executionStatus").value("COMPLETED"))
				.andExpect(jsonPath("$.data.targets[0].effectAppliedAt").exists())
				.andExpect(jsonPath("$.data.targets[0].worker").value("첫 작업자"))
				.andExpect(jsonPath("$.data.targets[0].resultDetails.weather").value("맑음"));

		mockMvc.perform(post("/api/work-operations/{id}/complete", operationId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"completedDate\":\"2026-07-16\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("COMPLETED"))
				.andExpect(jsonPath("$.data.targets[0].executionStatus").value("COMPLETED"));
		org.assertj.core.api.Assertions.assertThat(
				workTargetExecutionRepository.findByTargetWorkOperationIdOrderByIdAsc(operationId)
						.getFirst().getCompletedAt().toLocalDate())
				.isEqualTo(java.time.LocalDate.of(2026, 7, 15));
		org.assertj.core.api.Assertions.assertThat(
				workOperationRepository.findById(operationId).orElseThrow().getActualEndAt().toLocalDate())
				.isEqualTo(java.time.LocalDate.of(2026, 7, 15));

		mockMvc.perform(post("/api/work-operations/{id}/targets/{targetId}/complete", operationId, targetId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "worker": "중복 작업자",
						  "resultDetails": {"weather": "변경 시도"}
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("COMPLETED"))
				.andExpect(jsonPath("$.data.targets[0].worker").value("첫 작업자"))
				.andExpect(jsonPath("$.data.targets[0].resultDetails.weather").value("맑음"));
		org.assertj.core.api.Assertions.assertThat(
				workAppliedEffectRepository.countByWorkOperationIdAndTargetId(operationId, targetId))
				.isEqualTo(1);

		mockMvc.perform(patch("/api/orchid-groups/{id}/move", targetGroup.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "toBedZoneId": %d,
						  "startPosition": 1,
						  "endPosition": 10,
						  "worker": "테스터"
						}
						""".formatted(destinationZone.getId())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.houseNumber").value(5));

		mockMvc.perform(get("/api/orchid-groups/{id}/work-history", targetGroup.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(2)))
				.andExpect(jsonPath("$.data[?(@.sourceKind == 'WORK_OPERATION')].workOperationId").value(hasItem(operationId.intValue())))
				.andExpect(jsonPath("$.data[?(@.sourceKind == 'WORK_OPERATION')].propagated").value(hasItem(true)))
				.andExpect(jsonPath("$.data[?(@.sourceKind == 'WORK_OPERATION')].locationSnapshot.houseNumber").value(hasItem(3)))
				.andExpect(jsonPath("$.data[?(@.sourceKind == 'WORK_OPERATION')].currentLocation.houseNumber").value(hasItem(5)))
				.andExpect(jsonPath("$.data[?(@.sourceKind == 'LEGACY_WORK_RECORD')].workType").value(hasItem("위치 이동")));
	}

	@Test
	void searchesOperationsByOverlappingPeriodStatusAndScope() throws Exception {
		mockMvc.perform(post("/api/work-operations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "workTypeId": %d,
						  "title": "7월 기간 농약 작업",
						  "plannedStartDate": "2026-07-10",
						  "plannedEndDate": "2026-07-20",
						  "sourceScopeType": "HOUSE",
						  "sourceScopeId": %d
						}
						""".formatted(pesticideType.getId(), sourceHouse.getId())))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/work-operations")
				.param("from", "2026-07-15")
				.param("to", "2026-07-31")
				.param("status", "PLANNED")
				.param("scopeType", "HOUSE")
				.param("scopeId", sourceHouse.getId().toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(1)))
				.andExpect(jsonPath("$.data[0].title").value("7월 기간 농약 작업"))
				.andExpect(jsonPath("$.data[0].targets", hasSize(1)));

		mockMvc.perform(get("/api/work-operations")
				.param("from", "2026-08-01")
				.param("to", "2026-08-31"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(0)));
	}

	@Test
	void separatesManagementListFromOperationHistory() throws Exception {
		var created = mockMvc.perform(post("/api/work-operations")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "workTypeId": %d,
								  "title": "관리 대상 작업",
								  "plannedStartDate": "2026-07-17",
								  "sourceScopeType": "HOUSE",
								  "sourceScopeId": %d
								}
								""".formatted(pesticideType.getId(), sourceHouse.getId())))
				.andExpect(status().isCreated())
				.andReturn();
		Long operationId = Long.valueOf(created.getResponse().getContentAsString().replaceAll(
				".*?\\\"data\\\":\\{\\\"id\\\":(\\d+).*", "$1"));

		mockMvc.perform(get("/api/work-operations").param("view", "MANAGEMENT"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(1)))
				.andExpect(jsonPath("$.data[0].status").value("PLANNED"));
		mockMvc.perform(get("/api/work-operations").param("view", "HISTORY"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(0)));

		mockMvc.perform(post("/api/work-operations/{id}/cancel", operationId))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/work-operations").param("view", "MANAGEMENT"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(1)))
				.andExpect(jsonPath("$.data[0].status").value("CANCELED"));
		mockMvc.perform(get("/api/work-operations").param("view", "HISTORY"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(1)))
				.andExpect(jsonPath("$.data[0].status").value("CANCELED"));
	}

	@Test
	void cancelsRemainingTargetsAfterSomeTargetsAreCompleted() throws Exception {
		OrchidGroup secondGroup = new OrchidGroup(
				targetGroup.getBedZone(),
				targetGroup.getGenus(),
				targetGroup.getVarietyName(),
				80,
				"3.5치",
				2,
				"정상",
				2,
				BigDecimal.TEN,
				new BigDecimal("20"));
		secondGroup.assignVariety(targetGroup.getVariety());
		secondGroup = orchidGroupRepository.save(secondGroup);

		var created = mockMvc.perform(post("/api/work-operations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "workTypeId": %d,
						  "title": "일부 완료 후 취소",
						  "plannedStartDate": "2026-07-17",
						  "sourceScopeType": "MANUAL_SELECTION",
						  "sourceOrchidGroupIds": [%d, %d]
						}
						""".formatted(pesticideType.getId(), targetGroup.getId(), secondGroup.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.targets", hasSize(2)))
				.andReturn();
		Long operationId = Long.valueOf(created.getResponse().getContentAsString().replaceAll(
				".*?\\\"data\\\":\\{\\\"id\\\":(\\d+).*", "$1"));
		Long completedTargetId = workOperationTargetRepository
				.findByWorkOperationIdAndExcludedAtIsNullOrderByIdAsc(operationId).getFirst().getId();

		mockMvc.perform(post("/api/work-operations/{id}/start", operationId))
				.andExpect(status().isOk());
		mockMvc.perform(post(
				"/api/work-operations/{id}/targets/{targetId}/complete", operationId, completedTargetId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"worker": "작업자", "resultDetails": {"weather": "맑음"}}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.progress.completed").value(1))
				.andExpect(jsonPath("$.data.progress.pending").value(1));

		mockMvc.perform(post("/api/work-operations/{id}/cancel", operationId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("CANCELED"))
				.andExpect(jsonPath("$.data.actualEndAt").exists())
				.andExpect(jsonPath("$.data.progress.completed").value(1))
				.andExpect(jsonPath("$.data.progress.canceled").value(1))
				.andExpect(jsonPath("$.data.targets[?(@.executionStatus == 'COMPLETED')]", hasSize(1)))
				.andExpect(jsonPath("$.data.targets[?(@.executionStatus == 'CANCELED')]", hasSize(1)));

		org.assertj.core.api.Assertions.assertThat(
				workAppliedEffectRepository.countByWorkOperationIdAndTargetId(operationId, completedTargetId))
				.isEqualTo(1);
	}

	@Test
	void createsAnImmediatelyCompletedRecordOperationAndRejectsLegacyManualWrites() throws Exception {
		mockMvc.perform(post("/api/work-operations/record")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "workTypeId": %d,
						  "title": "구역 농약 기록",
						  "plannedStartDate": "2026-07-15",
						  "sourceScopeType": "BED_ZONE",
						  "sourceScopeId": %d,
						  "details": {"materialName": "살균제"},
						  "worker": "테스터"
						}
						""".formatted(pesticideType.getId(), targetGroup.getBedZone().getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.status").value("COMPLETED"))
				.andExpect(jsonPath("$.data.targets", hasSize(1)))
				.andExpect(jsonPath("$.data.targets[0].executionStatus").value("COMPLETED"))
				.andExpect(jsonPath("$.data.targets[0].resultDetails.materialName").value("살균제"));

		mockMvc.perform(post("/api/work-records")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "workTypeId": %d,
						  "workDate": "2026-07-15",
						  "targetType": "ORCHID_GROUP",
						  "targetId": %d
						}
						""".formatted(pesticideType.getId(), targetGroup.getId())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details[0]").value("신규 작업 기록은 작업 실행 API를 사용해주세요."));
	}
}
