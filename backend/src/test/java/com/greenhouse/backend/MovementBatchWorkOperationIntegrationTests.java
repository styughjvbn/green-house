package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.structure.BedZone;
import com.greenhouse.backend.farm.domain.structure.BedZoneSide;
import com.greenhouse.backend.farm.domain.structure.House;
import com.greenhouse.backend.farm.domain.structure.PhysicalBed;
import com.greenhouse.backend.farm.domain.variety.Variety;
import com.greenhouse.backend.farm.repository.transformation.OrchidGroupLineageRepository;
import com.greenhouse.backend.work.domain.operation.WorkType;
import com.greenhouse.backend.work.domain.operation.WorkTypeDefinition;
import com.greenhouse.backend.work.domain.operation.WorkTypeTemplate;
import com.greenhouse.backend.work.repository.WorkOperationRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class MovementBatchWorkOperationIntegrationTests extends AbstractBackendIntegrationTest {

	@Autowired
	private OrchidGroupLineageRepository lineageRepository;

	@Autowired
	private WorkOperationRepository operationRepository;

	private BedZone sourceZone;

	private BedZone destinationZone;

	private WorkType movementType;

	private Variety firstVariety;

	private Variety secondVariety;

	@BeforeEach
	void setUp() {
		movementType = workTypeRepository.save(new WorkType(WorkTypeDefinition.MOVEMENT.name(), "자리 이동",
				WorkTypeTemplate.MOVEMENT, true, true, true, 1));
		workTypeRepository
			.save(new WorkType(WorkTypeDefinition.DISCARD.name(), "폐기", WorkTypeTemplate.DISCARD, true, true, true, 2));
		House house = new House(9920, "자리 이동 테스트동");
		PhysicalBed bed = new PhysicalBed(1, 1);
		bed.updatePositionUnits(new BigDecimal("30"), "칸");
		sourceZone = new BedZone("원본", BedZoneSide.LEFT, 1);
		destinationZone = new BedZone("목적", BedZoneSide.RIGHT, 2);
		bed.addBedZone(sourceZone);
		bed.addBedZone(destinationZone);
		house.addPhysicalBed(bed);
		houseRepository.save(house);
		firstVariety = varietyRepository
			.save(new Variety("MOVE-BATCH-1", "팔레놉시스", "이동 품종 1", null, "4치", true, true, null, null));
		secondVariety = varietyRepository
			.save(new Variety("MOVE-BATCH-2", "덴드로비움", "이동 품종 2", null, "3치", true, true, null, null));
	}

	@Test
	void movesMultipleSourcesOfOneVarietyAndRecordsDiscardedQuantity() throws Exception {
		OrchidGroup first = createSource(firstVariety, 10, 0, 1, 1);
		OrchidGroup second = createSource(firstVariety, 20, 1, 3, 2);

		var created = mockMvc.perform(post("/api/work-operations").contentType(MediaType.APPLICATION_JSON).content("""
				{
				  "workTypeId": %d,
				  "title": "다중 자리 이동",
				  "plannedStartDate": "2026-08-09",
				  "sourceScopeType": "MANUAL_SELECTION",
				  "sourceOrchidGroupIds": [%d, %d]
				}
				""".formatted(movementType.getId(), first.getId(), second.getId())))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.targets", hasSize(2)))
			.andReturn();
		Long operationId = Long.valueOf(
				created.getResponse().getContentAsString().replaceAll(".*?\\\"data\\\":\\{\\\"id\\\":(\\d+).*", "$1"));

		mockMvc.perform(post("/api/work-operations/{id}/start", operationId)).andExpect(status().isOk());
		mockMvc.perform(post("/api/work-operations/{id}/structure-change-executions", operationId)
			.contentType(MediaType.APPLICATION_JSON)
			.content(
					"""
							{
							  "idempotencyKey": "movement-batch-1",
							  "completedDate": "2026-08-09",
							  "worker": "이동 담당자",
							  "sources": [
							    {"sourceOrchidGroupId": %d, "inputQuantity": 10},
							    {"sourceOrchidGroupId": %d, "inputQuantity": 20}
							  ],
							  "results": [
							    {"bedZoneId": %d, "quantity": 26, "sourceOrchidGroupIds": [%d, %d], "potSize": "6치", "ageYear": 9, "purpose": "HELD", "startPosition": 0, "endPosition": 3}
							  ]
							}
							"""
						.formatted(first.getId(), second.getId(), sourceZone.getId(), first.getId(), second.getId())))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.status").value("COMPLETED"))
			.andExpect(jsonPath("$.data.targets[0].resultDetails.lossQuantity").value(4))
			.andExpect(jsonPath("$.data.targets[0].resultDetails.discardWorkOperationId").isNumber());

		assertThat(orchidGroupRepository.findById(first.getId()).orElseThrow().getQuantity()).isZero();
		assertThat(orchidGroupRepository.findById(second.getId()).orElseThrow().getQuantity()).isZero();
		var results = orchidGroupRepository.findByBedZoneIdAndQuantityGreaterThanOrderBySortOrderAsc(sourceZone.getId(),
				0);
		assertThat(results).extracting(OrchidGroup::getQuantity).containsExactly(26);
		assertThat(results).extracting(group -> group.getVariety().getId()).containsOnly(firstVariety.getId());
		assertThat(results.getFirst().getPotSize()).isEqualTo(first.getPotSize());
		assertThat(results.getFirst().getAgeYear()).isEqualTo(first.getAgeYear());
		assertThat(results.getFirst().getStatus()).isEqualTo("정상");
		assertThat(lineageRepository.findAll()).isEmpty();
		mockMvc.perform(get("/api/orchid-groups/{id}/lineage", results.getFirst().getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.transformations", hasSize(1)))
			.andExpect(jsonPath("$.data.transformations[0].relationType").value("MOVED_TO"))
			.andExpect(jsonPath("$.data.transformations[0].totalInputQuantity").value(30))
			.andExpect(jsonPath("$.data.transformations[0].totalResultQuantity").value(26))
			.andExpect(jsonPath("$.data.transformations[0].sources", hasSize(2)))
			.andExpect(jsonPath("$.data.transformations[0].results", hasSize(1)));
		var discardOperations = operationRepository.findAll()
			.stream()
			.filter(operation -> WorkTypeDefinition.DISCARD.name().equals(operation.getWorkType().getCode()))
			.toList();
		assertThat(discardOperations).hasSize(1);
		assertThat(discardOperations.getFirst().getStatus().name()).isEqualTo("COMPLETED");
		assertThat(discardOperations.getFirst().getDetails()).containsEntry("movementOperationId", operationId);
		assertThat(discardOperations.getFirst().getTitle()).endsWith("동시 폐기");
	}

	@Test
	void createsCompletedMovementRecordForMultipleSourcesOfOneVariety() throws Exception {
		OrchidGroup first = createSource(firstVariety, 10, 0, 1, 1);
		OrchidGroup second = createSource(firstVariety, 20, 1, 3, 2);

		mockMvc
			.perform(post("/api/work-operations/structure-change-records/batch").contentType(MediaType.APPLICATION_JSON)
				.content(
						"""
								{
								  "records": [{
								    "operation": {
								      "workTypeId": %d,
								      "title": "다중 자리 이동 기록",
								      "plannedStartDate": "2026-08-09",
								      "sourceScopeType": "MANUAL_SELECTION",
								      "sourceOrchidGroupIds": [%d, %d]
								    },
								    "execution": {
								      "idempotencyKey": "movement-record-1",
								      "completedDate": "2026-08-09",
								      "sources": [
								        {"sourceOrchidGroupId": %d, "inputQuantity": 10},
								        {"sourceOrchidGroupId": %d, "inputQuantity": 20}
								      ],
								      "results": [
								        {"bedZoneId": %d, "quantity": 9, "sourceOrchidGroupIds": [%d], "potSize": "4치", "ageYear": 2, "purpose": "NORMAL", "startPosition": 0, "endPosition": 1},
								        {"bedZoneId": %d, "quantity": 18, "sourceOrchidGroupIds": [%d], "potSize": "3치", "ageYear": 2, "purpose": "NORMAL", "startPosition": 1, "endPosition": 3}
								      ]
								    }
								  }]
								}
								"""
							.formatted(movementType.getId(), first.getId(), second.getId(), first.getId(),
									second.getId(), destinationZone.getId(), first.getId(), destinationZone.getId(),
									second.getId())))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data", hasSize(1)))
			.andExpect(jsonPath("$.data[0].workTypeCode").value("MOVEMENT"))
			.andExpect(jsonPath("$.data[0].status").value("COMPLETED"))
			.andExpect(jsonPath("$.data[0].targets[0].resultDetails.lossQuantity").value(3));

		assertThat(orchidGroupRepository
			.findByBedZoneIdAndQuantityGreaterThanOrderBySortOrderAsc(destinationZone.getId(), 0))
			.extracting(OrchidGroup::getQuantity)
			.containsExactly(9, 18);
	}

	@Test
	void reusesPositionsReleasedByAnotherVarietyInTheSameRecordBatch() throws Exception {
		OrchidGroup first = createSource(firstVariety, 10, 0, 5, 1);
		OrchidGroup second = createSource(secondVariety, 20, 5, 15, 2);

		mockMvc
			.perform(post("/api/work-operations/structure-change-records/batch").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "records": [
						    {
						      "operation": {
						        "workTypeId": %d,
						        "title": "품종 A 자리 이동",
						        "plannedStartDate": "2026-08-09",
						        "sourceScopeType": "MANUAL_SELECTION",
						        "sourceOrchidGroupIds": [%d]
						      },
						      "execution": {
						        "idempotencyKey": "cross-variety-position-a",
						        "completedDate": "2026-08-09",
						        "sources": [{"sourceOrchidGroupId": %d, "inputQuantity": 10}],
						        "results": [{
						          "bedZoneId": %d,
						          "quantity": 10,
						          "attributeSourceOrchidGroupId": %d,
						          "purpose": "NORMAL",
						          "startPosition": 10,
						          "endPosition": 15
						        }]
						      }
						    },
						    {
						      "operation": {
						        "workTypeId": %d,
						        "title": "품종 B 자리 이동",
						        "plannedStartDate": "2026-08-09",
						        "sourceScopeType": "MANUAL_SELECTION",
						        "sourceOrchidGroupIds": [%d]
						      },
						      "execution": {
						        "idempotencyKey": "cross-variety-position-b",
						        "completedDate": "2026-08-09",
						        "sources": [{"sourceOrchidGroupId": %d, "inputQuantity": 20}],
						        "results": [{
						          "bedZoneId": %d,
						          "quantity": 20,
						          "attributeSourceOrchidGroupId": %d,
						          "purpose": "NORMAL",
						          "startPosition": 5,
						          "endPosition": 10
						        }]
						      }
						    }
						  ]
						}
						""".formatted(movementType.getId(), first.getId(), first.getId(), sourceZone.getId(),
						first.getId(), movementType.getId(), second.getId(), second.getId(), sourceZone.getId(),
						second.getId())))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data", hasSize(2)))
			.andExpect(jsonPath("$.data[0].status").value("COMPLETED"))
			.andExpect(jsonPath("$.data[1].status").value("COMPLETED"));

		assertThat(orchidGroupRepository.findById(first.getId()).orElseThrow().getQuantity()).isZero();
		assertThat(orchidGroupRepository.findById(second.getId()).orElseThrow().getQuantity()).isZero();
		var results = orchidGroupRepository.findByBedZoneIdAndQuantityGreaterThanOrderBySortOrderAsc(sourceZone.getId(),
				0);
		assertThat(results).hasSize(2);
		assertThat(results).anySatisfy(result -> {
			assertThat(result.getVariety().getId()).isEqualTo(firstVariety.getId());
			assertThat(result.getStartPosition()).isEqualByComparingTo("10.00");
			assertThat(result.getEndPosition()).isEqualByComparingTo("15.00");
		});
		assertThat(results).anySatisfy(result -> {
			assertThat(result.getVariety().getId()).isEqualTo(secondVariety.getId());
			assertThat(result.getStartPosition()).isEqualByComparingTo("5.00");
			assertThat(result.getEndPosition()).isEqualByComparingTo("10.00");
		});
	}

	@Test
	void createsSeparateMovementPlansForEachVariety() throws Exception {
		OrchidGroup first = createSource(firstVariety, 10, 0, 1, 1);
		OrchidGroup second = createSource(secondVariety, 20, 1, 3, 2);

		mockMvc.perform(post("/api/work-operations/batch").contentType(MediaType.APPLICATION_JSON).content("""
				{
				  "operation": {
				    "workTypeId": %d,
				    "title": "품종별 자리 이동",
				    "plannedStartDate": "2026-08-09",
				    "sourceScopeType": "MANUAL_SELECTION",
				    "sourceOrchidGroupIds": [%d, %d]
				  }
				}
				""".formatted(movementType.getId(), first.getId(), second.getId())))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data", hasSize(2)))
			.andExpect(jsonPath("$.data[0].workType").value("자리 이동"))
			.andExpect(jsonPath("$.data[0].targets", hasSize(1)))
			.andExpect(jsonPath("$.data[1].targets", hasSize(1)));
	}

	@Test
	void poolsSourceQuantitiesBeforeSplittingMovementResults() throws Exception {
		OrchidGroup first = createSource(firstVariety, 10, 0, 1, 1);
		OrchidGroup second = createSource(firstVariety, 20, 1, 3, 2);

		var created = mockMvc.perform(post("/api/work-operations").contentType(MediaType.APPLICATION_JSON).content("""
				{
				  "workTypeId": %d,
				  "title": "원본별 수량 검증",
				  "plannedStartDate": "2026-08-09",
				  "sourceScopeType": "MANUAL_SELECTION",
				  "sourceOrchidGroupIds": [%d, %d]
				}
				""".formatted(movementType.getId(), first.getId(), second.getId())))
			.andExpect(status().isCreated())
			.andReturn();
		Long operationId = Long.valueOf(
				created.getResponse().getContentAsString().replaceAll(".*?\\\"data\\\":\\{\\\"id\\\":(\\d+).*", "$1"));
		mockMvc.perform(post("/api/work-operations/{id}/start", operationId)).andExpect(status().isOk());

		mockMvc
			.perform(post("/api/work-operations/{id}/structure-change-executions", operationId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(
						"""
								{
								  "idempotencyKey": "movement-over-input",
								  "completedDate": "2026-08-09",
								  "sources": [
								    {"sourceOrchidGroupId": %d, "inputQuantity": 10},
								    {"sourceOrchidGroupId": %d, "inputQuantity": 20}
								  ],
								  "results": [
								    {"bedZoneId": %d, "quantity": 15, "sourceOrchidGroupIds": [%d], "potSize": "4치", "ageYear": 2, "purpose": "NORMAL", "startPosition": 0, "endPosition": 1},
								    {"bedZoneId": %d, "quantity": 10, "sourceOrchidGroupIds": [%d], "potSize": "3치", "ageYear": 2, "purpose": "NORMAL", "startPosition": 1, "endPosition": 2}
								  ]
								}
								"""
							.formatted(first.getId(), second.getId(), destinationZone.getId(), first.getId(),
									destinationZone.getId(), second.getId())))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.status").value("COMPLETED"));

		assertThat(orchidGroupRepository.findById(first.getId()).orElseThrow().getQuantity()).isZero();
		assertThat(orchidGroupRepository.findById(second.getId()).orElseThrow().getQuantity()).isZero();
		assertThat(orchidGroupRepository
			.findByBedZoneIdAndQuantityGreaterThanOrderBySortOrderAsc(destinationZone.getId(), 0))
			.extracting(OrchidGroup::getQuantity)
			.containsExactly(15, 10);
	}

	private OrchidGroup createSource(Variety variety, int quantity, int start, int end, int sortOrder) {
		OrchidGroup group = new OrchidGroup(sourceZone, variety.getGenus(), variety.getName(), quantity,
				variety.getDefaultPotSize(), 2, "정상", sortOrder, BigDecimal.valueOf(start), BigDecimal.valueOf(end));
		group.assignVariety(variety);
		return saveOrchidGroup(group);
	}

}
