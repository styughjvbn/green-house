package com.greenhouse.backend;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.greenhouse.backend.farm.domain.structure.BedZone;
import com.greenhouse.backend.farm.domain.structure.BedZoneSide;
import com.greenhouse.backend.farm.domain.structure.House;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.structure.PhysicalBed;
import com.greenhouse.backend.farm.domain.variety.Variety;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class OrchidGroupCollectionIntegrationTests extends AbstractBackendIntegrationTest {

	@Test
	void keepsMultipleMembershipsUntilTheyAreExplicitlyRemoved() throws Exception {
		House house = new House(91, "사용자 그룹 테스트동");
		PhysicalBed bed = new PhysicalBed(1, 1);
		BedZone zone = new BedZone("좌측", BedZoneSide.LEFT, 1);
		bed.addBedZone(zone);
		house.addPhysicalBed(bed);
		houseRepository.save(house);
		Variety variety = varietyRepository.save(new Variety(
				"COLLECTION-TEST", "팔레놉시스", "그룹 테스트 난", null, "3.5\"", true, true, null, null));
		OrchidGroup orchidGroup = new OrchidGroup(
				zone, variety.getGenus(), variety.getName(), 40, "3.5\"", 2, "정상", 1,
				BigDecimal.ONE, BigDecimal.TEN);
		orchidGroup.assignVariety(variety);
		orchidGroup = orchidGroupRepository.save(orchidGroup);

		Long firstCollectionId = createCollection("우량주 관리");
		Long secondCollectionId = createCollection("봄 출하 후보");

		addMember(firstCollectionId, orchidGroup.getId())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.orchidGroupCount").value(1))
				.andExpect(jsonPath("$.data.totalQuantity").value(40));
		addMember(firstCollectionId, orchidGroup.getId())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.members", hasSize(1)));
		addMember(secondCollectionId, orchidGroup.getId()).andExpect(status().isOk());

		orchidGroup.updateDetails(
				variety.getGenus(), variety.getName(), 35, "4\"", 3, "주의", null, null, false,
				BigDecimal.ONE, BigDecimal.TEN, null);
		orchidGroupRepository.save(orchidGroup);

		mockMvc.perform(get("/api/orchid-groups/{id}/collections", orchidGroup.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(2)))
				.andExpect(jsonPath("$.data[0].members[0].quantity").value(35));

		mockMvc.perform(post("/api/orchid-group-collections/{id}/archive", firstCollectionId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("ARCHIVED"));
		mockMvc.perform(get("/api/orchid-group-collections"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(1)))
				.andExpect(jsonPath("$.data[0].id").value(secondCollectionId));

		mockMvc.perform(delete("/api/orchid-group-collections/{collectionId}/members/{orchidGroupId}",
				secondCollectionId, orchidGroup.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.orchidGroupCount").value(0));
		mockMvc.perform(get("/api/orchid-groups/{id}/collections", orchidGroup.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(1)))
				.andExpect(jsonPath("$.data[0].status").value("ARCHIVED"));
	}

	private Long createCollection(String name) throws Exception {
		var result = mockMvc.perform(post("/api/orchid-group-collections")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"%s","purpose":"테스트","createdBy":"테스터"}
						""".formatted(name)))
				.andExpect(status().isCreated())
				.andReturn();
		return Long.valueOf(result.getResponse().getContentAsString().replaceAll(".*\\\"id\\\":(\\d+).*", "$1"));
	}

	private org.springframework.test.web.servlet.ResultActions addMember(Long collectionId, Long orchidGroupId)
			throws Exception {
		return mockMvc.perform(post("/api/orchid-group-collections/{id}/members", collectionId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"orchidGroupIds":[%d],"createdBy":"테스터"}
						""".formatted(orchidGroupId)));
	}
}
