package com.greenhouse.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class AnalyticsIntegrationTests extends AbstractBackendIntegrationTest {

	@Test
	void returnsSalesAnalyticsWithoutSeedData() throws Exception {
		mockMvc.perform(get("/api/analytics/sales"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.currentMonthSales").value(0))
				.andExpect(jsonPath("$.data.monthlySales").isArray())
				.andExpect(jsonPath("$.data.recentSlips").isArray());
	}

	@Test
	void returnsPartnerAnalyticsWithoutSeedData() throws Exception {
		mockMvc.perform(get("/api/analytics/partners"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.partnerStats").isArray())
				.andExpect(jsonPath("$.data.partnerSales").isArray());
	}

	@Test
	void returnsWorkAnalyticsWithoutSeedData() throws Exception {
		mockMvc.perform(get("/api/analytics/work"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalCount").value(0))
				.andExpect(jsonPath("$.data.workTypeCounts").isArray())
				.andExpect(jsonPath("$.data.recentRecords").isArray());
	}
}
