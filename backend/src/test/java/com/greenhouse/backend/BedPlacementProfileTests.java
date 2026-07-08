package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.greenhouse.backend.farm.application.BedPlacementProfileService;
import com.greenhouse.backend.farm.domain.PlacementCapacityMode;
import com.greenhouse.backend.farm.dto.BedZoneCapacityRequest;
import com.greenhouse.backend.farm.dto.BedZonePlacementProfileRequest;
import com.greenhouse.backend.farm.repository.BedZoneRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Disabled;
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
@Disabled("Seed data is currently disabled; re-enable after deterministic test fixtures are restored.")
class BedPlacementProfileTests {

	@Autowired MockMvc mockMvc;
	@Autowired BedZoneRepository bedZoneRepository;
	@Autowired BedPlacementProfileService profileService;

	@Test
	void returnsPlacementProfile() throws Exception {
		Long zoneId = bedZoneRepository.findAll().getFirst().getId();

		mockMvc.perform(get("/api/bed-zones/{bedZoneId}/placement-profile", zoneId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.bedZoneId").value(zoneId))
				.andExpect(jsonPath("$.data.capacities", hasSize(0)));
	}

	@Test
	void savesIncreasingCapacityModes() {
		Long zoneId = bedZoneRepository.findAll().getFirst().getId();
		var request = new BedZonePlacementProfileRequest(List.of(
				capacity(PlacementCapacityMode.SPACIOUS, 3),
				capacity(PlacementCapacityMode.STANDARD, 4),
				capacity(PlacementCapacityMode.EXPANDED, 5)));

		var updated = profileService.updateProfile(zoneId, request);

		assertThat(updated.capacities()).hasSize(3);
		assertThat(updated.capacities().get(1).capacityValue()).isEqualTo(4);
	}

	@Test
	void rejectsCapacityThatDecreasesInStrongerMode() {
		Long zoneId = bedZoneRepository.findAll().getFirst().getId();
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
