package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.greenhouse.backend.farm.application.OrchidGroupCommandService;
import com.greenhouse.backend.farm.dto.OrchidGroupMoveRequest;
import com.greenhouse.backend.farm.dto.OrchidGroupUpdateRequest;
import com.greenhouse.backend.farm.repository.BedZoneRepository;
import com.greenhouse.backend.farm.repository.OrchidGroupRepository;
import com.greenhouse.backend.work.repository.WorkRecordRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Disabled("Seed data is currently disabled; re-enable after deterministic test fixtures are restored.")
class BedPlacementTests {

	@Autowired BedZoneRepository bedZoneRepository;
	@Autowired OrchidGroupRepository orchidGroupRepository;
	@Autowired WorkRecordRepository workRecordRepository;
	@Autowired OrchidGroupCommandService commandService;

	private Long groupId;
	private Long targetZoneId;

	@BeforeEach
	void setUp() {
		var group = orchidGroupRepository.findAll().getFirst();
		groupId = group.getId();
		commandService.update(groupId, new OrchidGroupUpdateRequest(
				group.getVariety().getId(),
				group.getQuantity(),
				group.getPotSize(),
				group.getAgeYear(),
				group.getStatus(),
				group.getPlacementType(),
				group.getTrayCount(),
				group.getSplitPlacementAllowed(),
				group.getStartPosition(),
				group.getEndPosition(),
				group.getMemo()));
		targetZoneId = bedZoneRepository.findAll().stream()
				.filter(zone -> zone.getOrchidGroups().isEmpty())
				.findFirst()
				.orElseThrow()
				.getId();
	}

	@Test
	void movesWithDirectPositionRange() {
		var moved = commandService.move(groupId, new OrchidGroupMoveRequest(
				targetZoneId,
				BigDecimal.ZERO,
				BigDecimal.valueOf(8),
				null,
				"직접 배치"));

		assertThat(moved.bedZoneId()).isEqualTo(targetZoneId);
		assertThat(moved.startPosition()).isEqualTo(BigDecimal.ZERO.setScale(2));
		assertThat(moved.endPosition()).isEqualTo(BigDecimal.valueOf(8).setScale(2));
	}

	@Test
	void rejectsInvalidPositionRange() {
		assertThatThrownBy(() -> commandService.move(groupId, new OrchidGroupMoveRequest(
				targetZoneId,
				BigDecimal.valueOf(10),
				BigDecimal.valueOf(8),
				null,
				null)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void createsMovementRecordWhenZoneChanges() {
		long before = workRecordRepository.count();

		commandService.move(groupId, new OrchidGroupMoveRequest(
				targetZoneId,
				BigDecimal.ZERO,
				BigDecimal.valueOf(8),
				"관리자",
				"이동"));

		assertThat(workRecordRepository.count()).isEqualTo(before + 1);
	}
}
