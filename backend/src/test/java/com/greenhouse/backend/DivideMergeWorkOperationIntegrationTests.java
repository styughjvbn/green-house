package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.greenhouse.backend.farm.domain.BedZone;
import com.greenhouse.backend.farm.domain.BedZoneSide;
import com.greenhouse.backend.farm.domain.House;
import com.greenhouse.backend.farm.domain.OrchidGroup;
import com.greenhouse.backend.farm.domain.OrchidGroupLineageRelationType;
import com.greenhouse.backend.farm.domain.PhysicalBed;
import com.greenhouse.backend.farm.domain.Variety;
import com.greenhouse.backend.farm.repository.OrchidGroupLineageRepository;
import com.greenhouse.backend.work.domain.WorkType;
import com.greenhouse.backend.work.domain.WorkTypeTemplate;
import com.greenhouse.backend.work.repository.WorkAppliedEffectRepository;
import com.greenhouse.backend.work.repository.WorkEffectOrchidGroupRepository;
import com.greenhouse.backend.work.repository.WorkOperationRepository;
import com.greenhouse.backend.work.repository.WorkOperationTargetRepository;
import com.greenhouse.backend.work.repository.WorkTargetExecutionRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class DivideMergeWorkOperationIntegrationTests extends AbstractBackendIntegrationTest {

	@Autowired private OrchidGroupLineageRepository lineageRepository;
	@Autowired private WorkEffectOrchidGroupRepository effectOrchidGroupRepository;
	@Autowired private WorkAppliedEffectRepository appliedEffectRepository;
	@Autowired private WorkTargetExecutionRepository targetExecutionRepository;
	@Autowired private WorkOperationTargetRepository operationTargetRepository;
	@Autowired private WorkOperationRepository operationRepository;

	private BedZone sourceZone;
	private BedZone resultZone;
	private Variety variety;
	private WorkType repotType;
	private WorkType divideType;
	private WorkType mergeType;

	@BeforeEach
	void setUp() {
		lineageRepository.deleteAll();
		effectOrchidGroupRepository.deleteAll();
		appliedEffectRepository.deleteAll();
		targetExecutionRepository.deleteAll();
		operationTargetRepository.deleteAll();
		operationRepository.deleteAll();
		workRecordRepository.deleteAll();
		orchidGroupRepository.deleteAll();
		varietyRepository.deleteAll();
		bedZoneRepository.deleteAll();
		physicalBedRepository.deleteAll();
		houseRepository.deleteAll();
		workTypeRepository.deleteAll();

		repotType = workTypeRepository.save(new WorkType(
				WorkType.REPOT_CODE, "분갈이", WorkTypeTemplate.REPOT, true, true, true, 1));
		divideType = workTypeRepository.save(new WorkType(
				WorkType.DIVIDE_CODE, "분주", WorkTypeTemplate.REPOT, true, true, true, 2));
		mergeType = workTypeRepository.save(new WorkType(
				WorkType.MERGE_CODE, "합식", WorkTypeTemplate.REPOT, true, true, true, 3));
		House house = new House(1, "1동");
		PhysicalBed bed = new PhysicalBed(1, 1);
		bed.updatePositionUnits(new BigDecimal("24"), "칸");
		sourceZone = new BedZone("좌측", BedZoneSide.LEFT, 1);
		resultZone = new BedZone("우측", BedZoneSide.RIGHT, 2);
		bed.addBedZone(sourceZone);
		bed.addBedZone(resultZone);
		house.addPhysicalBed(bed);
		houseRepository.save(house);
		variety = varietyRepository.save(new Variety(
				"STRUCTURE-001", "팔레놉시스", "구조 변경 테스트", null,
				"3.5치", true, true, null, null));
	}

	@Test
	void dividesOneSourceIntoMultipleResultsWithSplitLineage() throws Exception {
		OrchidGroup source = createSource(30, "0", "3");
		Long operationId = createPlan(divideType, source.getId());
		Long targetId = operationTargetRepository
				.findByWorkOperationIdAndExcludedAtIsNullOrderByIdAsc(operationId).getFirst().getId();

		mockMvc.perform(post("/api/work-operations/{id}/start", operationId))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/work-operations/{id}/targets/{targetId}/complete", operationId, targetId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "worker": "분주 담당자",
						  "resultDetails": {
						    "idempotencyKey": "divide-period-1",
						    "title": "분주 실행",
						    "workDate": "2026-07-16",
						    "sourceOrchidGroupId": %d,
						    "inputQuantity": 30,
						    "lossQuantity": 0,
						    "results": [
						      {"bedZoneId": %d, "quantity": 10, "potSize": "3치", "ageYear": 1, "startPosition": 0, "endPosition": 1},
						      {"bedZoneId": %d, "quantity": 20, "potSize": "3치", "ageYear": 1, "startPosition": 1, "endPosition": 2}
						    ],
						    "inheritCollectionIds": []
						  }
						}
						""".formatted(source.getId(), resultZone.getId(), resultZone.getId())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.targets[0].executionStatus").value("COMPLETED"));

		assertThat(orchidGroupRepository.findById(source.getId()).orElseThrow().getQuantity()).isZero();
		assertThat(lineageRepository.findBySourceOrchidGroupIdOrderByCreatedAtAscIdAsc(source.getId()))
				.hasSize(2)
				.allMatch(lineage -> lineage.getRelationType() == OrchidGroupLineageRelationType.SPLIT_TO);
	}

	@Test
	void rejectsMixedVarietiesForEachStructureChangePlan() throws Exception {
		OrchidGroup first = createSource(20, "0", "1");
		Variety anotherVariety = varietyRepository.save(new Variety(
				"STRUCTURE-002", "팔레놉시스", "다른 구조 변경 품종", null,
				"3.5치", true, true, null, null));
		OrchidGroup second = createSource(anotherVariety, 20, "1", "2");
		String sourceIds = first.getId() + "," + second.getId();

		for (WorkType workType : java.util.List.of(repotType, divideType, mergeType)) {
			mockMvc.perform(post("/api/work-operations")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "workTypeId": %d,
							  "title": "혼합 품종 작업",
							  "plannedStartDate": "2026-07-16",
							  "sourceScopeType": "MANUAL_SELECTION",
							  "sourceOrchidGroupIds": [%s]
							}
							""".formatted(workType.getId(), sourceIds)))
					.andExpect(status().isBadRequest());
		}
		assertThat(operationRepository.count()).isZero();
	}

	@Test
	void mergesMultipleSourcesIntoOneResultAtomically() throws Exception {
		OrchidGroup first = createSource(10, "0", "1");
		OrchidGroup second = createSource(20, "1", "2");
		Long operationId = createPlan(mergeType, first.getId(), second.getId());

		mockMvc.perform(post("/api/work-operations/{id}/start", operationId))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/work-operations/{id}/merge/complete", operationId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "worker": "합식 담당자",
						  "resultDetails": {
						    "sources": [
						      {"sourceOrchidGroupId": %d, "inputQuantity": 10},
						      {"sourceOrchidGroupId": %d, "inputQuantity": 20}
						    ],
						    "lossQuantity": 0,
						    "result": {
						      "bedZoneId": %d,
						      "quantity": 30,
						      "potSize": "4치",
						      "ageYear": 2,
						      "startPosition": 0,
						      "endPosition": 2
						    }
						  }
						}
						""".formatted(first.getId(), second.getId(), resultZone.getId())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.targets", hasSize(2)))
				.andExpect(jsonPath("$.data.targets[0].executionStatus").value("COMPLETED"))
				.andExpect(jsonPath("$.data.targets[1].executionStatus").value("COMPLETED"));

		assertThat(orchidGroupRepository.findById(first.getId()).orElseThrow().getQuantity()).isZero();
		assertThat(orchidGroupRepository.findById(second.getId()).orElseThrow().getQuantity()).isZero();
		var resultGroups = orchidGroupRepository.findByBedZoneIdAndQuantityGreaterThanOrderBySortOrderAsc(
				resultZone.getId(), 0);
		assertThat(resultGroups).singleElement().satisfies(result -> {
			assertThat(result.getQuantity()).isEqualTo(30);
			assertThat(lineageRepository.findByResultOrchidGroupIdOrderByCreatedAtAscIdAsc(result.getId()))
					.hasSize(2)
					.allMatch(lineage -> lineage.getRelationType() == OrchidGroupLineageRelationType.MERGED_TO);
		});
	}

	@Test
	void recordsPartialBatchAndCreatesMultiplePurposeResults() throws Exception {
		OrchidGroup source = createSource(30, "0", "3");
		Long operationId = createPlan(divideType, source.getId());

		mockMvc.perform(post("/api/work-operations/{id}/start", operationId))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/work-operations/{id}/structure-change-executions", operationId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "idempotencyKey": "divide-batch-1",
						  "worker": "분주 담당자",
						  "sources": [
						    {"sourceOrchidGroupId": %d, "inputQuantity": 10}
						  ],
						  "lossQuantity": 0,
						  "results": [
						    {"bedZoneId": %d, "quantity": 6, "sourceOrchidGroupIds": [%d], "potSize": "3.5치", "ageYear": 2, "purpose": "NORMAL", "startPosition": 0, "endPosition": 1},
						    {"bedZoneId": %d, "quantity": 4, "sourceOrchidGroupIds": [%d], "potSize": "3.5치", "ageYear": 2, "purpose": "DIVIDE_CANDIDATE", "startPosition": 1, "endPosition": 2}
						  ]
						}
						""".formatted(
								source.getId(), resultZone.getId(), source.getId(), resultZone.getId(), source.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.targets[0].executionStatus").value("PARTIALLY_COMPLETED"))
				.andExpect(jsonPath("$.data.targets[0].processedQuantity").value(10))
				.andExpect(jsonPath("$.data.targets[0].remainingQuantity").value(20))
				.andExpect(jsonPath("$.data.progress.progressPercent").value(33));

		assertThat(orchidGroupRepository.findById(source.getId()).orElseThrow().getQuantity()).isEqualTo(20);
		assertThat(orchidGroupRepository.findByBedZoneIdAndQuantityGreaterThanOrderBySortOrderAsc(
				resultZone.getId(), 0))
				.hasSize(2)
				.extracting(OrchidGroup::getStatus)
				.containsExactly("정상", "분주 예정");
		assertThat(appliedEffectRepository.findByWorkOperationIdOrderByIdAsc(operationId)).hasSize(1);

		Long targetId = operationTargetRepository
				.findByWorkOperationIdAndExcludedAtIsNullOrderByIdAsc(operationId).getFirst().getId();
		mockMvc.perform(post("/api/work-operations/{id}/targets/{targetId}/skip", operationId, targetId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"worker\":\"분주 담당자\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.targets[0].executionStatus").value("SKIPPED"))
				.andExpect(jsonPath("$.data.progress.progressPercent").value(100));
	}

	@Test
	void mergesMultiplePlannedSourcesIntoOneBatchResult() throws Exception {
		OrchidGroup first = createSource(8, "0", "1");
		OrchidGroup second = createSource(12, "1", "2");
		Long operationId = createPlan(mergeType, first.getId(), second.getId());

		mockMvc.perform(post("/api/work-operations/{id}/start", operationId))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/work-operations/{id}/structure-change-executions", operationId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "idempotencyKey": "merge-batch-1",
						  "sources": [
						    {"sourceOrchidGroupId": %d, "inputQuantity": 8},
						    {"sourceOrchidGroupId": %d, "inputQuantity": 12}
						  ],
						  "lossQuantity": 0,
						  "results": [
						    {"bedZoneId": %d, "quantity": 20, "sourceOrchidGroupIds": [%d, %d], "potSize": "4치", "ageYear": 2, "purpose": "NORMAL", "startPosition": 0, "endPosition": 2}
						  ]
						}
						""".formatted(
								first.getId(), second.getId(), resultZone.getId(), first.getId(), second.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.targets[0].executionStatus").value("COMPLETED"))
				.andExpect(jsonPath("$.data.targets[1].executionStatus").value("COMPLETED"));

		var result = orchidGroupRepository
				.findByBedZoneIdAndQuantityGreaterThanOrderBySortOrderAsc(resultZone.getId(), 0).getFirst();
		assertThat(lineageRepository.findByResultOrchidGroupIdOrderByCreatedAtAscIdAsc(result.getId()))
				.hasSize(2)
				.allMatch(lineage -> lineage.getRelationType() == OrchidGroupLineageRelationType.MERGED_TO);
	}

	private Long createPlan(WorkType workType, Long... sourceIds) throws Exception {
		String ids = java.util.Arrays.stream(sourceIds).map(String::valueOf)
				.collect(java.util.stream.Collectors.joining(","));
		var result = mockMvc.perform(post("/api/work-operations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "workTypeId": %d,
						  "title": "%s 계획",
						  "plannedStartDate": "2026-07-16",
						  "sourceScopeType": "MANUAL_SELECTION",
						  "sourceOrchidGroupIds": [%s]
						}
						""".formatted(workType.getId(), workType.getName(), ids)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.workTypeCode").value(workType.getCode()))
				.andReturn();
		return Long.valueOf(result.getResponse().getContentAsString().replaceAll(
				".*?\\\"data\\\":\\{\\\"id\\\":(\\d+).*", "$1"));
	}

	private OrchidGroup createSource(int quantity, String start, String end) {
		return createSource(variety, quantity, start, end);
	}

	private OrchidGroup createSource(Variety sourceVariety, int quantity, String start, String end) {
		OrchidGroup group = new OrchidGroup(
				sourceZone, sourceVariety.getGenus(), sourceVariety.getName(), quantity, "3.5치", 2, "정상", 1,
				new BigDecimal(start), new BigDecimal(end));
		group.assignVariety(sourceVariety);
		return orchidGroupRepository.save(group);
	}
}
