package com.greenhouse.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PaginationContractIntegrationTest extends AbstractBackendIntegrationTest {

	@ParameterizedTest
	@ValueSource(strings = { "/api/business-partners/page", "/api/business-partners/options", "/api/sales-slips/page",
			"/api/auction-settlements/page", "/api/partner-payment-events/page" })
	void keepsClampingContracts(String path) throws Exception {
		mockMvc.perform(get(path).param("page", "-1").param("size", "0"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.page").value(0))
			.andExpect(jsonPath("$.data.size").value(1));
		mockMvc.perform(get(path).param("page", "2").param("size", "2147483647"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.size").value(100));
	}

	@ParameterizedTest
	@ValueSource(strings = { "/api/auction-lots", "/api/varieties", "/api/materials", "/api/inbound-records",
			"/api/work-operations" })
	void keepsValidationContracts(String path) throws Exception {
		for (String[] input : new String[][] { { "-1", "20" }, { "0", "0" }, { "0", "101" } }) {
			mockMvc.perform(get(path).param("page", input[0]).param("size", input[1]))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
		}
	}

}
