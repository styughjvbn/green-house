package com.greenhouse.backend;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.greenhouse.backend.farm.domain.BedZone;
import com.greenhouse.backend.farm.domain.BedZoneSide;
import com.greenhouse.backend.farm.domain.House;
import com.greenhouse.backend.farm.domain.OrchidGroupCollection;
import com.greenhouse.backend.farm.domain.PhysicalBed;
import com.greenhouse.backend.farm.domain.Variety;
import com.greenhouse.backend.farm.repository.OrchidGroupCollectionMemberRepository;
import com.greenhouse.backend.farm.repository.OrchidGroupCollectionRepository;
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
class MultiCreateWorkOperationIntegrationTests extends AbstractBackendIntegrationTest {

	@Autowired private WorkOperationRepository workOperationRepository;
	@Autowired private WorkOperationTargetRepository workOperationTargetRepository;
	@Autowired private WorkTargetExecutionRepository workTargetExecutionRepository;
	@Autowired private WorkAppliedEffectRepository workAppliedEffectRepository;
	@Autowired private WorkEffectOrchidGroupRepository workEffectOrchidGroupRepository;
	@Autowired private OrchidGroupCollectionRepository collectionRepository;
	@Autowired private OrchidGroupCollectionMemberRepository memberRepository;

	private BedZone bedZone;
	private Variety variety;
	private OrchidGroupCollection collection;

	@BeforeEach
	void setUp() {
		workEffectOrchidGroupRepository.deleteAll();
		workAppliedEffectRepository.deleteAll();
		workTargetExecutionRepository.deleteAll();
		workOperationTargetRepository.deleteAll();
		workOperationRepository.deleteAll();
		memberRepository.deleteAll();
		collectionRepository.deleteAll();
		orchidGroupRepository.deleteAll();
		varietyRepository.deleteAll();
		bedZoneRepository.deleteAll();
		physicalBedRepository.deleteAll();
		houseRepository.deleteAll();
		workTypeRepository.deleteAll();

		workTypeRepository.save(new WorkType(
				WorkType.MULTI_CREATE_CODE, "난 묶음 다중 생성", WorkTypeTemplate.MULTI_CREATE,
				true, true, true, 1));
		House house = new House(3, "3동");
		PhysicalBed bed = new PhysicalBed(1, 1);
		bed.updatePositionUnits(new BigDecimal("24"), "칸");
		bedZone = new BedZone("좌측", BedZoneSide.LEFT, 1);
		bed.addBedZone(bedZone);
		house.addPhysicalBed(bed);
		houseRepository.save(house);

		variety = varietyRepository.save(new Variety(
				"TEST-001", "팔레놉시스", "테스트 난", null, "3.5치", true, true, null, null));
		collection = collectionRepository.save(new OrchidGroupCollection("봄 출하", null, null, "테스터"));
	}

	@Test
	void createsMultipleGroupsOnceAndLinksTheCauseOperation() throws Exception {
		String request = """
				{
				  "idempotencyKey": "multi-create-test-1",
				  "title": "초기 난 묶음 등록",
				  "workDate": "2026-07-15",
				  "worker": "테스터",
				  "rows": [
				    {
				      "orchidGroup": {
				        "bedZoneId": %d, "varietyId": %d, "quantity": 30,
				        "potSize": "3.5치", "ageYear": 2, "status": "정상",
				        "placementType": "단일", "startPosition": 0, "endPosition": 2
				      },
				      "collectionIds": [%d]
				    },
				    {
				      "orchidGroup": {
				        "bedZoneId": %d, "varietyId": %d, "quantity": 40,
				        "potSize": "4치", "ageYear": 3, "status": "정상",
				        "placementType": "단일", "startPosition": 2, "endPosition": 4
				      }
				    }
				  ]
				}
				""".formatted(
				bedZone.getId(), variety.getId(), collection.getId(), bedZone.getId(), variety.getId());

		var first = mockMvc.perform(post("/api/work-operations/multi-create")
				.contentType(MediaType.APPLICATION_JSON)
				.content(request))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.operation.status").value("COMPLETED"))
				.andExpect(jsonPath("$.data.operation.sourceScopeType").value("NONE"))
				.andExpect(jsonPath("$.data.createdOrchidGroups", hasSize(2)))
				.andExpect(jsonPath("$.data.createdOrchidGroups[0].potSizeCode").value("POT_3_5"))
				.andReturn();

		Long operationId = workOperationRepository.findByRequestKey("multi-create-test-1").orElseThrow().getId();
		mockMvc.perform(post("/api/work-operations/multi-create")
				.contentType(MediaType.APPLICATION_JSON)
				.content(request))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.operation.id").value(operationId))
				.andExpect(jsonPath("$.data.createdOrchidGroups", hasSize(2)));

		org.assertj.core.api.Assertions.assertThat(orchidGroupRepository.count()).isEqualTo(2);
		org.assertj.core.api.Assertions.assertThat(workAppliedEffectRepository.count()).isEqualTo(1);
		org.assertj.core.api.Assertions.assertThat(workEffectOrchidGroupRepository.count()).isEqualTo(2);
		org.assertj.core.api.Assertions.assertThat(memberRepository.count()).isEqualTo(1);
	}
}
