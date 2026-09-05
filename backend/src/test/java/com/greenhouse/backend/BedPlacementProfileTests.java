package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.greenhouse.backend.farm.application.structure.BedPlacementProfileService;
import com.greenhouse.backend.farm.domain.structure.PlacementCapacityMode;
import com.greenhouse.backend.farm.dto.structure.BedZoneCapacityRequest;
import com.greenhouse.backend.farm.dto.structure.BedZonePlacementProfileRequest;
import com.greenhouse.backend.farm.support.FarmTestFixtures;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BedPlacementProfileTests {

	@Autowired MockMvc mockMvc;
	@Autowired EntityManager entityManager;
	@Autowired BedPlacementProfileService profileService;
	private Long zoneId;

	@BeforeEach
	void createFixture() {
		zoneId = new FarmTestFixtures(entityManager).layout(981).left().getId();
	}

	@Test
	void returnsPlacementProfile() throws Exception {
		mockMvc.perform(get("/api/bed-zones/{bedZoneId}/placement-profile", zoneId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.bedZoneId").value(zoneId))
				.andExpect(jsonPath("$.data.capacities", hasSize(0)));
	}

	@Test
	void savesIncreasingCapacityModes() {
		var request = new BedZonePlacementProfileRequest(List.of(
				capacity(PlacementCapacityMode.SPACIOUS, 3),
				capacity(PlacementCapacityMode.STANDARD, 4),
				capacity(PlacementCapacityMode.EXPANDED, 5)));

		var updated = profileService.updateProfile(zoneId, request);

		assertThat(updated.capacities()).hasSize(3);
		assertThat(updated.capacities().get(1).capacityValue()).isEqualTo(4);
	}

	@Test
	void rejectedReplacementPreservesExistingRules() {
		profileService.updateProfile(zoneId, new BedZonePlacementProfileRequest(List.of(
				capacity(PlacementCapacityMode.STANDARD, 5))));
		entityManager.flush();
		entityManager.clear();

		assertThatThrownBy(() -> profileService.updateProfile(zoneId, new BedZonePlacementProfileRequest(List.of(
				capacity(PlacementCapacityMode.SPACIOUS, 8),
				capacity(PlacementCapacityMode.STANDARD, 5)))))
				.isInstanceOf(IllegalArgumentException.class);

		assertThat(profileService.getProfile(zoneId).capacities())
				.singleElement().satisfies(capacity -> {
					assertThat(capacity.capacityMode()).isEqualTo(PlacementCapacityMode.STANDARD);
					assertThat(capacity.capacityValue()).isEqualTo(5);
				});
	}

	@Test
	void rejectsCapacityThatDecreasesInStrongerMode() {
		var request = new BedZonePlacementProfileRequest(List.of(
				capacity(PlacementCapacityMode.STANDARD, 5),
				capacity(PlacementCapacityMode.EXPANDED, 4)));

		assertThatThrownBy(() -> profileService.updateProfile(zoneId, request))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("작을 수 없습니다");
	}

	private BedZoneCapacityRequest capacity(PlacementCapacityMode mode, int value) {
		return new BedZoneCapacityRequest(
				"TRAY_20",
				null,
				mode,
				value,
				BigDecimal.valueOf(6),
				true,
				null);
	}
}
