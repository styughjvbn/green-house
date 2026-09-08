package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.greenhouse.backend.farm.application.orchid.OrchidGroupCommandService;
import com.greenhouse.backend.farm.application.orchid.OrchidGroupMovementService;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupMoveRequest;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupUpdateRequest;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.structure.BedZoneRepository;
import com.greenhouse.backend.farm.support.FarmTestFixtures;
import com.greenhouse.backend.work.domain.operation.WorkType;
import com.greenhouse.backend.work.domain.operation.WorkTypeDefinition;
import com.greenhouse.backend.work.domain.operation.WorkTypeTemplate;
import com.greenhouse.backend.work.repository.WorkOperationRepository;
import com.greenhouse.backend.work.repository.WorkTypeRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BedPlacementTests {

	@Autowired
	BedZoneRepository bedZoneRepository;

	@Autowired
	OrchidGroupRepository orchidGroupRepository;

	@Autowired
	WorkOperationRepository workOperationRepository;

	@Autowired
	OrchidGroupCommandService commandService;

	@Autowired
	OrchidGroupMovementService movementService;

	@Autowired
	EntityManager entityManager;

	@Autowired
	WorkTypeRepository workTypeRepository;

	private Long groupId;

	private Long targetZoneId;

	@BeforeEach
	void setUp() {
		var fixtures = new FarmTestFixtures(entityManager);
		var layout = fixtures.layout(982);
		var group = fixtures.orchidGroup(layout.left(), "PLACEMENT-TEST", 20);
		if (workTypeRepository.findByCode(WorkTypeDefinition.MOVEMENT.name()).isEmpty()) {
			workTypeRepository.save(new WorkType(WorkTypeDefinition.MOVEMENT.name(), "자리 이동", WorkTypeTemplate.MOVEMENT,
					true, true, true, 1));
		}
		groupId = group.getId();
		commandService.update(groupId,
				new OrchidGroupUpdateRequest(group.getVariety().getId(), group.getQuantity(), group.getPotSize(),
						group.getAgeYear(), group.getStatus(), group.getPlacementType(), group.getTrayCount(),
						group.getSplitPlacementAllowed(), group.getStartPosition(), group.getEndPosition(),
						group.getMemo()));
		targetZoneId = layout.right().getId();
	}

	@Test
	void movesWithDirectPositionRange() {
		var moved = movementService.move(groupId,
				new OrchidGroupMoveRequest(targetZoneId, BigDecimal.ZERO, BigDecimal.valueOf(8), null, "직접 배치"));

		assertThat(moved.bedZoneId()).isEqualTo(targetZoneId);
		assertThat(moved.startPosition()).isEqualTo(BigDecimal.ZERO.setScale(2));
		assertThat(moved.endPosition()).isEqualTo(BigDecimal.valueOf(8).setScale(2));
	}

	@Test
	void rejectsInvalidPositionRange() {
		assertThatThrownBy(() -> movementService.move(groupId,
				new OrchidGroupMoveRequest(targetZoneId, BigDecimal.valueOf(10), BigDecimal.valueOf(8), null, null)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void createsMovementOperationWhenZoneChanges() {
		long before = workOperationRepository.count();

		movementService.move(groupId,
				new OrchidGroupMoveRequest(targetZoneId, BigDecimal.ZERO, BigDecimal.valueOf(8), "관리자", "이동"));

		assertThat(workOperationRepository.count()).isEqualTo(before + 1);
	}

}
