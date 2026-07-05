package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.greenhouse.backend.common.exception.CapacityConflictException;
import com.greenhouse.backend.farm.application.BedPlacementProfileService;
import com.greenhouse.backend.farm.application.OrchidGroupCommandService;
import com.greenhouse.backend.farm.application.PlacementRecommendationService;
import com.greenhouse.backend.farm.domain.PlacementCapacityMode;
import com.greenhouse.backend.farm.domain.PlacementRecommendationStatus;
import com.greenhouse.backend.farm.dto.BedZoneCapacityRequest;
import com.greenhouse.backend.farm.dto.BedZonePlacementProfileRequest;
import com.greenhouse.backend.farm.dto.BedZoneSegmentRequest;
import com.greenhouse.backend.farm.dto.OrchidGroupMovePlacementRequest;
import com.greenhouse.backend.farm.dto.OrchidGroupMoveRequest;
import com.greenhouse.backend.farm.dto.OrchidGroupUpdateRequest;
import com.greenhouse.backend.farm.repository.BedZoneRepository;
import com.greenhouse.backend.farm.repository.OrchidGroupRepository;
import com.greenhouse.backend.work.repository.WorkRecordRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PlacementRecommendationTests {

	@Autowired BedZoneRepository bedZoneRepository;
	@Autowired OrchidGroupRepository orchidGroupRepository;
	@Autowired WorkRecordRepository workRecordRepository;
	@Autowired BedPlacementProfileService profileService;
	@Autowired PlacementRecommendationService recommendationService;
	@Autowired OrchidGroupCommandService commandService;

	private Long groupId;
	private Long targetZoneId;

	@BeforeEach
	void setUp() {
		var group = orchidGroupRepository.findAll().getFirst();
		groupId = group.getId();
		commandService.update(groupId, new OrchidGroupUpdateRequest(
			group.getGenus(), group.getVarietyName(), group.getQuantity(), group.getPotSize(), group.getAgeYear(),
			group.getStatus(), "TRAY_20", 8, true, group.getMemo()
		));
		targetZoneId = bedZoneRepository.findAll().stream()
			.filter(zone -> zone.getOrchidGroups().isEmpty())
			.findFirst().orElseThrow().getId();
		configureTarget(4);
	}

	@Test
	void recommendsSplitAllocationAtStandardMode() {
		var response = recommendationService.recommend(groupId, null);
		var candidate = response.candidates().stream().filter(item -> item.bedZoneId().equals(targetZoneId)).findFirst().orElseThrow();

		assertThat(candidate.status()).isEqualTo(PlacementRecommendationStatus.RECOMMENDED);
		assertThat(candidate.requiredMode()).isEqualTo(PlacementCapacityMode.STANDARD);
		assertThat(candidate.allocations()).hasSize(2);
		assertThat(candidate.allocations()).extracting(item -> item.occupancyUnits()).containsExactly(4, 4);
		assertThat(candidate.allocations()).extracting(item -> item.quantity()).containsExactly(60, 60);
	}

	@Test
	void rejectsMoveThatExceedsSegmentCapacity() {
		Long segmentId = profileService.getProfile(targetZoneId).segments().getFirst().id();

		assertThatThrownBy(() -> commandService.move(groupId, new OrchidGroupMoveRequest(
			targetZoneId, PlacementCapacityMode.STANDARD,
			List.of(new OrchidGroupMovePlacementRequest(segmentId, 120, 8)), null, null, null
		))).isInstanceOf(CapacityConflictException.class);
	}

	@Test
	void temporaryPlacementRequiresReorganizeDate() {
		Long first = profileService.getProfile(targetZoneId).segments().getFirst().id();
		Long second = profileService.getProfile(targetZoneId).segments().get(1).id();

		assertThatThrownBy(() -> commandService.move(groupId, new OrchidGroupMoveRequest(
			targetZoneId, PlacementCapacityMode.TEMPORARY,
			List.of(new OrchidGroupMovePlacementRequest(first, 60, 4), new OrchidGroupMovePlacementRequest(second, 60, 4)),
			null, null, null
		))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("재정리 예정일");
	}

	@Test
	void confirmsPreciseMoveAndCreatesMovementRecord() {
		var candidate = recommendationService.recommend(groupId, null).candidates().stream()
			.filter(item -> item.bedZoneId().equals(targetZoneId)).findFirst().orElseThrow();
		long before = workRecordRepository.count();
		var requests = candidate.allocations().stream()
			.map(item -> new OrchidGroupMovePlacementRequest(item.segmentId(), item.quantity(), item.occupancyUnits()))
			.toList();

		var moved = commandService.move(groupId, new OrchidGroupMoveRequest(
			targetZoneId, candidate.requiredMode(), requests, null, "관리자", "추천 배치"
		));

		assertThat(moved.bedZoneId()).isEqualTo(targetZoneId);
		assertThat(moved.segmentPlacements()).hasSize(2);
		assertThat(workRecordRepository.count()).isEqualTo(before + 1);
	}

	private void configureTarget(int capacity) {
		var profile = profileService.getProfile(targetZoneId);
		var segments = profile.segments().stream().map(segment -> new BedZoneSegmentRequest(
			segment.id(), segment.name(), segment.segmentType(), segment.sortOrder(), segment.memo(),
			segment.sortOrder() <= 2 ? List.of(
				new BedZoneCapacityRequest("TRAY_20", null, PlacementCapacityMode.STANDARD, capacity, true, null),
				new BedZoneCapacityRequest("TRAY_20", null, PlacementCapacityMode.TEMPORARY, capacity, true, null)
			) : List.of()
		)).toList();
		profileService.updateProfile(targetZoneId, new BedZonePlacementProfileRequest(segments));
	}
}
