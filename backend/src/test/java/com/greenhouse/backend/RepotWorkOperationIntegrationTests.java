package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.greenhouse.backend.farm.domain.BedZone;
import com.greenhouse.backend.farm.domain.BedZoneSide;
import com.greenhouse.backend.farm.domain.House;
import com.greenhouse.backend.farm.domain.OrchidGroup;
import com.greenhouse.backend.farm.domain.OrchidGroupCollection;
import com.greenhouse.backend.farm.domain.OrchidGroupCollectionMember;
import com.greenhouse.backend.farm.domain.OrchidGroupLineageRelationType;
import com.greenhouse.backend.farm.domain.PhysicalBed;
import com.greenhouse.backend.farm.domain.Variety;
import com.greenhouse.backend.farm.repository.OrchidGroupCollectionMemberRepository;
import com.greenhouse.backend.farm.repository.OrchidGroupCollectionRepository;
import com.greenhouse.backend.farm.repository.OrchidGroupLineageRepository;
import com.greenhouse.backend.work.domain.effect.WorkEffectOrchidGroupRelationType;
import com.greenhouse.backend.work.domain.operation.WorkType;
import com.greenhouse.backend.work.domain.operation.WorkTypeTemplate;
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
class RepotWorkOperationIntegrationTests extends AbstractBackendIntegrationTest {

	@Autowired private OrchidGroupLineageRepository lineageRepository;
	@Autowired private OrchidGroupCollectionRepository collectionRepository;
	@Autowired private OrchidGroupCollectionMemberRepository memberRepository;
	@Autowired private WorkEffectOrchidGroupRepository effectOrchidGroupRepository;
	@Autowired private WorkAppliedEffectRepository appliedEffectRepository;
	@Autowired private WorkTargetExecutionRepository targetExecutionRepository;
	@Autowired private WorkOperationTargetRepository operationTargetRepository;
	@Autowired private WorkOperationRepository operationRepository;

	private BedZone bedZone;
	private Variety variety;
	private WorkType repotType;

	@BeforeEach
	void setUp() {
		lineageRepository.deleteAll();
		effectOrchidGroupRepository.deleteAll();
		appliedEffectRepository.deleteAll();
		targetExecutionRepository.deleteAll();
		operationTargetRepository.deleteAll();
		operationRepository.deleteAll();
		memberRepository.deleteAll();
		collectionRepository.deleteAll();
		orchidGroupRepository.deleteAll();
		varietyRepository.deleteAll();
		bedZoneRepository.deleteAll();
		physicalBedRepository.deleteAll();
		houseRepository.deleteAll();
		workTypeRepository.deleteAll();

		repotType = workTypeRepository.save(new WorkType(
				WorkType.REPOT_CODE, "분갈이", WorkTypeTemplate.REPOT, true, true, true, 1));
		House house = new House(1, "1동");
		PhysicalBed bed = new PhysicalBed(1, 1);
		bed.updatePositionUnits(new BigDecimal("24"), "칸");
		bedZone = new BedZone("좌측", BedZoneSide.LEFT, 1);
		bed.addBedZone(bedZone);
		house.addPhysicalBed(bed);
		houseRepository.save(house);
		variety = varietyRepository.save(new Variety(
				"REPOT-001", "팔레놉시스", "분갈이 테스트", null, "3.5치", true, true, null, null));
	}

