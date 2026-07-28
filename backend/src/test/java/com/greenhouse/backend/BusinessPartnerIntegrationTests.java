package com.greenhouse.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class BusinessPartnerIntegrationTests extends AbstractBackendIntegrationTest {

	@Test
	void pagesAndFiltersBusinessPartners() throws Exception {
		createPartner("페이지검증 가", "WHOLESALE", "첫 번째 메모");
		createPartner("페이지검증 나", "RETAIL", "두 번째 메모");
		createPartner("별도 거래처", "WHOLESALE", "페이지검증 메모");

		mockMvc.perform(get("/api/business-partners/page")
						.param("keyword", "페이지검증")
						.param("active", "true")
						.param("page", "0")
						.param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content.length()").value(2))
				.andExpect(jsonPath("$.data.page").value(0))
				.andExpect(jsonPath("$.data.size").value(2))
				.andExpect(jsonPath("$.data.totalElements").value(3))
				.andExpect(jsonPath("$.data.totalPages").value(2));

		mockMvc.perform(get("/api/business-partners/page")
						.param("keyword", "페이지검증")
						.param("partnerType", "RETAIL")
						.param("page", "0")
						.param("size", "10"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content.length()").value(1))
				.andExpect(jsonPath("$.data.content[0].name").value("페이지검증 나"));
	}

	@Test
	void updatesBusinessPartnerBasicInformation() throws Exception {
		var partnerResult = mockMvc.perform(post("/api/business-partners")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "name": "테스트 도매처",
						  "partnerType": "WHOLESALE",
						  "ownerName": "김대표",
						  "phone": "010-0000-0000",
						  "address": "기존 주소",
						  "memo": "기존 메모"
						}
						"""))
				.andExpect(status().isCreated())
				.andReturn();
		var partnerId = Long.valueOf(
				partnerResult.getResponse().getContentAsString().replaceAll(".*\\\"id\\\":(\\d+).*", "$1"));

		mockMvc.perform(put("/api/business-partners/{partnerId}", partnerId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "name": "수정 거래처",
						  "partnerType": "RETAIL",
						  "ownerName": "박대표",
						  "phone": "010-1111-2222",
						  "address": "수정 주소",
						  "memo": "수정 메모"
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(partnerId))
				.andExpect(jsonPath("$.data.name").value("수정 거래처"))
				.andExpect(jsonPath("$.data.partnerType").value("RETAIL"))
				.andExpect(jsonPath("$.data.ownerName").value("박대표"))
				.andExpect(jsonPath("$.data.phone").value("010-1111-2222"))
				.andExpect(jsonPath("$.data.address").value("수정 주소"))
				.andExpect(jsonPath("$.data.memo").value("수정 메모"))
				.andExpect(jsonPath("$.data.active").value(true));
	}

	private void createPartner(String name, String partnerType, String memo) throws Exception {
		mockMvc.perform(post("/api/business-partners")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "%s",
								  "partnerType": "%s",
								  "ownerName": null,
								  "phone": null,
								  "address": null,
								  "memo": "%s"
								}
								""".formatted(name, partnerType, memo)))
				.andExpect(status().isCreated());
	}
}
