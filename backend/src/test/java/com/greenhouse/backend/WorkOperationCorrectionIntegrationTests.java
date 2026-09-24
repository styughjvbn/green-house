package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.greenhouse.backend.farm.application.orchid.OrchidGroupCommandService;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.structure.BedZone;
import com.greenhouse.backend.farm.domain.structure.BedZoneSide;
import com.greenhouse.backend.farm.domain.structure.House;
import com.greenhouse.backend.farm.domain.structure.PhysicalBed;
import com.greenhouse.backend.farm.domain.variety.Variety;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupCreateRequest;
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
		workTypeRepository.save(new WorkType(WorkTypeDefinition.CORRECTION.name(), "구조 변경 보정",
				WorkTypeTemplate.CORRECTION, true, true, true, 2));
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
			.andExpect(jsonPath("$.data.originalOperation.status").value("CORRECTED"))
			.andExpect(jsonPath("$.data.corrections", hasSize(1)))
			.andExpect(jsonPath("$.data.corrections[0].reason").value("결과 수량 확인 필요"))
			.andExpect(jsonPath("$.data.corrections[0].correctionOperation.status").value("COMPLETED"))
			.andExpect(jsonPath("$.data.corrections[0].effectDetails.adjustments[0].beforeQuantity").value(30))
			.andExpect(jsonPath("$.data.corrections[0].effectDetails.adjustments[0].afterQuantity").value(25));
		mockMvc
			.perform(post("/api/work-operations/{id}/corrections", originalId).contentType(MediaType.APPLICATION_JSON)
				.content(correctionRequest("correction-1")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.corrections", hasSize(1)));

		mockMvc.perform(get("/api/work-operations/{id}/corrections", originalId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.corrections", hasSize(1)));

		assertThat(correctionRepository.count()).isEqualTo(1);
		assertThat(operationRepository.count()).isEqualTo(2);
		assertThat(appliedEffectRepository.count()).isEqualTo(2);
		assertThat(operationRepository.findWithWorkTypeById(originalId).orElseThrow().getStatus())
			.isEqualTo(WorkOperationStatus.CORRECTED);
		var correctedGroup = orchidGroupRepository.findById(createdGroupId).orElseThrow();
		assertThat(correctedGroup.getQuantity()).isEqualTo(25);
		assertThat(correctedGroup.getStatus()).isEqualTo("수량 보정");
		var correctionEffect = appliedEffectRepository.findAll()
			.stream()
			.filter(effect -> WorkTypeDefinition.CORRECTION.name().equals(effect.getHandlerCode()))
			.findFirst()
			.orElseThrow();
		assertThat(correctionEffect.getResultDetails()).containsKey("adjustments");
	}

	@Test
	void rejectsCorrectionOfARecordOnlyCorrectionOperation() throws Exception {
		Long originalId = createRepotOperation();
		mockMvc
			.perform(post("/api/work-operations/{id}/corrections", originalId).contentType(MediaType.APPLICATION_JSON)
				.content(correctionRequest("correction-original")))
			.andExpect(status().isCreated());
		Long correctionOperationId = correctionRepository.findAll().getFirst().getCorrectionWorkOperation().getId();

		mockMvc
			.perform(post("/api/work-operations/{id}/corrections", correctionOperationId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(correctionRequest("correction-invalid")))
			.andExpect(status().isBadRequest());
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
			.andExpect(jsonPath("$.data.corrections[0].effectDetails.beforeWorkDate").value("2026-07-15"))
			.andExpect(jsonPath("$.data.corrections[0].effectDetails.afterWorkDate").value("2026-07-14"))
			.andExpect(jsonPath("$.data.corrections[0].effectDetails.adjustments", hasSize(0)));

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
		var createdSource = orchidGroupCommandService.create(new OrchidGroupCreateRequest(bedZone.getId(),
				variety.getId(), 30, "3.5치", 2, "정상", "POT", null, false, new BigDecimal("0"),
				new BigDecimal("2"), null));
		OrchidGroup source = orchidGroupRepository.findById(createdSource.id()).orElseThrow();
		mockMvc.perform(post("/api/work-operations/structure-change-records").contentType(MediaType.APPLICATION_JSON).content("""
				{
				  "operation": {
				    "workTypeId": %d,
				    "title": "보정할 분갈이",
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
				  "title": "분갈이 결과 보정 확인",
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
				  "title": "분갈이 작업일 보정",
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

}
