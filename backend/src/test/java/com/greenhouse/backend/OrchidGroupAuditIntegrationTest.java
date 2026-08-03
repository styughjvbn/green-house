package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.greenhouse.backend.audit.domain.AuditAction;
import com.greenhouse.backend.audit.domain.AuditSource;
import com.greenhouse.backend.audit.repository.AuditEventRepository;
import com.greenhouse.backend.farm.domain.structure.BedZone;
import com.greenhouse.backend.farm.domain.structure.BedZoneSide;
import com.greenhouse.backend.farm.domain.structure.House;
import com.greenhouse.backend.farm.domain.structure.PhysicalBed;
import com.greenhouse.backend.farm.domain.variety.Variety;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;

class OrchidGroupAuditIntegrationTest extends AbstractBackendIntegrationTest {
	@Autowired AuditEventRepository auditEventRepository;
	@Autowired JdbcTemplate jdbcTemplate;
	private Long zoneId;
	private Long varietyId;

	@BeforeEach
	void setUp() {
		auditEventRepository.deleteAll();
		House house = new House(9901, "감사 테스트동");
		PhysicalBed bed = new PhysicalBed(1, 1);
		bed.updatePositionUnits(new BigDecimal("20"), "칸");
		BedZone zone = new BedZone("왼쪽", BedZoneSide.LEFT, 1);
		bed.addBedZone(zone);
		house.addPhysicalBed(bed);
		houseRepository.saveAndFlush(house);
		zoneId = zone.getId();
		varietyId = varietyRepository.saveAndFlush(new Variety(
				"AUDIT-" + System.nanoTime(), "Phal", "감사품종", null, "4인치", true, true, null, null)).getId();
	}

	@Test
	void correctionStoresDataAndRequestIdentityAndNoOpDoesNotStore() throws Exception {
		MockHttpSession session = new MockHttpSession();
		var create = mockMvc.perform(post("/api/orchid-groups")
				.with(user("operator"))
				.session(session)
				.header("X-Request-Id", "req-create")
				.header("X-Client-Instance-Id", "browser-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(payload(10)))
				.andExpect(status().isCreated())
				.andExpect(header().string("X-Request-Id", "req-create"))
				.andReturn();
		Long groupId = Long.valueOf(create.getResponse().getContentAsString().replaceAll(".*\\\"id\\\":(\\d+).*", "$1"));

		mockMvc.perform(patch("/api/orchid-groups/{id}", groupId)
				.with(user("operator"))
				.session(session)
				.header("X-Request-Id", "req-correction")
				.header("X-Client-Instance-Id", "browser-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(updatePayload(15)))
				.andExpect(status().isOk());

		var correction = auditEventRepository.findAll().stream()
				.filter(event -> event.getSource() == AuditSource.ORCHID_GROUP_CORRECTION)
				.findFirst().orElseThrow();
		assertThat(correction.getAction()).isEqualTo(AuditAction.UPDATED);
		assertThat(correction.getActorId()).isEqualTo("operator");
		assertThat(correction.getSessionId()).isEqualTo(session.getId());
		assertThat(correction.getRequestId()).isEqualTo("req-correction");
		assertThat(correction.getClientInstanceId()).isEqualTo("browser-1");
		assertThat(correction.getChangedFields()).containsExactly("quantity");
		String json = jdbcTemplate.queryForObject(
				"select cast(before_data as varchar) || cast(after_data as varchar) from audit_events where id = ?",
				String.class, correction.getId());
		assertThat(json).contains("varietyId", "quantity", "houseId", "physicalBedId", "zoneId", "status")
				.doesNotContain("memo", "reservedQuantity", "createdAt", "updatedAt");

		long eventCount = auditEventRepository.count();
		mockMvc.perform(patch("/api/orchid-groups/{id}", groupId)
				.with(user("operator"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(updatePayload(15)))
				.andExpect(status().isOk());
		assertThat(auditEventRepository.count()).isEqualTo(eventCount);
	}

	private String payload(int quantity) {
		return """
				{"bedZoneId":%d,"varietyId":%d,"quantity":%d,"potSize":"4인치","ageYear":2,
				 "status":"정상","startPosition":1,"endPosition":2}
				""".formatted(zoneId, varietyId, quantity);
	}

	private String updatePayload(int quantity) {
		return """
				{"varietyId":%d,"quantity":%d,"potSize":"4인치","ageYear":2,
				 "status":"정상","startPosition":1,"endPosition":2}
				""".formatted(varietyId, quantity);
	}
}
