package com.greenhouse.backend;

import static com.greenhouse.backend.support.JsonResponseTestSupport.requiredId;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.farm.orchid.domain.OrchidGroup;
import com.greenhouse.backend.farm.structure.domain.BedZone;
import com.greenhouse.backend.farm.structure.domain.BedZoneSide;
import com.greenhouse.backend.farm.structure.domain.House;
import com.greenhouse.backend.farm.structure.domain.PhysicalBed;
import com.greenhouse.backend.farm.variety.domain.Variety;
import com.greenhouse.backend.work.api.operation.WorkTypeTemplate;
import com.greenhouse.backend.work.effect.repository.WorkAppliedEffectRepository;
import com.greenhouse.backend.work.effect.repository.WorkEffectOrchidGroupRepository;
import com.greenhouse.backend.work.operation.domain.WorkType;
import com.greenhouse.backend.work.operation.domain.WorkTypeDefinition;
import com.greenhouse.backend.work.operation.repository.WorkOperationRepository;
import com.greenhouse.backend.work.target.repository.WorkOperationTargetRepository;
import com.greenhouse.backend.work.target.repository.WorkTargetExecutionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

@org.springframework.test.context.TestPropertySource(
    properties =
        "spring.datasource.url=jdbc:h2:mem:workoperationintegrationtests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class WorkOperationIntegrationTests extends AbstractBackendIntegrationTest {

  @Autowired private WorkOperationRepository workOperationRepository;

  @Autowired private WorkOperationTargetRepository workOperationTargetRepository;

  @Autowired private WorkTargetExecutionRepository workTargetExecutionRepository;

  @Autowired private WorkAppliedEffectRepository workAppliedEffectRepository;

  @Autowired private WorkEffectOrchidGroupRepository workEffectOrchidGroupRepository;

  private WorkType pesticideType;

  private WorkType movementType;

  private WorkType discardType;

  private WorkType repotType;

  private OrchidGroup targetGroup;

  private House sourceHouse;

  private BedZone destinationZone;

  @BeforeEach
  void setUp() {
    workEffectOrchidGroupRepository.deleteAll();
    workAppliedEffectRepository.deleteAll();
    workTargetExecutionRepository.deleteAll();
    workOperationTargetRepository.deleteAll();
    workCommandReceiptRepository.deleteAll();
    workOperationRepository.deleteAll();
    orchidGroupRepository.deleteAll();
    varietyRepository.deleteAll();
    bedZoneRepository.deleteAll();
    physicalBedRepository.deleteAll();
    houseRepository.deleteAll();
    workTypeRepository.deleteAll();

    pesticideType =
        workTypeRepository.save(
            new WorkType("PESTICIDE", "농약", WorkTypeTemplate.PESTICIDE, true, false, true, 1));
    movementType =
        workTypeRepository.save(
            new WorkType("MOVEMENT", "자리 이동", WorkTypeTemplate.MOVEMENT, true, true, true, 2));
    discardType =
        workTypeRepository.save(
            new WorkType("DISCARD", "폐기", WorkTypeTemplate.DISCARD, true, true, true, 3));
    repotType =
        workTypeRepository.save(
            new WorkType(
                WorkTypeDefinition.REPOT.name(),
                "분갈이",
                WorkTypeTemplate.REPOT,
                true,
                true,
                true,
                4));

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

    Variety variety =
        varietyRepository.save(
            new Variety("TEST-001", "팔레놉시스", "테스트 난", null, "3.5치", true, true, null, null));
    targetGroup =
        new OrchidGroup(
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
    targetGroup = saveOrchidGroup(targetGroup);
  }

  @Test
  void exposesOperationAndTargetActionsFromTheBackendContract() throws Exception {
    mockMvc
        .perform(
            post("/api/work-operations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
				{
				  "workTypeId": %d,
				  "title": "작업 가능 액션 확인",
				  "plannedStartDate": "2026-07-16",
				  "sourceScopeType": "MANUAL_SELECTION",
				  "sourceOrchidGroupIds": [%d]
				}
				"""
                        .formatted(pesticideType.getId(), targetGroup.getId())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.availableActions", containsInAnyOrder("START", "CANCEL")))
        .andExpect(jsonPath("$.data.targets[0].availableActions", hasSize(0)));

    Long operationId = workOperationRepository.findAll().getFirst().getId();
    mockMvc
        .perform(post("/api/work-operations/{id}/start", operationId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.availableActions", containsInAnyOrder("PAUSE", "CANCEL")))
        .andExpect(
            jsonPath(
                "$.data.targets[0].availableActions",
                containsInAnyOrder("START", "COMPLETE", "SKIP")));
  }

  @Test
  void excludesLegacyMigrationMetadataFromOperationDetails() throws Exception {
    mockMvc
        .perform(
            post("/api/work-operations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
				{
				  "workTypeId": %d,
				  "title": "레거시 메타데이터 필터 확인",
				  "plannedStartDate": "2026-07-16",
				  "sourceScopeType": "MANUAL_SELECTION",
				  "sourceOrchidGroupIds": [%d],
				  "details": {
				    "materialName": "표시할 자재",
				    "migrationSource": "LEGACY_WORK_RECORD",
				    "legacyWorkRecordId": 99,
				    "legacyStatus": "COMPLETED",
				    "legacyTargetType": "FARM"
				  }
				}
				"""
                        .formatted(pesticideType.getId(), targetGroup.getId())))
        .andExpect(status().isCreated());

    Long operationId = workOperationRepository.findAll().getFirst().getId();
    mockMvc
        .perform(get("/api/work-operations/{id}/details", operationId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.fields", hasSize(1)))
        .andExpect(jsonPath("$.data.fields[0].key").value("materialName"))
        .andExpect(jsonPath("$.data.fields[0].value").value("표시할 자재"));
  }

  @Test
  void batchCreateSplitsSingleVarietyStructureWorkInOneRequest() throws Exception {
    Variety anotherVariety =
        varietyRepository.save(
            new Variety("TEST-002", "카틀레야", "다른 품종", null, "3.5치", true, true, null, null));
    OrchidGroup anotherGroup =
        new OrchidGroup(
            targetGroup.getBedZone(),
            anotherVariety.getGenus(),
            anotherVariety.getName(),
            80,
            "3.5치",
            2,
            "정상",
            2,
            new BigDecimal("11"),
            new BigDecimal("12"));
    anotherGroup.assignVariety(anotherVariety);
    anotherGroup = saveOrchidGroup(anotherGroup);

    mockMvc
        .perform(
            post("/api/work-operations/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
				{
				  "operation": {
				    "workTypeId": %d,
				    "title": "품종별 분갈이",
				    "plannedStartDate": "2026-07-16",
				    "sourceScopeType": "MANUAL_SELECTION",
				    "sourceOrchidGroupIds": [%d, %d]
				  }
				}
				"""
                        .formatted(repotType.getId(), targetGroup.getId(), anotherGroup.getId())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data", hasSize(2)))
        .andExpect(jsonPath("$.data[*].title", hasItem("테스트 난 · 분갈이")))
        .andExpect(jsonPath("$.data[*].title", hasItem("다른 품종 · 분갈이")));

    Assertions.assertThat(workOperationRepository.count()).isEqualTo(2);
    Assertions.assertThat(workOperationTargetRepository.count()).isEqualTo(2);
  }

  @Test
  void discardsPartialOrFullQuantityAndClosesFullyDiscardedGroup() throws Exception {
    Long partialOperationId = createDiscardOperation("일부 폐기");
    Long partialTargetId =
        workOperationTargetRepository
            .findByWorkOperationIdAndExcludedAtIsNullOrderByIdAsc(partialOperationId)
            .getFirst()
            .getId();

    mockMvc
        .perform(post("/api/work-operations/{id}/start", partialOperationId))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post(
                    "/api/work-operations/{id}/targets/{targetId}/complete",
                    partialOperationId,
                    partialTargetId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
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

    OrchidGroup partiallyDiscarded =
        orchidGroupRepository.findById(targetGroup.getId()).orElseThrow();
    Assertions.assertThat(partiallyDiscarded.getQuantity()).isEqualTo(70);
    Assertions.assertThat(partiallyDiscarded.getStatus()).isEqualTo("정상");

    mockMvc
        .perform(
            post(
                    "/api/work-operations/{id}/targets/{targetId}/complete",
                    partialOperationId,
                    partialTargetId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
						{
						  "worker": "중복 폐기 담당자",
						  "resultDetails": {
						    "discardQuantity": 10,
						    "reason": "중복 요청"
						  }
						}
						"""))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REUSED"));

    OrchidGroup afterDuplicate = orchidGroupRepository.findById(targetGroup.getId()).orElseThrow();
    Assertions.assertThat(afterDuplicate.getQuantity()).isEqualTo(70);
    Assertions.assertThat(
            workAppliedEffectRepository.countByWorkOperationIdAndTargetId(
                partialOperationId, partialTargetId))
        .isEqualTo(1);

    mockMvc
        .perform(post("/api/work-operations/{id}/complete", partialOperationId))
        .andExpect(status().isOk());

    Long fullOperationId = createDiscardOperation("전량 폐기");
    Long fullTargetId =
        workOperationTargetRepository
            .findByWorkOperationIdAndExcludedAtIsNullOrderByIdAsc(fullOperationId)
            .getFirst()
            .getId();
    mockMvc
        .perform(post("/api/work-operations/{id}/start", fullOperationId))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post(
                    "/api/work-operations/{id}/targets/{targetId}/complete",
                    fullOperationId,
                    fullTargetId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
						{
						  "resultDetails": {"discardQuantity": 70}
						}
						"""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.targets[0].executionStatus").value("COMPLETED"))
        .andExpect(jsonPath("$.data.targets[0].resultDetails.remainingQuantity").value(0))
        .andExpect(jsonPath("$.data.targets[0].resultDetails.status").value("폐기"));

    OrchidGroup fullyDiscarded = orchidGroupRepository.findById(targetGroup.getId()).orElseThrow();
    Assertions.assertThat(fullyDiscarded.getQuantity()).isZero();
    Assertions.assertThat(fullyDiscarded.getStatus()).isEqualTo("폐기");
  }

  @Test
  void createsCompletedDiscardRecordWithAllTargetResults() throws Exception {
    mockMvc
        .perform(
            post("/api/work-operations/discard-records")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
				{
				  "operation": {
				    "workTypeId": %d,
				    "title": "폐기 작업 기록",
				    "plannedStartDate": "2026-07-16",
				    "plannedEndDate": "2026-07-16",
				    "sourceScopeType": "MANUAL_SELECTION",
				    "sourceOrchidGroupIds": [%d]
				  },
				  "completedDate": "2026-07-16",
				  "worker": "폐기 담당자",
				  "results": [
				    {
				      "orchidGroupId": %d,
				      "discardQuantity": 25,
				      "reason": "상태 불량"
				    }
				  ]
				}
				"""
                        .formatted(discardType.getId(), targetGroup.getId(), targetGroup.getId())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data", hasSize(1)))
        .andExpect(jsonPath("$.data[0].status").value("COMPLETED"))
        .andExpect(jsonPath("$.data[0].targets[0].resultDetails.discardedQuantity").value(25))
        .andExpect(jsonPath("$.data[0].targets[0].resultDetails.remainingQuantity").value(75));

    OrchidGroup updated = orchidGroupRepository.findById(targetGroup.getId()).orElseThrow();
    Assertions.assertThat(updated.getQuantity()).isEqualTo(75);
    Assertions.assertThat(workOperationRepository.count()).isEqualTo(1);

    Long operationId = workOperationRepository.findAll().getFirst().getId();
    mockMvc
        .perform(get("/api/work-operations/{id}/details", operationId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.summary.id").value(operationId))
        .andExpect(jsonPath("$.data.executions", hasSize(1)))
        .andExpect(jsonPath("$.data.executions[0].resultType").value("DISCARD"))
        .andExpect(jsonPath("$.data.executions[0].reason").value("상태 불량"))
        .andExpect(jsonPath("$.data.executions[0].sources[0].beforeQuantity").value(100))
        .andExpect(jsonPath("$.data.executions[0].sources[0].inputQuantity").value(25))
        .andExpect(jsonPath("$.data.executions[0].sources[0].afterQuantity").value(75));

    mockMvc
        .perform(get("/api/work-operations/{id}", operationId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.availableActions", hasItem("CANCEL")));
    mockMvc
        .perform(get("/api/work-operations/{id}/void-eligibility", operationId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.cancellable").value(true))
        .andExpect(jsonPath("$.data.affectedOperations[0].workOperationId").value(operationId))
        .andExpect(
            jsonPath("$.data.affectedOrchidGroups[0].orchidGroupId").value(targetGroup.getId()))
        .andExpect(jsonPath("$.data.affectedOrchidGroups[0].impactType").value("RESTORED"));
    mockMvc
        .perform(
            post("/api/work-operations/{id}/void", operationId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
						{
						  "idempotencyKey": "void-independent-discard",
						  "reason": "잘못 등록한 독립 폐기"
						}
						"""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("VOIDED"));

    OrchidGroup restored = orchidGroupRepository.findById(targetGroup.getId()).orElseThrow();
    Assertions.assertThat(restored.getQuantity()).isEqualTo(100);
    Assertions.assertThat(restored.getStatus()).isEqualTo("정상");
  }

  @Test
  void createsSeparateCompletedDiscardRecordsForEachVariety() throws Exception {
    Variety anotherVariety =
        varietyRepository.save(
            new Variety("TEST-003", "카틀레야", "폐기 대상 품종", null, "3.5치", true, true, null, null));
    OrchidGroup anotherGroup =
        new OrchidGroup(
            targetGroup.getBedZone(),
            anotherVariety.getGenus(),
            anotherVariety.getName(),
            80,
            "3.5치",
            2,
            "정상",
            2,
            new BigDecimal("11"),
            new BigDecimal("12"));
    anotherGroup.assignVariety(anotherVariety);
    anotherGroup = saveOrchidGroup(anotherGroup);

    mockMvc
        .perform(
            post("/api/work-operations/discard-records")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
						{
						  "operation": {
						    "workTypeId": %d,
						    "title": "품종별 폐기",
						    "plannedStartDate": "2026-07-16",
						    "plannedEndDate": "2026-07-16",
						    "sourceScopeType": "MANUAL_SELECTION",
						    "sourceOrchidGroupIds": [%d, %d]
						  },
						  "completedDate": "2026-07-16",
						  "worker": "폐기 담당자",
						  "results": [
						    {"orchidGroupId": %d, "discardQuantity": 10, "reason": "상태 불량"},
						    {"orchidGroupId": %d, "discardQuantity": 20, "reason": "상태 불량"}
						  ]
						}
						"""
                        .formatted(
                            discardType.getId(),
                            targetGroup.getId(),
                            anotherGroup.getId(),
                            targetGroup.getId(),
                            anotherGroup.getId())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data", hasSize(2)))
        .andExpect(jsonPath("$.data[*].title", hasItem("테스트 난 · 폐기")))
        .andExpect(jsonPath("$.data[*].title", hasItem("폐기 대상 품종 · 폐기")))
        .andExpect(jsonPath("$.data[*].status", everyItem(is("COMPLETED"))))
        .andExpect(jsonPath("$.data[*].targets", everyItem(hasSize(1))));

    Assertions.assertThat(workOperationRepository.count()).isEqualTo(2);
  }

  private Long createDiscardOperation(String title) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/work-operations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
				{
				  "workTypeId": %d,
				  "title": "%s",
				  "plannedStartDate": "2026-07-16",
				  "sourceScopeType": "MANUAL_SELECTION",
				  "sourceOrchidGroupIds": [%d]
				}
				"""
                            .formatted(discardType.getId(), title, targetGroup.getId())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.workTypeCode").value("DISCARD"))
            .andReturn();
    return requiredId(result.getResponse().getContentAsString(), "/data/id");
  }

  @Test
  void plansAndExecutesMovementPerTargetWithoutLegacyRecord() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/work-operations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
					{
					  "workTypeId": %d,
					  "title": "5동 자리 이동",
					  "plannedStartDate": "2026-07-16",
					  "sourceScopeType": "MANUAL_SELECTION",
					  "sourceOrchidGroupIds": [%d]
					}
					"""
                            .formatted(movementType.getId(), targetGroup.getId())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.workTypeCode").value("MOVEMENT"))
            .andExpect(jsonPath("$.data.targets", hasSize(1)))
            .andReturn();

    Long operationId = requiredId(createResult.getResponse().getContentAsString(), "/data/id");
    mockMvc
        .perform(post("/api/work-operations/{id}/start", operationId))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/work-operations/{id}/structure-change-executions", operationId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
						{"idempotencyKey":"planned-move","completedDate":"2026-07-16","worker":"이동 담당자",
						 "sources":[{"sourceOrchidGroupId":%d,"inputQuantity":100}],
						 "results":[{"bedZoneId":%d,"quantity":100,"attributeSourceOrchidGroupId":%d,
						 "purpose":"NORMAL","startPosition":0,"endPosition":10}]}
						"""
                        .formatted(
                            targetGroup.getId(), destinationZone.getId(), targetGroup.getId())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.targets[0].executionStatus").value("COMPLETED"))
        .andExpect(jsonPath("$.data.targets[0].resultDetails.identityPreserved").value(true));
    Assertions.assertThat(
            orchidGroupRepository.findById(targetGroup.getId()).orElseThrow().getBedZone().getId())
        .isEqualTo(destinationZone.getId());

    mockMvc
        .perform(get("/api/work-operations/{id}/details", operationId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.executions", hasSize(1)))
        .andExpect(jsonPath("$.data.executions[0].resultType").value("MOVEMENT"))
        .andExpect(
            jsonPath("$.data.executions[0].sources[0].orchidGroupId").value(targetGroup.getId()))
        .andExpect(
            jsonPath("$.data.executions[0].results[0].bedZoneId").value(destinationZone.getId()))
        .andExpect(jsonPath("$.data.executions[0].results[0].startPosition").value(0))
        .andExpect(jsonPath("$.data.executions[0].results[0].endPosition").value(10));
  }

  @Test
  void preservesHouseTargetSnapshotAfterOrchidGroupMoves() throws Exception {
    mockMvc
        .perform(
            post("/api/work-operations/target-preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
				{
				  "sourceScopeType": "HOUSE",
				  "sourceScopeId": %d
				}
				"""
                        .formatted(sourceHouse.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.orchidGroupCount").value(1))
        .andExpect(jsonPath("$.data.totalQuantity").value(100))
        .andExpect(jsonPath("$.data.targets[0].orchidGroupId").value(targetGroup.getId()))
        .andExpect(jsonPath("$.data.targets[0].locationSnapshot.houseNumber").value(3));

    var createResult =
        mockMvc
            .perform(
                post("/api/work-operations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
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
					"""
                            .formatted(pesticideType.getId(), sourceHouse.getId())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.status").value("PLANNED"))
            .andExpect(jsonPath("$.data.targets", hasSize(1)))
            .andReturn();

    String response = createResult.getResponse().getContentAsString();
    Long operationId = requiredId(response, "/data/id");
    Long targetId =
        workOperationTargetRepository
            .findByWorkOperationIdAndExcludedAtIsNullOrderByIdAsc(operationId)
            .getFirst()
            .getId();

    mockMvc
        .perform(post("/api/work-operations/{id}/start", operationId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
    mockMvc
        .perform(
            post("/api/work-operations/{id}/targets/{targetId}/complete", operationId, targetId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
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

    mockMvc
        .perform(
            post("/api/work-operations/{id}/complete", operationId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"completedDate\":\"2026-07-16\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("COMPLETED"))
        .andExpect(jsonPath("$.data.targets[0].executionStatus").value("COMPLETED"));
    Assertions.assertThat(
            TimeConfig.toFarmTime(
                    workTargetExecutionRepository
                        .findByTargetWorkOperationIdOrderByIdAsc(operationId)
                        .getFirst()
                        .getCompletedAt())
                .toLocalDate())
        .isEqualTo(LocalDate.of(2026, 7, 15));
    Assertions.assertThat(
            TimeConfig.toFarmTime(
                    workOperationRepository.findById(operationId).orElseThrow().getActualEndAt())
                .toLocalDate())
        .isEqualTo(LocalDate.of(2026, 7, 15));

    mockMvc
        .perform(
            post("/api/work-operations/{id}/targets/{targetId}/complete", operationId, targetId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
						{
						  "worker": "중복 작업자",
						  "resultDetails": {"weather": "변경 시도"}
						}
						"""))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REUSED"));
    Assertions.assertThat(
            workAppliedEffectRepository.countByWorkOperationIdAndTargetId(operationId, targetId))
        .isEqualTo(1);

    mockMvc
        .perform(
            get("/api/work-history")
                .param("historyScopeType", "HOUSE")
                .param("historyScopeId", sourceHouse.getId().toString())
                .param("page", "0")
                .param("size", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content", hasSize(1)))
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].workOperationId").value(operationId));

    mockMvc
        .perform(movementRecord(targetGroup.getId(), destinationZone.getId(), 1, 10))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/orchid-groups/{id}/work-history", targetGroup.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data", hasSize(2)))
        .andExpect(
            jsonPath("$.data[?(@.sourceKind == 'WORK_OPERATION')].workOperationId")
                .value(hasItem(operationId.intValue())))
        .andExpect(
            jsonPath("$.data[?(@.sourceKind == 'WORK_OPERATION')].propagated").value(hasItem(true)))
        .andExpect(
            jsonPath("$.data[?(@.sourceKind == 'WORK_OPERATION')].locationSnapshot.houseNumber")
                .value(hasItem(3)))
        .andExpect(
            jsonPath("$.data[?(@.sourceKind == 'WORK_OPERATION')].currentLocation.houseNumber")
                .value(hasItem(5)))
        .andExpect(
            jsonPath("$.data[?(@.sourceKind == 'WORK_OPERATION')].workType")
                .value(hasItem("자리 이동")));

    mockMvc
        .perform(
            get("/api/work-history")
                .param("historyScopeType", "ORCHID_GROUP")
                .param("historyScopeId", targetGroup.getId().toString())
                .param("page", "0")
                .param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content", hasSize(1)))
        .andExpect(jsonPath("$.data.page").value(0))
        .andExpect(jsonPath("$.data.size").value(1))
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.totalPages").value(2))
        .andExpect(jsonPath("$.data.content[0].workType").value("자리 이동"))
        .andExpect(jsonPath("$.data.content[0].currentLocation.houseNumber").value(5));

    mockMvc
        .perform(
            get("/api/work-history")
                .param("historyScopeType", "ORCHID_GROUP")
                .param("historyScopeId", targetGroup.getId().toString())
                .param("page", "1")
                .param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content", hasSize(1)))
        .andExpect(jsonPath("$.data.page").value(1))
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.content[0].workOperationId").value(operationId));
  }

  @Test
  void removedDirectMoveEndpointDoesNotChangeGroupsOrCreateWork() throws Exception {
    mockMvc
        .perform(
            patch("/api/orchid-groups/{id}/move", targetGroup.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isNotFound());

    Assertions.assertThat(workOperationRepository.count()).isZero();
  }

  @Test
  void searchesOperationsByOverlappingPeriodStatusAndScope() throws Exception {
    mockMvc
        .perform(
            post("/api/work-operations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
				{
				  "workTypeId": %d,
				  "title": "7월 기간 농약 작업",
				  "plannedStartDate": "2026-07-10",
				  "plannedEndDate": "2026-07-20",
				  "sourceScopeType": "HOUSE",
				  "sourceScopeId": %d
				}
				"""
                        .formatted(pesticideType.getId(), sourceHouse.getId())))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            post("/api/work-operations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
				{
				  "workTypeId": %d,
				  "title": "7월 기간 농약 작업 2",
				  "plannedStartDate": "2026-07-21",
				  "sourceScopeType": "HOUSE",
				  "sourceScopeId": %d
				}
				"""
                        .formatted(pesticideType.getId(), sourceHouse.getId())))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            get("/api/work-operations")
                .param("from", "2026-07-15")
                .param("to", "2026-07-31")
                .param("status", "PLANNED")
                .param("sourceScopeType", "HOUSE")
                .param("sourceScopeId", sourceHouse.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content", hasSize(2)))
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.content[0].title").value("7월 기간 농약 작업 2"))
        .andExpect(jsonPath("$.data.content[0].progress.total").value(1))
        .andExpect(jsonPath("$.data.content[0].targets").doesNotExist());

    mockMvc
        .perform(get("/api/work-operations").param("from", "2026-08-01").param("to", "2026-08-31"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content", hasSize(0)));

    mockMvc
        .perform(
            get("/api/work-operations")
                .param("keyword", "농약")
                .param("page", "0")
                .param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content", hasSize(1)))
        .andExpect(jsonPath("$.data.page").value(0))
        .andExpect(jsonPath("$.data.size").value(1))
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.totalPages").value(2))
        .andExpect(jsonPath("$.data.content[0].title").value("7월 기간 농약 작업 2"))
        .andExpect(jsonPath("$.data.content[0].progress.total").value(1))
        .andExpect(jsonPath("$.data.content[0].targets").doesNotExist());

    mockMvc
        .perform(
            get("/api/work-operations")
                .param("keyword", "농약")
                .param("page", "1")
                .param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content", hasSize(1)))
        .andExpect(jsonPath("$.data.page").value(1))
        .andExpect(jsonPath("$.data.content[0].title").value("7월 기간 농약 작업"));

    mockMvc
        .perform(
            get("/api/work-operations/calendar")
                .param("from", "2026-07-01")
                .param("to", "2026-07-31")
                .param("status", "PLANNED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data", hasSize(2)))
        .andExpect(jsonPath("$.data[0].title").value("7월 기간 농약 작업"))
        .andExpect(jsonPath("$.data[1].title").value("7월 기간 농약 작업 2"))
        .andExpect(jsonPath("$.data[0].targets").doesNotExist());

    mockMvc
        .perform(get("/api/work-operations").param("size", "101"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void includesTodayChangedOperationInManagementList() throws Exception {
    var created =
        mockMvc
            .perform(
                post("/api/work-operations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
				{
				  "workTypeId": %d,
				  "title": "관리 대상 작업",
				  "plannedStartDate": "2026-07-17",
				  "sourceScopeType": "HOUSE",
				  "sourceScopeId": %d
				}
				"""
                            .formatted(pesticideType.getId(), sourceHouse.getId())))
            .andExpect(status().isCreated())
            .andReturn();
    Long operationId = requiredId(created.getResponse().getContentAsString(), "/data/id");

    mockMvc
        .perform(get("/api/work-operations").param("view", "MANAGEMENT"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content", hasSize(1)))
        .andExpect(jsonPath("$.data.content[0].status").value("PLANNED"));
    mockMvc
        .perform(post("/api/work-operations/{id}/end-remaining", operationId))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/work-operations").param("view", "MANAGEMENT"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content", hasSize(1)))
        .andExpect(jsonPath("$.data.content[0].status").value("STOPPED"));
  }

  @Test
  void cancelsRemainingTargetsAfterSomeTargetsAreCompleted() throws Exception {
    OrchidGroup secondGroup =
        new OrchidGroup(
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
    secondGroup = saveOrchidGroup(secondGroup);

    var created =
        mockMvc
            .perform(
                post("/api/work-operations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
				{
				  "workTypeId": %d,
				  "title": "일부 완료 후 취소",
				  "plannedStartDate": "2026-07-17",
				  "sourceScopeType": "MANUAL_SELECTION",
				  "sourceOrchidGroupIds": [%d, %d]
				}
				"""
                            .formatted(
                                pesticideType.getId(), targetGroup.getId(), secondGroup.getId())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.targets", hasSize(2)))
            .andReturn();
    Long operationId = requiredId(created.getResponse().getContentAsString(), "/data/id");
    Long completedTargetId =
        workOperationTargetRepository
            .findByWorkOperationIdAndExcludedAtIsNullOrderByIdAsc(operationId)
            .getFirst()
            .getId();

    mockMvc
        .perform(post("/api/work-operations/{id}/start", operationId))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post(
                    "/api/work-operations/{id}/targets/{targetId}/complete",
                    operationId,
                    completedTargetId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
						{"worker": "작업자", "resultDetails": {"weather": "맑음"}}
						"""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.progress.completed").value(1))
        .andExpect(jsonPath("$.data.progress.pending").value(1));

    mockMvc
        .perform(post("/api/work-operations/{id}/end-remaining", operationId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("STOPPED"))
        .andExpect(jsonPath("$.data.actualEndAt").exists())
        .andExpect(jsonPath("$.data.progress.completed").value(1))
        .andExpect(jsonPath("$.data.progress.canceled").value(1))
        .andExpect(jsonPath("$.data.targets[?(@.executionStatus == 'COMPLETED')]", hasSize(1)))
        .andExpect(jsonPath("$.data.targets[?(@.executionStatus == 'CANCELED')]", hasSize(1)));

    Assertions.assertThat(
            workAppliedEffectRepository.countByWorkOperationIdAndTargetId(
                operationId, completedTargetId))
        .isEqualTo(1);
  }

  @Test
  void createsAnImmediatelyCompletedRecordOperation() throws Exception {
    mockMvc
        .perform(
            post("/api/work-operations/record")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
				{
				  "workTypeId": %d,
				  "title": "구역 농약 기록",
				  "plannedStartDate": "2026-07-15",
				  "sourceScopeType": "BED_ZONE",
				  "sourceScopeId": %d,
				  "details": {"materialName": "살균제"},
				  "worker": "테스터"
				}
				"""
                        .formatted(pesticideType.getId(), targetGroup.getBedZone().getId())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.status").value("COMPLETED"))
        .andExpect(jsonPath("$.data.targets", hasSize(1)))
        .andExpect(jsonPath("$.data.targets[0].executionStatus").value("COMPLETED"))
        .andExpect(jsonPath("$.data.targets[0].resultDetails.materialName").value("살균제"));
  }
}
