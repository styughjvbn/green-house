package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.greenhouse.backend.partner.application.BusinessPartnerService;
import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.partner.domain.PartnerType;
import com.greenhouse.backend.partner.repository.BusinessPartnerRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class BusinessPartnerIntegrationTests extends AbstractBackendIntegrationTest {
	@Autowired BusinessPartnerRepository partnerRepository;
	@Autowired BusinessPartnerService partnerService;
	@Autowired EntityManager entityManager;

	@Test
	void filtersOptionsBeforePagingAndKeepsStableOrderForDuplicateNames() throws Exception {
		var first = optionPartner("선택 검증", PartnerType.WHOLESALE, true);
		var second = optionPartner("선택 검증", PartnerType.RETAIL, true);
		optionPartner("선택 검증", PartnerType.AUCTION_HOUSE, true);
		optionPartner("선택 검증", PartnerType.WHOLESALE, false);

		mockMvc.perform(get("/api/business-partners/options").param("keyword", " 선택 검증 ")
				.param("active", "true").param("auctionHouse", "false").param("size", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.content[0].id").value(first.getId()))
			.andExpect(jsonPath("$.data.totalElements").value(2));
		mockMvc.perform(get("/api/business-partners/options").param("keyword", "선택 검증")
				.param("active", "true").param("auctionHouse", "false").param("size", "1").param("page", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.content[0].id").value(second.getId()));
		assertThat(partnerService.getOptions("선택 검증", true, true, 0, 10).totalElements()).isEqualTo(1);
		assertThat(partnerService.getOptions("선택 검증", null, false, 0, 10).totalElements()).isEqualTo(1);
		assertThat(partnerService.getOptions("선택 검증", null, null, 0, 10).totalElements()).isEqualTo(4);
	}

	@Test
	void resolvesInactiveSelectedOptionWithoutContactDetails() throws Exception {
		var partner = optionPartner("보존 거래처", PartnerType.WHOLESALE, false);
		mockMvc.perform(get("/api/business-partners/{id}/option", partner.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(partner.getId()))
			.andExpect(jsonPath("$.data.name").value("보존 거래처"))
			.andExpect(jsonPath("$.data.active").value(false))
			.andExpect(jsonPath("$.data.ownerName").doesNotExist())
			.andExpect(jsonPath("$.data.phone").doesNotExist())
			.andExpect(jsonPath("$.data.address").doesNotExist())
			.andExpect(jsonPath("$.data.memo").doesNotExist());
		mockMvc.perform(get("/api/business-partners/-1/option"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}

	@Test
	void limitsLegacyListButKeepsEveryOptionReachable() throws Exception {
		for (int i = 0; i < 501; i++) {
			optionPartner("누적 거래처 %03d".formatted(i), PartnerType.WHOLESALE, true);
		}
		mockMvc.perform(get("/api/business-partners").param("keyword", "누적 거래처"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(500))
			.andExpect(jsonPath("$.data[499].name").value("누적 거래처 499"));
		mockMvc.perform(get("/api/business-partners/options").param("keyword", "누적 거래처")
				.param("page", "50").param("size", "10"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.totalElements").value(501))
			.andExpect(jsonPath("$.data.content.length()").value(1))
			.andExpect(jsonPath("$.data.content[0].name").value("누적 거래처 500"))
			.andExpect(jsonPath("$.data.content[0].memo").doesNotExist());
		assertThat(partnerService.getOptions("누적 거래처 500", null, true, 0, 10).content()).hasSize(1);
	}

	@Test
	void preservesLegacyNameSearchAndSharesManagementSearchForOptions() {
		optionPartner("Alpha 100%", PartnerType.RETAIL, true);
		optionPartner("Alpha 1000", PartnerType.WHOLESALE, true);
		optionPartner("Alpha hidden", PartnerType.RETAIL, false);
		assertThat(partnerService.getPartners(" ALPHA ", PartnerType.RETAIL)).hasSize(1);
		assertThat(partnerService.getPartners("100%", null)).hasSize(1);
		assertThat(partnerService.getPartners("옵션 전용 메모", null)).isEmpty();
		for (String keyword : java.util.List.of("옵션 대표", "010-1234", "옵션 주소", "옵션 전용 메모")) {
			assertThat(partnerService.getOptions(keyword, null, true, 0, 10).totalElements()).isEqualTo(2);
		}
	}

	@Test
	void normalizesOptionPagingAndReturnsEmptyMissingPages() throws Exception {
		optionPartner("크기 검증", PartnerType.WHOLESALE, true);
		assertThat(partnerService.getOptions(null, null, true, -1, 0).page()).isZero();
		assertThat(partnerService.getOptions(null, null, true, -1, 0).size()).isEqualTo(1);
		assertThat(partnerService.getOptions(null, null, true, 0, 999).size()).isEqualTo(100);
		assertThat(partnerService.getOptions("크기 검증", null, true, 10, 10).content()).isEmpty();
		assertThat(partnerService.getOptions("없는 거래처", null, null, 0, 10).totalElements()).isZero();
		mockMvc.perform(get("/api/business-partners/options").param("auctionHouse", "invalid"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
	}

	@ParameterizedTest
	@ValueSource(ints = {1, 10, 50})
	void optionsUseTwoQueriesRegardlessOfPageSize(int size) {
		for (int i = 0; i <= size; i++) {
			optionPartner("조회 수 %03d".formatted(i), PartnerType.WHOLESALE, true);
		}
		entityManager.flush();
		entityManager.clear();
		var statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
		boolean wasEnabled = statistics.isStatisticsEnabled();
		statistics.setStatisticsEnabled(true);
		statistics.clear();
		try {
			var page = partnerService.getOptions("조회 수", null, true, 0, size);
			assertThat(page.content()).hasSize(size);
			assertThat(page.totalElements()).isEqualTo(size + 1);
			assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
			assertThat(statistics.getEntityLoadCount()).isEqualTo(size);
		} finally {
			statistics.setStatisticsEnabled(wasEnabled);
		}
	}

	private BusinessPartner optionPartner(String name, PartnerType type, boolean active) {
		var partner = new BusinessPartner(name, type, "옵션 대표", "010-1234", "옵션 주소", "옵션 전용 메모");
		ReflectionTestUtils.setField(partner, "active", active);
		return partnerRepository.save(partner);
	}


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