	@Test
	void plansRepotAndAppliesStructureChangeWhenTargetIsCompleted() throws Exception {
		OrchidGroup source = createSource(40, "0", "2");
		var created = mockMvc.perform(post("/api/work-operations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "workTypeId": %d,
						  "title": "계획 분갈이",
						  "plannedStartDate": "2026-07-16",
						  "plannedEndDate": "2026-07-18",
						  "sourceScopeType": "MANUAL_SELECTION",
						  "sourceOrchidGroupIds": [%d]
						}
						""".formatted(repotType.getId(), source.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.workTypeCode").value("REPOT"))
				.andExpect(jsonPath("$.data.status").value("PLANNED"))
				.andReturn();
		Long operationId = Long.valueOf(created.getResponse().getContentAsString().replaceAll(
				".*?\\\"data\\\":\\{\\\"id\\\":(\\d+).*", "$1"));
		Long targetId = operationTargetRepository
				.findByWorkOperationIdAndExcludedAtIsNullOrderByIdAsc(operationId)
				.getFirst().getId();

		mockMvc.perform(post("/api/work-operations/{id}/start", operationId))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/work-operations/{id}/targets/{targetId}/complete", operationId, targetId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "worker": "계획 작업자",
						  "resultDetails": {
						    "idempotencyKey": "planned-repot",
						    "title": "계획 분갈이",
						    "workDate": "2026-07-16",
						    "sourceOrchidGroupId": %d,
						    "inputQuantity": 40,
						    "lossQuantity": 0,
						    "results": [{
						      "bedZoneId": %d,
						      "quantity": 40,
						      "potSize": "4치",
						      "ageYear": 3,
						      "splitPlacementAllowed": false,
						      "startPosition": 0,
						      "endPosition": 2
						    }],
						    "inheritCollectionIds": []
						  }
						}
						""".formatted(source.getId(), bedZone.getId())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.targets[0].executionStatus").value("COMPLETED"));

		assertThat(orchidGroupRepository.findById(source.getId()).orElseThrow().getQuantity()).isZero();
		assertThat(lineageRepository.findBySourceOrchidGroupIdOrderByCreatedAtAscIdAsc(source.getId()))
				.hasSize(1);
	}

	@Test
	void partiallyRepotsAndPreservesLineageHistoryAndSelectedCollections() throws Exception {
		OrchidGroup source = createSource(100, "0", "2");
		OrchidGroupCollection collection = collectionRepository.save(
				new OrchidGroupCollection("우량주", null, null, "테스터"));
		memberRepository.save(new OrchidGroupCollectionMember(collection.getId(), source.getId(), "테스터"));

		mockMvc.perform(post("/api/work-operations/repot")
				.contentType(MediaType.APPLICATION_JSON)
				.content(repotRequest(
						"repot-partial", source.getId(), 40, 2, "작업 중 손상", 38,
						"2", "4", ", \"inheritCollectionIds\": [" + collection.getId() + "]")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.operation.status").value("COMPLETED"))
				.andExpect(jsonPath("$.data.operation.sourceScopeType").value("ORCHID_GROUP"))
				.andExpect(jsonPath("$.data.sourceOrchidGroup.quantity").value(60))
				.andExpect(jsonPath("$.data.resultOrchidGroups", hasSize(1)))
				.andExpect(jsonPath("$.data.resultOrchidGroups[0].quantity").value(38))
				.andExpect(jsonPath("$.data.lossQuantity").value(2));

		Long operationId = operationRepository.findByRequestKey("repot-partial").orElseThrow().getId();
		Long resultId = orchidGroupRepository.findAll().stream()
				.map(OrchidGroup::getId).filter(id -> !id.equals(source.getId())).findFirst().orElseThrow();
		var lineage = lineageRepository.findBySourceOrchidGroupIdOrderByCreatedAtAscIdAsc(source.getId());
		assertThat(lineage).hasSize(1);
		assertThat(lineage.getFirst().getRelationType()).isEqualTo(OrchidGroupLineageRelationType.REPOTTED_TO);
		assertThat(lineage.getFirst().getSourceQuantity()).isEqualTo(40);
		assertThat(memberRepository.findByCollectionIdAndOrchidGroupIdAndRemovedAtIsNull(
				collection.getId(), resultId)).isPresent();
		assertThat(effectOrchidGroupRepository
				.findByWorkAppliedEffectWorkOperationIdAndRelationTypeOrderByIdAsc(
						operationId, WorkEffectOrchidGroupRelationType.SOURCE)).hasSize(1);
		assertThat(effectOrchidGroupRepository
				.findByWorkAppliedEffectWorkOperationIdAndRelationTypeOrderByIdAsc(
						operationId, WorkEffectOrchidGroupRelationType.RESULT)).hasSize(1);

		mockMvc.perform(get("/api/orchid-groups/{id}/work-history", resultId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(1)))
				.andExpect(jsonPath("$.data[0].workOperationId").value(operationId));
	}

	@Test
	void rejectsDeletingOrchidGroupReferencedByWorkEffect() throws Exception {
		OrchidGroup source = createSource(40, "0", "2");
		mockMvc.perform(post("/api/work-operations/repot")
				.contentType(MediaType.APPLICATION_JSON)
				.content(repotRequest("repot-delete-conflict", source.getId(), 40, 0, null, 40, "2", "4", "")))
				.andExpect(status().isCreated());

		Long resultId = orchidGroupRepository.findAll().stream()
				.map(OrchidGroup::getId)
				.filter(id -> !id.equals(source.getId()))
				.findFirst()
				.orElseThrow();

		mockMvc.perform(delete("/api/orchid-groups/{orchidGroupId}", resultId))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("CONFLICT"))
				.andExpect(jsonPath("$.error.message").value("작업 이력과 연결된 난 묶음은 삭제할 수 없습니다. 작업 취소, 보정 또는 폐기 작업으로 처리해주세요."));
	}

	@Test
	void fullyRepotsIntoTheSamePlacementOnlyOnce() throws Exception {
		OrchidGroup source = createSource(50, "0", "2");
		String request = repotRequest("repot-full", source.getId(), 50, 0, null, 50, "0", "2", "");

		mockMvc.perform(post("/api/work-operations/repot")
				.contentType(MediaType.APPLICATION_JSON).content(request))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.sourceOrchidGroup.quantity").value(0))
				.andExpect(jsonPath("$.data.sourceOrchidGroup.status").value("종료"))
				.andExpect(jsonPath("$.data.resultOrchidGroups[0].status").value("정상"));
		mockMvc.perform(post("/api/work-operations/repot")
				.contentType(MediaType.APPLICATION_JSON).content(request))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.resultOrchidGroups", hasSize(1)));

		assertThat(orchidGroupRepository.count()).isEqualTo(2);
		assertThat(operationRepository.count()).isEqualTo(1);
		assertThat(lineageRepository.count()).isEqualTo(1);
	}

	@Test
	void rollsBackWhenInputQuantityDoesNotBalance() throws Exception {
		OrchidGroup source = createSource(100, "0", "2");

		mockMvc.perform(post("/api/work-operations/repot")
				.contentType(MediaType.APPLICATION_JSON)
				.content(repotRequest("repot-invalid", source.getId(), 40, 2, "손실", 37, "2", "4", "")))
				.andExpect(status().isBadRequest());

		assertThat(orchidGroupRepository.findById(source.getId()).orElseThrow().getQuantity()).isEqualTo(100);
		assertThat(operationRepository.count()).isZero();
		assertThat(lineageRepository.count()).isZero();
	}

	private OrchidGroup createSource(int quantity, String start, String end) {
		OrchidGroup group = new OrchidGroup(
				bedZone, variety.getGenus(), variety.getName(), quantity, "3.5치", 2, "정상", 1,
				new BigDecimal(start), new BigDecimal(end));
		group.assignVariety(variety);
		return orchidGroupRepository.save(group);
	}

	private String repotRequest(
			String key,
			Long sourceId,
			int inputQuantity,
			int lossQuantity,
			String lossReason,
			int resultQuantity,
			String start,
			String end,
			String extraFields) {
		String lossReasonField = lossReason == null ? "" : ", \"lossReason\": \"" + lossReason + "\"";
		return """
				{
				  "idempotencyKey": "%s",
				  "title": "분갈이 실행",
				  "workDate": "2026-07-15",
				  "worker": "테스터",
				  "sourceOrchidGroupId": %d,
				  "inputQuantity": %d,
				  "lossQuantity": %d%s,
				  "results": [{
				    "bedZoneId": %d,
				    "quantity": %d,
				    "potSize": "4치",
				    "ageYear": 3,
				    "startPosition": %s,
				    "endPosition": %s
				  }]%s
				}
				""".formatted(
				key, sourceId, inputQuantity, lossQuantity, lossReasonField,
				bedZone.getId(), resultQuantity, start, end, extraFields);
	}
}
