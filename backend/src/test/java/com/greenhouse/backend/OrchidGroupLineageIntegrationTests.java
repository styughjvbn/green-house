package com.greenhouse.backend;

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
import com.greenhouse.backend.farm.repository.transformation.OrchidGroupLineageRepository;
import com.greenhouse.backend.work.domain.operation.WorkType;
import com.greenhouse.backend.work.domain.operation.WorkTypeDefinition;
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

  @Autowired private WorkEffectOrchidGroupRepository effectOrchidGroupRepository;

  @Autowired private WorkAppliedEffectRepository appliedEffectRepository;

  @Autowired private WorkOperationRepository workOperationRepository;

  @Autowired private OrchidGroupCommandService orchidGroupCommandService;

  private BedZone bedZone;

  private Variety variety;

  private WorkType repotType;

  @BeforeEach
  void setUp() {
    lineageRepository.deleteAll();
    effectOrchidGroupRepository.deleteAll();
    appliedEffectRepository.deleteAll();
    workCommandReceiptRepository.deleteAll();
    workOperationRepository.deleteAll();
    orchidGroupRepository.deleteAll();
    varietyRepository.deleteAll();
    bedZoneRepository.deleteAll();
    physicalBedRepository.deleteAll();
    houseRepository.deleteAll();
    workTypeRepository.deleteAll();

    repotType =
        workTypeRepository.save(
            new WorkType(
                WorkTypeDefinition.REPOT.name(),
                "분갈이",
                WorkTypeTemplate.REPOT,
                true,
                true,
                true,
                1));
    House house = new House(1, "1동");
    PhysicalBed bed = new PhysicalBed(1, 1);
    bed.updatePositionUnits(new BigDecimal("24"), "칸");
    bedZone = new BedZone("좌측", BedZoneSide.LEFT, 1);
    bed.addBedZone(bedZone);
    house.addPhysicalBed(bed);
    houseRepository.save(house);
    variety =
        varietyRepository.save(
            new Variety("LINEAGE-001", "팔레놉시스", "계보 테스트", null, "3.5치", true, true, null, null));
  }

  @Test
  void findsSourcesAndResultsFromEitherOrchidGroup() throws Exception {
    var createdSource =
        orchidGroupCommandService.create(
            new OrchidGroupCreateRequest(
                bedZone.getId(),
                variety.getId(),
                30,
                "3.5치",
                2,
                "정상",
                "POT",
                null,
                false,
                new BigDecimal("0"),
                new BigDecimal("2"),
                null));
    OrchidGroup source = orchidGroupRepository.findById(createdSource.id()).orElseThrow();
    mockMvc
        .perform(
            post("/api/work-operations/structure-change-records")
                .contentType(MediaType.APPLICATION_JSON)
                .content(structureChangeRequest(source.getId())))
        .andExpect(status().isCreated());

    var operation = workOperationRepository.findAll().getFirst();
    var result =
        orchidGroupRepository.findAll().stream()
            .filter(group -> !group.getId().equals(source.getId()))
            .findFirst()
            .orElseThrow();

    mockMvc
        .perform(get("/api/orchid-groups/{id}/lineage", source.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.orchidGroupId").value(source.getId()))
        .andExpect(jsonPath("$.data.sources", hasSize(0)))
        .andExpect(jsonPath("$.data.results", hasSize(0)))
        .andExpect(jsonPath("$.data.transformations", hasSize(1)))
        .andExpect(jsonPath("$.data.transformations[0].relationType").value("REPOTTED_TO"))
        .andExpect(jsonPath("$.data.transformations[0].workOperationId").value(operation.getId()))
        .andExpect(jsonPath("$.data.transformations[0].totalInputQuantity").value(30))
        .andExpect(jsonPath("$.data.transformations[0].totalResultQuantity").value(28))
        .andExpect(
            jsonPath("$.data.transformations[0].sources[0].orchidGroup.id").value(source.getId()))
        .andExpect(
            jsonPath("$.data.transformations[0].results[0].orchidGroup.id").value(result.getId()));

    mockMvc
        .perform(get("/api/orchid-groups/{id}/lineage", result.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.sources", hasSize(0)))
        .andExpect(jsonPath("$.data.results", hasSize(0)))
        .andExpect(jsonPath("$.data.transformations", hasSize(1)));
  }

  @Test
  void rejectsUnknownOrchidGroup() throws Exception {
    mockMvc
        .perform(get("/api/orchid-groups/{id}/lineage", 999999))
        .andExpect(status().isNotFound());
  }

  private String structureChangeRequest(Long sourceId) {
    return """
				{
				  "operation": {
				    "workTypeId": %d,
				    "title": "계보 기반 분갈이",
				    "plannedStartDate": "2026-07-15",
				    "sourceScopeType": "MANUAL_SELECTION",
				    "sourceOrchidGroupIds": [%d]
				  },
				  "execution": {
				    "idempotencyKey": "lineage-test",
				    "completedDate": "2026-07-15",
				    "sources": [{"sourceOrchidGroupId": %d, "inputQuantity": 30}],
				    "lossQuantity": 2,
				    "results": [{
				      "bedZoneId": %d, "quantity": 28, "potSize": "4치", "ageYear": 3,
				      "purpose": "NORMAL", "startPosition": 0, "endPosition": 2
				    }]
				  }
				}
				"""
        .formatted(repotType.getId(), sourceId, sourceId, bedZone.getId());
  }
}
