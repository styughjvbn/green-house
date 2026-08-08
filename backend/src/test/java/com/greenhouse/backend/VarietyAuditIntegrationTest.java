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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class VarietyAuditIntegrationTest extends AbstractBackendIntegrationTest {
	@Autowired AuditEventRepository auditEventRepository;

	@Test
	void recordsVarietyCreateUpdateDeactivateAndDelete() throws Exception {
		var created = mockMvc.perform(post("/api/varieties")
				.with(user("operator"))
				.header("X-Request-Id", "variety-create")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"genus":"감사속","name":"감사품종","defaultPotSize":"4인치",
						 "color":"#112233","saleEnabled":true,"description":"설명","memo":"메모"}
						"""))
				.andExpect(status().isCreated())
				.andReturn();
		Long varietyId = Long.valueOf(created.getResponse().getContentAsString()
				.replaceAll(".*\\\"id\\\":(\\d+).*", "$1"));

		mockMvc.perform(patch("/api/varieties/{id}", varietyId)
				.with(user("operator"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"genus":"감사속","name":"감사품종 수정","alias":"별칭","defaultPotSize":"5인치",
						 "color":"#445566","saleEnabled":false,"description":"수정 설명","memo":"수정 메모"}
						"""))
				.andExpect(status().isOk());
		mockMvc.perform(patch("/api/varieties/{id}/deactivate", varietyId).with(user("operator")))
				.andExpect(status().isOk());
		mockMvc.perform(delete("/api/varieties/{id}", varietyId).with(user("operator")))
				.andExpect(status().isOk());

		var events = auditEventRepository.findAll().stream()
				.filter(event -> event.getSource() == AuditSource.VARIETY_MANAGEMENT)
				.filter(event -> event.getEntityId().equals(varietyId))
				.toList();
		assertThat(events).extracting(event -> event.getAction())
				.containsExactly(AuditAction.CREATED, AuditAction.UPDATED, AuditAction.DEACTIVATED, AuditAction.DELETED);
		assertThat(events.get(1).getChangedFields())
				.contains("name", "alias", "defaultPotSize", "color", "saleEnabled", "description", "memo");
		assertThat(events.get(2).getChangedFields()).containsExactly("active");
		assertThat(events).allSatisfy(event -> {
			assertThat(event.getEntityType()).isEqualTo("VARIETY");
			assertThat(event.getVarietyId()).isEqualTo(varietyId);
			assertThat(event.getActorId()).isEqualTo("operator");
		});
	}
}
