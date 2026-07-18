package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.greenhouse.backend.farm.domain.BedZone;
import com.greenhouse.backend.farm.domain.BedZoneSide;
import com.greenhouse.backend.farm.domain.House;
import com.greenhouse.backend.farm.domain.PhysicalBed;
import com.greenhouse.backend.farm.domain.Variety;
import com.greenhouse.backend.work.domain.WorkOperationStatus;
import com.greenhouse.backend.work.domain.WorkType;
import com.greenhouse.backend.work.domain.WorkTypeTemplate;
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

	@Autowired private WorkOperationCorrectionRepository correctionRepository;
	@Autowired private WorkEffectOrchidGroupRepository effectOrchidGroupRepository;
	@Autowired private WorkAppliedEffectRepository appliedEffectRepository;
	@Autowired private WorkOperationRepository operationRepository;

	private BedZone bedZone;
	private Variety variety;
	private Long createdGroupId;
	private WorkType pesticideType;

	@BeforeEach
	void setUp() {
		correctionRepository.deleteAll();
		effectOrchidGroupRepository.deleteAll();
		appliedEffectRepository.deleteAll();
		operationRepository.deleteAll();
		orchidGroupRepository.deleteAll();
		varietyRepository.deleteAll();
		bedZoneRepository.deleteAll();
		physicalBedRepository.deleteAll();
		houseRepository.deleteAll();
		workTypeRepository.deleteAll();

		workTypeRepository.save(new WorkType(
				WorkType.MULTI_CREATE_CODE, "난 묶음 다중 생성", WorkTypeTemplate.MULTI_CREATE,
				true, true, true, 1));
		workTypeRepository.save(new WorkType(
				WorkType.CORRECTION_CODE, "구조 변경 보정", WorkTypeTemplate.CORRECTION,
				true, true, true, 2));
		pesticideType = workTypeRepository.save(new WorkType(
				"PESTICIDE", "농약", WorkTypeTemplate.PESTICIDE, true, false, true, 3));
		House house = new House(1, "1동");
		PhysicalBed bed = new PhysicalBed(1, 1);
		bed.updatePositionUnits(new BigDecimal("24"), "칸");
		bedZone = new BedZone("좌측", BedZoneSide.LEFT, 1);
		bed.addBedZone(bedZone);
		house.addPhysicalBed(bed);
		houseRepository.save(house);
		variety = varietyRepository.save(new Variety(
				"CORRECTION-001", "팔레놉시스", "보정 테스트", null, "3.5치", true, true, null, null));
	}

	@Test
	void adjustsAnOriginalResultOnceAndPreservesItsAuditHistory() throws Exception {
		Long originalId = createMultiCreateOperation();

		mockMvc.perform(post("/api/work-operations/{id}/corrections", originalId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(correctionRequest("correction-1")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.originalOperation.status").value("CORRECTED"))
				.andExpect(jsonPath("$.data.corrections", hasSize(1)))
				.andExpect(jsonPath("$.data.corrections[0].reason").value("결과 수량 확인 필요"))
				.andExpect(jsonPath("$.data.corrections[0].correctionOperation.status").value("COMPLETED"))
				.andExpect(jsonPath("$.data.corrections[0].effectDetails.adjustments[0].beforeQuantity").value(30))
				.andExpect(jsonPath("$.data.corrections[0].effectDetails.adjustments[0].afterQuantity").value(25));
		mockMvc.perform(post("/api/work-operations/{id}/corrections", originalId)
				.contentType(MediaType.APPLICATION_JSON)
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
		var correctionEffect = appliedEffectRepository.findAll().stream()
				.filter(effect -> WorkType.CORRECTION_CODE.equals(effect.getHandlerCode()))
				.findFirst().orElseThrow();
		assertThat(correctionEffect.getResultDetails()).containsKey("adjustments");
	}

	@Test
	void rejectsCorrectionOfARecordOnlyCorrectionOperation() throws Exception {
		Long originalId = createMultiCreateOperation();
		mockMvc.perform(post("/api/work-operations/{id}/corrections", originalId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(correctionRequest("correction-original")))
				.andExpect(status().isCreated());
		Long correctionOperationId = correctionRepository.findAll().getFirst()
				.getCorrectionWorkOperation().getId();

		mockMvc.perform(post("/api/work-operations/{id}/corrections", correctionOperationId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(correctionRequest("correction-invalid")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void rejectsAdjustmentWhenAResultHasDownstreamWork() throws Exception {
		Long originalId = createMultiCreateOperation();
		mockMvc.perform(post("/api/work-operations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "workTypeId": %d,
						  "title": "후속 농약 작업",
						  "plannedStartDate": "2026-07-16",
						  "sourceScopeType": "MANUAL_SELECTION",
						  "sourceOrchidGroupIds": [%d]
						}
						""".formatted(pesticideType.getId(), createdGroupId)))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/work-operations/{id}/corrections", originalId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(correctionRequest("correction-blocked")))
				.andExpect(status().isBadRequest());

		assertThat(orchidGroupRepository.findById(createdGroupId).orElseThrow().getQuantity()).isEqualTo(30);
		assertThat(correctionRepository.count()).isZero();
	}

	private Long createMultiCreateOperation() throws Exception {
		mockMvc.perform(post("/api/work-operations/multi-create")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "idempotencyKey": "correction-source",
						  "title": "보정할 다중 생성",
						  "workDate": "2026-07-15",
						  "rows": [{"orchidGroup": {
						    "bedZoneId": %d, "varietyId": %d, "quantity": 30,
						    "potSize": "3.5치", "ageYear": 2, "status": "정상",
						    "startPosition": 0, "endPosition": 2
						  }}]
						}
						""".formatted(bedZone.getId(), variety.getId())))
				.andExpect(status().isCreated());
		createdGroupId = orchidGroupRepository.findAll().getFirst().getId();
		return operationRepository.findByRequestKey("correction-source").orElseThrow().getId();
	}

	private String correctionRequest(String idempotencyKey) {
		return """
				{
				  "idempotencyKey": "%s",
				  "title": "다중 생성 결과 보정 확인",
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
}
