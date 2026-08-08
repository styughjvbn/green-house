package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.greenhouse.backend.audit.domain.AuditAction;
import com.greenhouse.backend.audit.domain.AuditSource;
import com.greenhouse.backend.audit.repository.AuditEventRepository;
import com.greenhouse.backend.farm.domain.inbound.InboundRecord;
import com.greenhouse.backend.farm.domain.inbound.InboundStatus;
import com.greenhouse.backend.farm.domain.inbound.InboundType;
import com.greenhouse.backend.farm.domain.variety.Variety;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class InboundRecordAuditIntegrationTest extends AbstractBackendIntegrationTest {
	@Autowired AuditEventRepository auditEventRepository;

	@Test
	void recordsDirectInboundUpdateCancelAndDelete() throws Exception {
		Variety variety = varietyRepository.saveAndFlush(new Variety(
				"IN-AUDIT-" + System.nanoTime(), "입고감사속", "입고감사품종", null,
				"4인치", true, true, null, null));
		InboundRecord inbound = inboundRecordRepository.saveAndFlush(new InboundRecord(
				LocalDate.of(2026, 8, 1), InboundType.FLASK_SEEDLING, variety, InboundStatus.TEMP_STORED,
				2, 80, null, "선반 A", null, null, null, null, null, null, null, "작업자", "최초"));

		mockMvc.perform(patch("/api/inbound-records/{id}", inbound.getId())
				.with(user("operator"))
				.header("X-Request-Id", "inbound-update")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"inboundDate":"2026-08-02","bottleCount":3,"estimatedQuantity":90,
						 "tempLocation":"선반 B","pottingDueDate":"2026-08-10","potSize":"4인치",
						 "ageYear":1,"growthStage":"유묘","placementType":"TRAY","trayCount":2,
						 "worker":"수정자","memo":"수정"}
						"""))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/inbound-records/{id}/cancel", inbound.getId())
				.with(user("operator"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"memo\":\"오입력 취소\"}"))
				.andExpect(status().isOk());
		mockMvc.perform(delete("/api/inbound-records/{id}", inbound.getId()).with(user("operator")))
				.andExpect(status().isOk());

		var events = auditEventRepository.findAll().stream()
				.filter(event -> event.getSource() == AuditSource.INBOUND_MANAGEMENT)
				.filter(event -> event.getEntityId().equals(inbound.getId()))
				.toList();
		assertThat(events).extracting(event -> event.getAction())
				.containsExactly(AuditAction.UPDATED, AuditAction.DEACTIVATED, AuditAction.DELETED);
		assertThat(events.get(0).getChangedFields())
				.contains("inboundDate", "bottleCount", "estimatedQuantity", "tempLocation", "pottingDueDate",
						"potSize", "ageYear", "growthStage", "placementType", "trayCount", "worker", "memo");
		assertThat(events.get(1).getChangedFields()).containsExactly("status", "memo");
		assertThat(events).allSatisfy(event -> {
			assertThat(event.getEntityType()).isEqualTo("INBOUND_RECORD");
			assertThat(event.getVarietyId()).isEqualTo(variety.getId());
			assertThat(event.getActorId()).isEqualTo("operator");
		});
	}
}
