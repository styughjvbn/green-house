package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.greenhouse.backend.farm.application.orchid.OrchidGroupCommandService;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationRelationType;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationType;
import com.greenhouse.backend.farm.domain.structure.BedZone;
import com.greenhouse.backend.farm.domain.structure.BedZoneSide;
import com.greenhouse.backend.farm.domain.structure.House;
import com.greenhouse.backend.farm.domain.structure.PhysicalBed;
import com.greenhouse.backend.farm.domain.variety.Variety;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupCreateRequest;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationRelationRepository;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationRepository;
import com.greenhouse.backend.work.domain.operation.WorkOperationStatus;
import com.greenhouse.backend.work.domain.operation.WorkType;
import com.greenhouse.backend.work.domain.operation.WorkTypeDefinition;
import com.greenhouse.backend.work.domain.operation.WorkTypeTemplate;
import com.greenhouse.backend.work.repository.WorkAppliedEffectRepository;
import com.greenhouse.backend.work.repository.WorkEffectOrchidGroupRepository;
import com.greenhouse.backend.work.repository.WorkOperationCorrectionRepository;
import com.greenhouse.backend.work.repository.WorkOperationRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class WorkOperationCorrectionIntegrationTests extends AbstractBackendIntegrationTest {

	@Autowired
	private WorkOperationCorrectionRepository correctionRepository;

	@Autowired
	private WorkEffectOrchidGroupRepository effectOrchidGroupRepository;

	@Autowired
	private WorkAppliedEffectRepository appliedEffectRepository;

	@Autowired
	private WorkOperationRepository operationRepository;

	@Autowired
	private OrchidGroupCommandService orchidGroupCommandService;

	@Autowired
	private OrchidGroupMutationRepository mutationRepository;

	@Autowired
	private OrchidGroupMutationRelationRepository mutationRelationRepository;

	private BedZone bedZone;

	private Variety variety;

	private Long createdGroupId;

	private WorkType pesticideType;

	private WorkType repotType;

	@BeforeEach
	void setUp() {
		correctionRepository.deleteAll();
		effectOrchidGroupRepository.deleteAll();
		appliedEffectRepository.deleteAll();
		workCommandReceiptRepository.deleteAll();
		operationRepository.deleteAll();
		orchidGroupRepository.deleteAll();
		varietyRepository.deleteAll();
		bedZoneRepository.deleteAll();
		physicalBedRepository.deleteAll();
		houseRepository.deleteAll();
		workTypeRepository.deleteAll();

		repotType = workTypeRepository
			.save(new WorkType(WorkTypeDefinition.REPOT.name(), "분갈이", WorkTypeTemplate.REPOT, true, true, true, 1));
		pesticideType = workTypeRepository
			.save(new WorkType("PESTICIDE", "농약", WorkTypeTemplate.PESTICIDE, true, false, true, 3));
		House house = new House(1, "1동");
		PhysicalBed bed = new PhysicalBed(1, 1);
		bed.updatePositionUnits(new BigDecimal("24"), "칸");
		bedZone = new BedZone("좌측", BedZoneSide.LEFT, 1);
		bed.addBedZone(bedZone);
		house.addPhysicalBed(bed);
		houseRepository.save(house);
		variety = varietyRepository
			.save(new Variety("CORRECTION-001", "팔레놉시스", "보정 테스트", null, "3.5치", true, true, null, null));
	}

	@Test
	void adjustsAnOriginalResultOnceAndPreservesItsAuditHistory() throws Exception {
		Long originalId = createRepotOperation();

		mockMvc
			.perform(post("/api/work-operations/{id}/corrections", originalId).contentType(MediaType.APPLICATION_JSON)
				.content(correctionRequest("correction-1")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.originalOperation.status").value("COMPLETED"))
			.andExpect(jsonPath("$.data.corrections", hasSize(1)))
			.andExpect(jsonPath("$.data.corrections[0].reason").value("결과 수량 확인 필요"))
			.andExpect(jsonPath("$.data.corrections[0].adjustments[0].beforeQuantity").value(30))
			.andExpect(jsonPath("$.data.corrections[0].adjustments[0].afterQuantity").value(25));
		mockMvc
			.perform(post("/api/work-operations/{id}/corrections", originalId).contentType(MediaType.APPLICATION_JSON)
				.content(correctionRequest("correction-1")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.corrections", hasSize(1)));

		mockMvc.perform(get("/api/work-operations/{id}/corrections", originalId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.corrections", hasSize(1)));

		assertThat(correctionRepository.count()).isEqualTo(1);
		assertThat(operationRepository.count()).isEqualTo(1);
		assertThat(appliedEffectRepository.count()).isEqualTo(1);
		assertThat(operationRepository.findWithWorkTypeById(originalId).orElseThrow().getStatus())
			.isEqualTo(WorkOperationStatus.COMPLETED);
		var correctedGroup = orchidGroupRepository.findById(createdGroupId).orElseThrow();
		assertThat(correctedGroup.getQuantity()).isEqualTo(25);
		assertThat(correctedGroup.getStatus()).isEqualTo("수량 보정");
		var correctionEffect = correctionRepository.findAll().getFirst();
		assertThat(correctionEffect.getResultDetails()).containsKey("adjustments");
		mockMvc.perform(get("/api/orchid-groups/{id}/work-history", createdGroupId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[?(@.workOperationId == %d)].correctable".formatted(originalId))
				.value(org.hamcrest.Matchers.hasItem(true)));
	}

	@Test
	void rejectsReusingAKeyWithDifferentContents() throws Exception {
		Long originalId = createRepotOperation();
		mockMvc
			.perform(post("/api/work-operations/{id}/corrections", originalId).contentType(MediaType.APPLICATION_JSON)
				.content(correctionRequest("same-key")))
			.andExpect(status().isCreated());
		mockMvc
			.perform(post("/api/work-operations/{id}/corrections", originalId).contentType(MediaType.APPLICATION_JSON)
				.content(correctionRequest("same-key").replace("25", "24")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REUSED"));
		assertThat(correctionRepository.count()).isEqualTo(1);
	}

	@Test
	void cancelsAnIncorrectlyCreatedResultWithoutRecordingADiscard() throws Exception {
		Long originalId = createRepotOperation();
		Long originalMutationId = appliedEffectRepository.findAll().getFirst().getMutationId();

		mockMvc
			.perform(post("/api/work-operations/{id}/corrections", originalId).contentType(MediaType.APPLICATION_JSON)
				.content(resultCancellationRequest()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.originalOperation.status").value("COMPLETED"))
			.andExpect(jsonPath("$.data.corrections[0].adjustments[0].beforeQuantity").value(30))
			.andExpect(jsonPath("$.data.corrections[0].adjustments[0].afterQuantity").value(0))
			.andExpect(jsonPath("$.data.corrections[0].adjustments[0].afterStatus").value("생성 취소"));

		OrchidGroup canceled = orchidGroupRepository.findById(createdGroupId).orElseThrow();
		assertThat(canceled.getQuantity()).isZero();
		assertThat(canceled.getStatus()).isEqualTo("생성 취소");
		var correctionEffect = correctionRepository.findAll().getFirst();
		var cancellationMutation = mutationRepository.findById(correctionEffect.getMutationId()).orElseThrow();
		assertThat(cancellationMutation.getMutationType()).isEqualTo(OrchidGroupMutationType.CANCEL_CREATION);
		assertThat(mutationRelationRepository.findByMutationIdOrderByIdAsc(cancellationMutation.getId()))
			.singleElement()
			.satisfies(relation -> {
				assertThat(relation.getRelationType()).isEqualTo(OrchidGroupMutationRelationType.CORRECTS);
				assertThat(relation.getRelatedMutation().getId()).isEqualTo(originalMutationId);
			});
	}

	@Test
	void correctsOnlyTheOriginalWorkDateAndPreservesItsAuditHistory() throws Exception {
		Long originalId = createRepotOperation();

		mockMvc
			.perform(post("/api/work-operations/{id}/corrections", originalId).contentType(MediaType.APPLICATION_JSON)
				.content(dateOnlyCorrectionRequest()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.originalOperation.plannedStartDate").value("2026-07-14"))
			.andExpect(jsonPath("$.data.originalOperation.plannedEndDate").value("2026-07-14"))
			.andExpect(jsonPath("$.data.corrections[0].beforeWorkDate").value("2026-07-15"))
			.andExpect(jsonPath("$.data.corrections[0].afterWorkDate").value("2026-07-14"))
			.andExpect(jsonPath("$.data.corrections[0].adjustments", hasSize(0)));

		var original = operationRepository.findWithWorkTypeById(originalId).orElseThrow();
		assertThat(original.getPlannedStartDate()).isEqualTo(java.time.LocalDate.of(2026, 7, 14));
		assertThat(original.getPlannedEndDate()).isEqualTo(java.time.LocalDate.of(2026, 7, 14));
		var unchangedGroup = orchidGroupRepository.findById(createdGroupId).orElseThrow();
		assertThat(unchangedGroup.getQuantity()).isEqualTo(30);
		assertThat(unchangedGroup.getStatus()).isEqualTo("정상");
	}

	@Test
	void rejectsAdjustmentWhenAResultHasDownstreamWork() throws Exception {
		Long originalId = createRepotOperation();
		mockMvc.perform(post("/api/work-operations").contentType(MediaType.APPLICATION_JSON).content("""
				{
				  "workTypeId": %d,
				  "title": "후속 농약 작업",
				  "plannedStartDate": "2026-07-16",
				  "sourceScopeType": "MANUAL_SELECTION",
				  "sourceOrchidGroupIds": [%d]
				}
				""".formatted(pesticideType.getId(), createdGroupId))).andExpect(status().isCreated());

		mockMvc
			.perform(post("/api/work-operations/{id}/corrections", originalId).contentType(MediaType.APPLICATION_JSON)
				.content(correctionRequest("correction-blocked")))
			.andExpect(status().isBadRequest());

		assertThat(orchidGroupRepository.findById(createdGroupId).orElseThrow().getQuantity()).isEqualTo(30);
		assertThat(correctionRepository.count()).isZero();
	}

	private Long createRepotOperation() throws Exception {
		var createdSource = orchidGroupCommandService
			.create(new OrchidGroupCreateRequest(bedZone.getId(), variety.getId(), 30, "3.5치", 2, "정상", "POT", null,
					false, new BigDecimal("0"), new BigDecimal("2"), null));
		OrchidGroup source = orchidGroupRepository.findById(createdSource.id()).orElseThrow();
		mockMvc
			.perform(post("/api/work-operations/structure-change-records").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "operation": {
						          "title": "보정할 분갈이",
						    "workTypeId": %d,
						    "plannedStartDate": "2026-07-15",
						    "plannedEndDate": "2026-07-15",
						    "sourceScopeType": "MANUAL_SELECTION",
						    "sourceOrchidGroupIds": [%d]
						  },
						  "execution": {
						    "idempotencyKey": "correction-source",
						    "completedDate": "2026-07-15",
						    "sources": [{"sourceOrchidGroupId": %d, "inputQuantity": 30}],
						    "lossQuantity": 0,
						    "results": [{
						      "bedZoneId": %d, "quantity": 30, "potSize": "4치", "ageYear": 2,
						      "purpose": "NORMAL", "startPosition": 0, "endPosition": 2
						    }]
						  }
						}
						""".formatted(repotType.getId(), source.getId(), source.getId(), bedZone.getId())))
			.andExpect(status().isCreated());
		createdGroupId = orchidGroupRepository.findAll()
			.stream()
			.filter(group -> !group.getId().equals(source.getId()))
			.findFirst()
			.orElseThrow()
			.getId();
		return operationRepository.findAll().getFirst().getId();
	}

	private String correctionRequest(String idempotencyKey) {
		return """
				{
				  "idempotencyKey": "%s",
				  "workDate": "2026-07-15",
				  "worker": "관리자",
				  "reason": "결과 수량 확인 필요",
				  "orchidGroupAdjustments": [{
				    "orchidGroupId": %d,
				    "quantity": 25,
				    "status": "수량 보정"
				  }]
				}
				""".formatted(idempotencyKey, createdGroupId);
	}

	private String dateOnlyCorrectionRequest() {
		return """
				{
				  "idempotencyKey": "correction-date-only",
				  "workDate": "2026-07-14",
				  "worker": "관리자",
				  "reason": "작업일 입력 오류",
				  "orchidGroupAdjustments": [{
				    "orchidGroupId": %d,
				    "quantity": 30,
				    "status": "정상"
				  }]
				}
				""".formatted(createdGroupId);
	}

	private String resultCancellationRequest() {
		return """
				{
				  "idempotencyKey": "correction-cancel-result",
				  "title": "잘못 생성된 분갈이 결과 취소",
				  "workDate": "2026-07-15",
				  "worker": "관리자",
				  "reason": "실제로 만들지 않은 결과",
				  "cancelResultCreation": true,
				  "orchidGroupAdjustments": [{
				    "orchidGroupId": %d,
				    "quantity": 30,
				    "status": "정상"
				  }]
				}
				""".formatted(createdGroupId);
	}

}
