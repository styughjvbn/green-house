package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.greenhouse.backend.audit.domain.AuditAction;
import com.greenhouse.backend.audit.domain.AuditSource;
import com.greenhouse.backend.audit.repository.AuditEventRepository;
import com.greenhouse.backend.farm.domain.structure.BedZone;
import com.greenhouse.backend.farm.domain.structure.BedZoneSide;
import com.greenhouse.backend.farm.domain.structure.House;
import com.greenhouse.backend.farm.domain.structure.PhysicalBed;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class BedPlacementAuditIntegrationTest extends AbstractBackendIntegrationTest {
	@Autowired AuditEventRepository auditEventRepository;

	@Test
	void recordsChangedProfileAndSkipsIdenticalProfile() throws Exception {
		House house = new House(9910, "배치 감사동");
		PhysicalBed bed = new PhysicalBed(1, 1);
		BedZone zone = new BedZone("왼쪽", BedZoneSide.LEFT, 1);
		bed.addBedZone(zone);
		house.addPhysicalBed(bed);
		houseRepository.saveAndFlush(house);
		String body = """
				{"capacities":[
				 {"placementType":"TRAY_20","potSize":"4인치","capacityMode":"STANDARD",
				  "capacityValue":4,"unitSpan":6,"allowed":true,"memo":"기준"}
				]}
				""";

		for (int index = 0; index < 2; index++) {
			mockMvc.perform(put("/api/bed-zones/{id}/placement-profile", zone.getId())
					.with(user("operator"))
					.header("X-Request-Id", "bed-profile-" + index)
					.contentType(MediaType.APPLICATION_JSON)
					.content(body))
					.andExpect(status().isOk());
		}

		var events = auditEventRepository.findAll().stream()
				.filter(event -> event.getSource() == AuditSource.FARM_STRUCTURE_MANAGEMENT)
				.filter(event -> event.getEntityId().equals(zone.getId()))
				.toList();
		assertThat(events).hasSize(1);
		assertThat(events.getFirst().getAction()).isEqualTo(AuditAction.UPDATED);
		assertThat(events.getFirst().getEntityType()).isEqualTo("BED_ZONE");
		assertThat(events.getFirst().getHouseId()).isEqualTo(house.getId());
		assertThat(events.getFirst().getPhysicalBedId()).isEqualTo(bed.getId());
		assertThat(events.getFirst().getZoneId()).isEqualTo(zone.getId());
		assertThat(events.getFirst().getChangedFields()).containsExactly("capacities");
	}
}
