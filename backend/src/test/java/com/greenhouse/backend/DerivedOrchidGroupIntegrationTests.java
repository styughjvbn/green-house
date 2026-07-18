package com.greenhouse.backend;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.greenhouse.backend.farm.domain.BedZone;
import com.greenhouse.backend.farm.domain.BedZoneSide;
import com.greenhouse.backend.farm.domain.House;
import com.greenhouse.backend.farm.domain.OrchidGroup;
import com.greenhouse.backend.farm.domain.PhysicalBed;
import com.greenhouse.backend.farm.domain.Variety;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class DerivedOrchidGroupIntegrationTests extends AbstractBackendIntegrationTest {

	@Test
	void recalculatesGroupsImmediatelyFromCurrentOrchidAttributes() throws Exception {
		House house = new House(92, "자동 그룹 테스트동");
		PhysicalBed bed = new PhysicalBed(1, 1);
		BedZone left = new BedZone("좌측", BedZoneSide.LEFT, 1);
		BedZone right = new BedZone("우측", BedZoneSide.RIGHT, 2);
		bed.addBedZone(left);
		bed.addBedZone(right);
		house.addPhysicalBed(bed);
		houseRepository.save(house);
		Variety variety = varietyRepository.save(new Variety(
				"DERIVED-TEST", "팔레놉시스", "자동 그룹 테스트 난", null, "3.5\"", true, true, null, null));

		saveGroup(left, variety, 40, "3.5\"", 2, "정상", 1);
		OrchidGroup second = saveGroup(right, variety, 30, "3.5\"", 2, "정상", 1);
		saveGroup(left, variety, 20, "4\"", 2, "정상", 2);
		saveGroup(right, variety, 99, "3.5\"", 2, "종료", 2);

		String groupKey = variety.getId() + ":2:POT_3_5";
		mockMvc.perform(get("/api/orchid-groups/derived-groups").param("varietyId", variety.getId().toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(2)))
				.andExpect(jsonPath("$.data[0].groupKey").value(groupKey))
				.andExpect(jsonPath("$.data[0].orchidGroupCount").value(2))
				.andExpect(jsonPath("$.data[0].totalQuantity").value(70))
				.andExpect(jsonPath("$.data[0].locationCount").value(2));

		mockMvc.perform(get("/api/orchid-groups/derived-groups/{groupKey}/members", groupKey))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(2)));

		second.updateDetails(
				variety.getGenus(), variety.getName(), 30, "4\"", 2, "정상", null, null, false,
				BigDecimal.ONE, BigDecimal.TEN, null);
		orchidGroupRepository.saveAndFlush(second);

		mockMvc.perform(get("/api/orchid-groups/derived-groups").param("varietyId", variety.getId().toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(2)))
				.andExpect(jsonPath("$.data[0].orchidGroupCount").value(1))
				.andExpect(jsonPath("$.data[0].totalQuantity").value(40))
				.andExpect(jsonPath("$.data[1].orchidGroupCount").value(2))
				.andExpect(jsonPath("$.data[1].totalQuantity").value(50));

		mockMvc.perform(get("/api/orchid-groups/derived-groups")
				.param("varietyId", variety.getId().toString())
				.param("potSizeCode", "POT_3_5")
				.param("ageYear", "2")
				.param("houseId", house.getId().toString())
				.param("status", "정상")
				.param("keyword", "그룹 테스트"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(1)));
	}

	private OrchidGroup saveGroup(
			BedZone zone,
			Variety variety,
			int quantity,
			String potSize,
			int ageYear,
			String status,
			int sortOrder) {
		OrchidGroup group = new OrchidGroup(
				zone, variety.getGenus(), variety.getName(), quantity, potSize, ageYear, status, sortOrder,
				BigDecimal.ONE, BigDecimal.TEN);
		group.assignVariety(variety);
		return orchidGroupRepository.save(group);
	}
}
