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
import com.greenhouse.backend.farm.domain.InboundRecord;
import com.greenhouse.backend.farm.domain.InboundStatus;
import com.greenhouse.backend.farm.domain.InboundType;
import com.greenhouse.backend.farm.domain.PhysicalBed;
import com.greenhouse.backend.farm.domain.Variety;
import com.greenhouse.backend.work.domain.WorkType;
import com.greenhouse.backend.work.domain.WorkTypeTemplate;
import com.greenhouse.backend.work.repository.WorkAppliedEffectRepository;
import com.greenhouse.backend.work.repository.WorkEffectOrchidGroupRepository;
import com.greenhouse.backend.work.repository.WorkOperationRepository;
import com.greenhouse.backend.work.repository.WorkOperationTargetRepository;
import com.greenhouse.backend.work.repository.WorkTargetExecutionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class InboundPottingPlanIntegrationTests extends AbstractBackendIntegrationTest {

	@Autowired private WorkEffectOrchidGroupRepository effectOrchidGroupRepository;
	@Autowired private WorkAppliedEffectRepository appliedEffectRepository;
	@Autowired private WorkTargetExecutionRepository targetExecutionRepository;
	@Autowired private WorkOperationTargetRepository operationTargetRepository;
	@Autowired private WorkOperationRepository operationRepository;

	private BedZone bedZone;
	private WorkType pottingType;
	private InboundRecord inboundRecord;

	@BeforeEach
	void setUp() {
		effectOrchidGroupRepository.deleteAll();
		appliedEffectRepository.deleteAll();
		targetExecutionRepository.deleteAll();
		operationTargetRepository.deleteAll();
		operationRepository.deleteAll();
		workRecordRepository.deleteAll();
		inboundRecordRepository.deleteAll();
		orchidGroupRepository.deleteAll();
		varietyRepository.deleteAll();
		bedZoneRepository.deleteAll();
		physicalBedRepository.deleteAll();
		houseRepository.deleteAll();
		workTypeRepository.deleteAll();

		pottingType = workTypeRepository.save(new WorkType(
				WorkType.POTTING_CODE, "포트 작업", WorkTypeTemplate.REPOT, true, false, true, 1));
		House house = new House(1, "1동");
		PhysicalBed bed = new PhysicalBed(1, 1);
		bed.updatePositionUnits(new BigDecimal("24"), "칸");
		bedZone = new BedZone("우측", BedZoneSide.RIGHT, 1);
		bed.addBedZone(bedZone);
		house.addPhysicalBed(bed);
		houseRepository.save(house);

		Variety variety = varietyRepository.save(new Variety(
				"POT-001", "팔레놉시스", "포트 계획 난", null, "2치", true, true, null, null));
		inboundRecord = inboundRecordRepository.save(new InboundRecord(
				LocalDate.of(2026, 7, 1),
				InboundType.FLASK_SEEDLING,
				variety,
				InboundStatus.POTTING_PENDING,
				10,
				120,
				null,
				"배양실 A",
				LocalDate.of(2026, 7, 16),
				"2치",
				1,
				null,
				null,
				null,
				null,
				"입고 담당",
				null));
	}

	@Test
	void plansInboundPottingAndCreatesTheOrchidGroupWhenCompleted() throws Exception {
		mockMvc.perform(get("/api/work-operations/inbound-potting-candidates"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(1)))
				.andExpect(jsonPath("$.data[0].id").value(inboundRecord.getId()));

		var created = mockMvc.perform(post("/api/work-operations/inbound-potting-plans")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "title": "7월 포트 작업",
						  "plannedStartDate": "2026-07-16",
						  "plannedEndDate": "2026-07-18",
						  "inboundRecordIds": [%d]
						}
						""".formatted(inboundRecord.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.workTypeId").value(pottingType.getId()))
				.andExpect(jsonPath("$.data.sourceScopeType").value("INBOUND_RECORD_SELECTION"))
				.andExpect(jsonPath("$.data.targets[0].targetReferenceType").value("INBOUND_RECORD"))
				.andExpect(jsonPath("$.data.targets[0].inboundRecordId").value(inboundRecord.getId()))
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
						  "worker": "포트 담당",
						  "resultDetails": {
						    "pottingDate": "2026-07-16",
						    "results": [{
						      "quantity": 100,
						      "potSize": "2치",
						      "ageYear": 1,
						      "bedZoneId": %d,
						      "startPosition": 0,
						      "endPosition": 4
						    }],
						    "worker": "포트 담당"
						  }
						}
						""".formatted(bedZone.getId())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.targets[0].executionStatus").value("COMPLETED"))
				.andExpect(jsonPath("$.data.targets[0].resultDetails.createdOrchidGroupIds", hasSize(1)));

		InboundRecord updated = inboundRecordRepository.findWithDetailsById(inboundRecord.getId()).orElseThrow();
		assertThat(updated.getCreatedOrchidGroup()).isNotNull();
		assertThat(updated.getStatus()).isEqualTo(InboundStatus.PLACED);
		assertThat(workRecordRepository.count()).isZero();
	}

	@Test
	void executesImmediateInboundPottingWithMultipleResultGroups() throws Exception {
		mockMvc.perform(post("/api/work-operations/inbound-potting-executions")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "inboundRecordId": %d,
						  "pottingDate": "2026-07-16",
						  "results": [
						    {
						      "quantity": 60,
						      "potSize": "2치",
						      "ageYear": 1,
						      "bedZoneId": %d,
						      "startPosition": 0,
						      "endPosition": 4
						    },
						    {
						      "quantity": 40,
						      "potSize": "2치",
						      "ageYear": 1,
						      "bedZoneId": %d,
						      "startPosition": 4,
						      "endPosition": 8
						    }
						  ],
						  "worker": "입고 담당",
						  "memo": "입고 화면 즉시 실행"
						}
						""".formatted(inboundRecord.getId(), bedZone.getId(), bedZone.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.status").value("COMPLETED"))
				.andExpect(jsonPath("$.data.workTypeCode").value("POTTING"))
				.andExpect(jsonPath("$.data.targets[0].inboundRecordId").value(inboundRecord.getId()))
				.andExpect(jsonPath("$.data.targets[0].resultDetails.actualQuantity").value(100))
				.andExpect(jsonPath("$.data.targets[0].resultDetails.createdOrchidGroupIds", hasSize(2)));

		InboundRecord updated = inboundRecordRepository.findWithDetailsById(inboundRecord.getId()).orElseThrow();
		assertThat(updated.getStatus()).isEqualTo(InboundStatus.PLACED);
		assertThat(updated.getActualQuantity()).isEqualTo(100);
		assertThat(updated.getCreatedOrchidGroup()).isNotNull();
		assertThat(orchidGroupRepository.findAll()).hasSize(2);
		mockMvc.perform(get("/api/inbound-records/{id}", inboundRecord.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.createdOrchidGroupIds", hasSize(2)));

		var operations = operationRepository.findAll();
		assertThat(operations).hasSize(1);
		assertThat(operations.getFirst().getWorkType().getCode()).isEqualTo(WorkType.POTTING_CODE);
		assertThat(operations.getFirst().getStatus().name()).isEqualTo("COMPLETED");
		var targets = operationTargetRepository
				.findByWorkOperationIdAndExcludedAtIsNullOrderByIdAsc(operations.getFirst().getId());
		assertThat(targets).hasSize(1);
		assertThat(targets.getFirst().getInboundRecordId()).isEqualTo(inboundRecord.getId());
		assertThat(targetExecutionRepository
				.findByTargetWorkOperationIdOrderByIdAsc(operations.getFirst().getId()).getFirst().getStatus().name())
				.isEqualTo("COMPLETED");
		assertThat(appliedEffectRepository.count()).isEqualTo(1);
		assertThat(effectOrchidGroupRepository.count()).isEqualTo(2);
		assertThat(workRecordRepository.count()).isZero();
	}

	@Test
	void immediateExecutionReusesTheExistingPottingPlan() throws Exception {
		var planned = mockMvc.perform(post("/api/work-operations/inbound-potting-plans")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "title": "기존 포트 작업 계획",
						  "plannedStartDate": "2026-07-16",
						  "inboundRecordIds": [%d]
						}
						""".formatted(inboundRecord.getId())))
				.andExpect(status().isCreated())
				.andReturn();
		Long operationId = Long.valueOf(planned.getResponse().getContentAsString().replaceAll(
				".*?\\\"data\\\":\\{\\\"id\\\":(\\d+).*", "$1"));

		mockMvc.perform(post("/api/work-operations/inbound-potting-plans")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "title": "중복 포트 작업 계획",
						  "plannedStartDate": "2026-07-16",
						  "inboundRecordIds": [%d]
						}
						""".formatted(inboundRecord.getId())))
				.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/work-operations/inbound-potting-executions")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "inboundRecordId": %d,
						  "pottingDate": "2026-07-16",
						  "results": [{
						    "quantity": 100,
						    "potSize": "2치",
						    "ageYear": 1,
						    "bedZoneId": %d,
						    "startPosition": 0,
						    "endPosition": 4
						  }],
						  "worker": "입고 담당"
						}
						""".formatted(inboundRecord.getId(), bedZone.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.id").value(operationId))
				.andExpect(jsonPath("$.data.status").value("COMPLETED"))
				.andExpect(jsonPath("$.data.targets[0].executionStatus").value("COMPLETED"));

		assertThat(operationRepository.count()).isEqualTo(1);
		assertThat(workRecordRepository.count()).isZero();
	}
}
