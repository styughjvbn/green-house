package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.greenhouse.backend.audit.application.AuditRecorder;
import com.greenhouse.backend.farm.application.orchid.OrchidGroupCommandService;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.structure.BedZone;
import com.greenhouse.backend.farm.domain.structure.BedZoneSide;
import com.greenhouse.backend.farm.domain.structure.House;
import com.greenhouse.backend.farm.domain.structure.PhysicalBed;
import com.greenhouse.backend.farm.domain.variety.Variety;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupUpdateRequest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class OrchidGroupAuditRollbackIntegrationTest extends AbstractBackendIntegrationTest {
	@Autowired OrchidGroupCommandService commandService;
	@MockitoBean AuditRecorder auditRecorder;

	@Test
	void auditFailureRollsBackCorrection() {
		House house = new House(9902, "롤백 테스트동");
		PhysicalBed bed = new PhysicalBed(1, 1);
		bed.updatePositionUnits(new BigDecimal("20"), "칸");
		BedZone zone = new BedZone("왼쪽", BedZoneSide.LEFT, 1);
		bed.addBedZone(zone); house.addPhysicalBed(bed); houseRepository.saveAndFlush(house);
		Variety variety = varietyRepository.saveAndFlush(new Variety(
				"ROLLBACK-" + System.nanoTime(), "Phal", "롤백품종", null, "4인치", true, true, null, null));
		OrchidGroup group = new OrchidGroup(zone, variety.getGenus(), variety.getName(), 10, "4인치", 2,
				"정상", 1, BigDecimal.ONE, BigDecimal.TWO);
		group.assignVariety(variety);
		Long groupId = orchidGroupRepository.saveAndFlush(group).getId();
		doThrow(new IllegalStateException("audit unavailable")).when(auditRecorder).record(any());

		var request = new OrchidGroupUpdateRequest(variety.getId(), 15, "4인치", 2, "정상",
				null, null, false, BigDecimal.ONE, BigDecimal.TWO, null);
		assertThatThrownBy(() -> commandService.update(groupId, request)).isInstanceOf(IllegalStateException.class);

		assertThat(orchidGroupRepository.findById(groupId).orElseThrow().getQuantity()).isEqualTo(10);
	}
}
