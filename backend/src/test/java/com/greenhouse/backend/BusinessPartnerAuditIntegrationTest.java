package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.greenhouse.backend.audit.domain.AuditAction;
import com.greenhouse.backend.audit.domain.AuditSource;
import com.greenhouse.backend.audit.repository.AuditEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class BusinessPartnerAuditIntegrationTest extends AbstractBackendIntegrationTest {
	@Autowired AuditEventRepository auditEventRepository;

	@Test
	void recordsCreateAndUpdateWithoutPersonalInformation() throws Exception {
		var created = mockMvc.perform(post("/api/business-partners")
				.with(user("partner-operator"))
				.header("X-Request-Id", "partner-create")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestJson("감사 도매처", "WHOLESALE", "김대표",
						"010-1234-5678", "개인 주소", "민감 메모")))
				.andExpect(status().isCreated())
				.andReturn();
		Long partnerId = Long.valueOf(created.getResponse().getContentAsString()
				.replaceAll(".*\\\"id\\\":(\\d+).*", "$1"));

		mockMvc.perform(put("/api/business-partners/{partnerId}", partnerId)
				.with(user("partner-operator"))
				.header("X-Request-Id", "partner-update")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestJson("감사 소매처", "RETAIL", "박대표",
						"010-9999-8888", "변경 주소", "변경 메모")))
				.andExpect(status().isOk());

		var events = auditEventRepository.findAll().stream()
				.filter(event -> event.getSource() == AuditSource.PARTNER_MANAGEMENT)
				.filter(event -> event.getEntityId().equals(partnerId))
				.toList();
		assertThat(events).extracting(event -> event.getAction())
				.containsExactly(AuditAction.CREATED, AuditAction.UPDATED);
		assertThat(events.getLast().getChangedFields())
				.containsExactly("name", "partnerType", "ownerName", "phone", "address", "memo");
		assertThat(events).allSatisfy(event -> {
			assertThat(event.getEntityType()).isEqualTo("BUSINESS_PARTNER");
			assertThat(event.getActorId()).isEqualTo("partner-operator");
			assertThat(event.getBeforeData().toString())
					.doesNotContain("김대표", "010-1234-5678", "개인 주소", "민감 메모",
							"박대표", "010-9999-8888", "변경 주소", "변경 메모");
			assertThat(event.getAfterData().toString())
					.doesNotContain("김대표", "010-1234-5678", "개인 주소", "민감 메모",
							"박대표", "010-9999-8888", "변경 주소", "변경 메모");
		});
	}

	@Test
	void skipsAuditForUnchangedUpdate() throws Exception {
		var created = mockMvc.perform(post("/api/business-partners")
				.with(user("partner-operator"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestJson("동일 거래처", "RETAIL", null, null, null, null)))
				.andExpect(status().isCreated())
				.andReturn();
		Long partnerId = Long.valueOf(created.getResponse().getContentAsString()
				.replaceAll(".*\\\"id\\\":(\\d+).*", "$1"));

		mockMvc.perform(put("/api/business-partners/{partnerId}", partnerId)
				.with(user("partner-operator"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestJson("동일 거래처", "RETAIL", null, null, null, null)))
				.andExpect(status().isOk());

		assertThat(auditEventRepository.findAll().stream()
				.filter(event -> event.getSource() == AuditSource.PARTNER_MANAGEMENT)
				.filter(event -> event.getEntityId().equals(partnerId)))
				.hasSize(1);
	}

	private String requestJson(String name, String partnerType, String ownerName,
			String phone, String address, String memo) {
		return """
				{
				  "name": "%s",
				  "partnerType": "%s",
				  "ownerName": %s,
				  "phone": %s,
				  "address": %s,
				  "memo": %s
				}
				""".formatted(name, partnerType, json(ownerName), json(phone), json(address), json(memo));
	}

	private String json(String value) {
		return value == null ? "null" : "\"" + value + "\"";
	}
}
