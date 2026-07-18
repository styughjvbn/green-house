package com.greenhouse.backend;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
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
import com.greenhouse.backend.farm.domain.PhysicalBed;
import com.greenhouse.backend.farm.domain.Variety;
import com.greenhouse.backend.farm.repository.OrchidGroupCollectionMemberRepository;
import com.greenhouse.backend.farm.repository.OrchidGroupCollectionRepository;
import com.greenhouse.backend.work.domain.WorkType;
import com.greenhouse.backend.work.domain.WorkTypeTemplate;
import com.greenhouse.backend.work.repository.WorkOperationTargetRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class WorkOperationScopeIntegrationTests extends AbstractBackendIntegrationTest {

	@Autowired
	private OrchidGroupCollectionRepository collectionRepository;
	@Autowired
	private OrchidGroupCollectionMemberRepository collectionMemberRepository;
	@Autowired
	private WorkOperationTargetRepository workOperationTargetRepository;

	@Test
	void resolvesDerivedCollectionAndManualScopesThenPreservesSnapshot() throws Exception {
		WorkType pesticide = workTypeRepository.findByCode("PESTICIDE")
				.orElseGet(() -> workTypeRepository.save(new WorkType(
						"PESTICIDE", "농약", WorkTypeTemplate.PESTICIDE, true, false, true, 1)));
		House house = new House(93, "작업 범위 테스트동");
		PhysicalBed bed = new PhysicalBed(1, 1);
		BedZone zone = new BedZone("좌측", BedZoneSide.LEFT, 1);
		bed.addBedZone(zone);
		house.addPhysicalBed(bed);
		houseRepository.save(house);
		Variety variety = varietyRepository.save(new Variety(
				"WORK-SCOPE-TEST", "팔레놉시스", "작업 범위 테스트 난", null, "3.5\"", true, true, null, null));
		OrchidGroup first = saveGroup(zone, variety, 40, "3.5\"", 1);
		OrchidGroup second = saveGroup(zone, variety, 30, "3.5\"", 2);
		OrchidGroup third = saveGroup(zone, variety, 20, "4\"", 3);

		OrchidGroupCollection collection = collectionRepository.save(
				new OrchidGroupCollection("작업 후보", null, "농약", "테스터"));
		collectionMemberRepository.save(new OrchidGroupCollectionMember(collection.getId(), first.getId(), "테스터"));
		collectionMemberRepository.save(new OrchidGroupCollectionMember(collection.getId(), third.getId(), "테스터"));

		String derivedKey = variety.getId() + ":2:POT_3_5";
		preview("""
				{"scopeType":"DERIVED_GROUP","scopeKey":"%s"}
				""".formatted(derivedKey))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.orchidGroupCount").value(2))
				.andExpect(jsonPath("$.data.totalQuantity").value(70));
		preview("""
				{"scopeType":"USER_COLLECTION","scopeId":%d}
				""".formatted(collection.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.orchidGroupCount").value(2))
				.andExpect(jsonPath("$.data.totalQuantity").value(60));
		preview("""
				{"scopeType":"MANUAL_SELECTION","orchidGroupIds":[%d,%d]}
				""".formatted(second.getId(), third.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.orchidGroupCount").value(2))
				.andExpect(jsonPath("$.data.totalQuantity").value(50));

		var result = mockMvc.perform(post("/api/work-operations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "workTypeId": %d,
						  "title": "자동 그룹 농약 작업",
						  "plannedStartDate": "2026-07-15",
						  "sourceScopeType": "DERIVED_GROUP",
						  "sourceScopeKey": "%s"
						}
						""".formatted(pesticide.getId(), derivedKey)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.sourceScopeType").value("DERIVED_GROUP"))
				.andExpect(jsonPath("$.data.sourceScopeId").doesNotExist())
				.andExpect(jsonPath("$.data.sourceConditionSnapshot.groupKey").value(derivedKey))
				.andExpect(jsonPath("$.data.targets", hasSize(2)))
				.andExpect(jsonPath("$.data.targets[*].inclusionSource", everyItem(is("DERIVED_GROUP"))))
				.andReturn();
		Long operationId = Long.valueOf(result.getResponse().getContentAsString()
				.replaceAll(".*?\\\"data\\\":\\{\\\"id\\\":(\\d+).*", "$1"));

		second.updateDetails(
				variety.getGenus(), variety.getName(), 30, "4\"", 2, "정상", null, null, false,
				BigDecimal.ONE, BigDecimal.TEN, null);
		orchidGroupRepository.saveAndFlush(second);

		preview("""
				{"scopeType":"DERIVED_GROUP","scopeKey":"%s"}
				""".formatted(derivedKey))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.orchidGroupCount").value(1));
		mockMvc.perform(get("/api/work-operations/{id}", operationId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.targets", hasSize(2)));

		mockMvc.perform(post("/api/work-operations/{id}/complete", operationId))
				.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/work-operations/{id}/start", operationId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
		mockMvc.perform(post("/api/work-operations/{id}/pause", operationId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("PAUSED"));

		var targets = workOperationTargetRepository
				.findByWorkOperationIdAndExcludedAtIsNullOrderByIdAsc(operationId);
		mockMvc.perform(post("/api/work-operations/{id}/targets/{targetId}/start", operationId, targets.get(0).getId()))
				.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/work-operations/{id}/resume", operationId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
		mockMvc.perform(post("/api/work-operations/{id}/targets/{targetId}/start", operationId, targets.get(0).getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"worker\":\"첫 작업자\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.progress.inProgress").value(1));
		mockMvc.perform(post("/api/work-operations/{id}/targets/{targetId}/complete", operationId, targets.get(0).getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"worker\":\"첫 작업자\",\"resultDetails\":{\"memo\":\"완료\"}}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.targets[0].worker").value("첫 작업자"))
				.andExpect(jsonPath("$.data.targets[0].resultDetails.memo").value("완료"));
		mockMvc.perform(post("/api/work-operations/{id}/targets/{targetId}/skip", operationId, targets.get(1).getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"worker\":\"둘째 작업자\",\"resultDetails\":{\"reason\":\"대상 제외\"}}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.progress.completed").value(1))
				.andExpect(jsonPath("$.data.progress.skipped").value(1))
				.andExpect(jsonPath("$.data.progress.progressPercent").value(100));
		mockMvc.perform(post("/api/work-operations/{id}/complete", operationId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("COMPLETED"));
	}

	private org.springframework.test.web.servlet.ResultActions preview(String content) throws Exception {
		return mockMvc.perform(post("/api/work-operations/target-preview")
				.contentType(MediaType.APPLICATION_JSON)
				.content(content));
	}

	private OrchidGroup saveGroup(BedZone zone, Variety variety, int quantity, String potSize, int sortOrder) {
		OrchidGroup group = new OrchidGroup(
				zone, variety.getGenus(), variety.getName(), quantity, potSize, 2, "정상", sortOrder,
				BigDecimal.ONE, BigDecimal.TEN);
		group.assignVariety(variety);
		return orchidGroupRepository.save(group);
	}
}
