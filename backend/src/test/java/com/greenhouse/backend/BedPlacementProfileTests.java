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
import com.greenhouse.backend.farm.dto.BedZoneSegmentRequest;
import com.greenhouse.backend.farm.repository.BedZoneRepository;
import com.greenhouse.backend.farm.repository.BedZoneSegmentRepository;
import java.math.BigDecimal;
import java.util.List;
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
	@Autowired BedZoneRepository bedZoneRepository;
	@Autowired BedZoneSegmentRepository segmentRepository;
	@Autowired BedPlacementProfileService profileService;

	@Test
	void createsFiveDefaultSegmentsForEveryBedZone() {
		assertThat(segmentRepository.count()).isEqualTo(450);
		bedZoneRepository.findAll().forEach(zone -> assertThat(zone.getSegments()).hasSize(5));
	}

	@Test
	void returnsPlacementProfileInSegmentOrder() throws Exception {
		Long zoneId = bedZoneRepository.findAll().getFirst().getId();

		mockMvc.perform(get("/api/bed-zones/{bedZoneId}/placement-profile", zoneId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.bedZoneId").value(zoneId))
			.andExpect(jsonPath("$.data.segments", hasSize(5)))
			.andExpect(jsonPath("$.data.segments[0].segmentType").value("START"))
			.andExpect(jsonPath("$.data.segments[4].segmentType").value("END"));
	}

	@Test
	void savesIncreasingCapacityModes() {
		Long zoneId = bedZoneRepository.findAll().getFirst().getId();
		var profile = profileService.getProfile(zoneId);
		var segments = profile.segments().stream().map(segment -> new BedZoneSegmentRequest(
			segment.id(), segment.name(), segment.segmentType(), segment.sortOrder(),
			segment.startPosition(), segment.endPosition(), segment.memo(),
			segment.sortOrder() == 1 ? List.of(
				capacity(PlacementCapacityMode.SPACIOUS, 3, spanOf(segment)),
				capacity(PlacementCapacityMode.STANDARD, 4, spanOf(segment)),
				capacity(PlacementCapacityMode.EXPANDED, 5, spanOf(segment))
			) : List.of()
		)).toList();

		var updated = profileService.updateProfile(zoneId, new BedZonePlacementProfileRequest(segments));

		assertThat(updated.segments().getFirst().capacities()).hasSize(3);
		assertThat(updated.segments().getFirst().capacities().get(1).capacityValue()).isEqualTo(4);
	}

	@Test
	void rejectsCapacityThatDecreasesInStrongerMode() {
		Long zoneId = bedZoneRepository.findAll().getFirst().getId();
		var profile = profileService.getProfile(zoneId);
		var first = profile.segments().getFirst();
		var request = new BedZonePlacementProfileRequest(List.of(new BedZoneSegmentRequest(
			first.id(), first.name(), first.segmentType(), 1,
			first.startPosition(), first.endPosition(), null,
			List.of(
				capacity(PlacementCapacityMode.STANDARD, 5, spanOf(first)),
				capacity(PlacementCapacityMode.EXPANDED, 4, spanOf(first))
			)
		)));

		assertThatThrownBy(() -> profileService.updateProfile(zoneId, request))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("작을 수 없습니다");
	}

	private BedZoneCapacityRequest capacity(PlacementCapacityMode mode, int value, BigDecimal unitSpan) {
		return new BedZoneCapacityRequest("TRAY_20", null, mode, value, unitSpan, true, null);
	}

	private BigDecimal spanOf(com.greenhouse.backend.farm.dto.BedZoneSegmentResponse segment) {
		return segment.endPosition().subtract(segment.startPosition());
	}
}
