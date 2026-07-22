package com.greenhouse.backend;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.greenhouse.backend.farm.application.OrchidGroupLineageService;
import com.greenhouse.backend.farm.domain.BedZone;
import com.greenhouse.backend.farm.domain.BedZoneSide;
import com.greenhouse.backend.farm.domain.House;
import com.greenhouse.backend.farm.domain.OrchidGroupLineageRelationType;
import com.greenhouse.backend.farm.domain.PhysicalBed;
import com.greenhouse.backend.farm.domain.Variety;
import com.greenhouse.backend.farm.repository.OrchidGroupLineageRepository;
import com.greenhouse.backend.work.domain.operation.WorkType;
import com.greenhouse.backend.work.domain.operation.WorkTypeTemplate;
import com.greenhouse.backend.work.repository.WorkAppliedEffectRepository;
import com.greenhouse.backend.work.repository.WorkEffectOrchidGroupRepository;
import com.greenhouse.backend.work.repository.WorkOperationRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class OrchidGroupLineageIntegrationTests extends AbstractBackendIntegrationTest {

	@Autowired private OrchidGroupLineageRepository lineageRepository;
	@Autowired private OrchidGroupLineageService lineageService;
	@Autowired private WorkEffectOrchidGroupRepository effectOrchidGroupRepository;
	@Autowired private WorkAppliedEffectRepository appliedEffectRepository;
	@Autowired private WorkOperationRepository workOperationRepository;

	private BedZone bedZone;
	private Variety variety;

	@BeforeEach
	void setUp() {
		lineageRepository.deleteAll();
		effectOrchidGroupRepository.deleteAll();
		appliedEffectRepository.deleteAll();
		workOperationRepository.deleteAll();
		orchidGroupRepository.deleteAll();
		varietyRepository.deleteAll();
		bedZoneRepository.deleteAll();
		physicalBedRepository.deleteAll();
		houseRepository.deleteAll();
		workTypeRepository.deleteAll();

		workTypeRepository.save(new WorkType(
				WorkType.MULTI_CREATE_CODE, "난 묶음 다중 생성", WorkTypeTemplate.MULTI_CREATE,
				true, true, true, 1));
		House house = new House(1, "1동");
		PhysicalBed bed = new PhysicalBed(1, 1);
		bed.updatePositionUnits(new BigDecimal("24"), "칸");
		bedZone = new BedZone("좌측", BedZoneSide.LEFT, 1);
		bed.addBedZone(bedZone);
		house.addPhysicalBed(bed);
		houseRepository.save(house);
		variety = varietyRepository.save(new Variety(
				"LINEAGE-001", "팔레놉시스", "계보 테스트", null, "3.5치", true, true, null, null));
	}

	@Test
	void findsSourcesAndResultsFromEitherOrchidGroup() throws Exception {
		mockMvc.perform(post("/api/work-operations/multi-create")
				.contentType(MediaType.APPLICATION_JSON)
				.content(multiCreateRequest()))
				.andExpect(status().isCreated());

		var operation = workOperationRepository.findByRequestKey("lineage-test").orElseThrow();
		var groups = orchidGroupRepository.findAll();
		var source = groups.get(0);
		var result = groups.get(1);
		lineageService.record(
				source, result, OrchidGroupLineageRelationType.REPOTTED_TO, operation, 30, 28);

		mockMvc.perform(get("/api/orchid-groups/{id}/lineage", source.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.orchidGroupId").value(source.getId()))
				.andExpect(jsonPath("$.data.sources", hasSize(0)))
				.andExpect(jsonPath("$.data.results", hasSize(1)))
				.andExpect(jsonPath("$.data.results[0].relationType").value("REPOTTED_TO"))
				.andExpect(jsonPath("$.data.results[0].workOperationId").value(operation.getId()))
				.andExpect(jsonPath("$.data.results[0].sourceQuantity").value(30))
				.andExpect(jsonPath("$.data.results[0].resultQuantity").value(28))
				.andExpect(jsonPath("$.data.results[0].resultOrchidGroup.id").value(result.getId()));

		mockMvc.perform(get("/api/orchid-groups/{id}/lineage", result.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.sources", hasSize(1)))
				.andExpect(jsonPath("$.data.sources[0].sourceOrchidGroup.id").value(source.getId()))
				.andExpect(jsonPath("$.data.results", hasSize(0)));
	}

	@Test
	void rejectsUnknownOrchidGroup() throws Exception {
		mockMvc.perform(get("/api/orchid-groups/{id}/lineage", 999999))
				.andExpect(status().isNotFound());
	}

	private String multiCreateRequest() {
		return """
				{
				  "idempotencyKey": "lineage-test",
				  "title": "계보 기반 생성",
				  "workDate": "2026-07-15",
				  "rows": [
				    {"orchidGroup": {
				      "bedZoneId": %d, "varietyId": %d, "quantity": 30,
				      "potSize": "3.5치", "ageYear": 2, "status": "정상",
				      "startPosition": 0, "endPosition": 2
				    }},
				    {"orchidGroup": {
				      "bedZoneId": %d, "varietyId": %d, "quantity": 28,
				      "potSize": "4치", "ageYear": 3, "status": "정상",
				      "startPosition": 2, "endPosition": 4
				    }}
				  ]
				}
				""".formatted(bedZone.getId(), variety.getId(), bedZone.getId(), variety.getId());
	}
}
