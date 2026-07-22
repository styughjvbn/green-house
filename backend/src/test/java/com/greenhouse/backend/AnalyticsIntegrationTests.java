package com.greenhouse.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.operation.WorkSourceScopeType;
import com.greenhouse.backend.work.domain.operation.WorkType;
import com.greenhouse.backend.work.domain.operation.WorkTypeTemplate;
import com.greenhouse.backend.work.repository.WorkOperationRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class AnalyticsIntegrationTests extends AbstractBackendIntegrationTest {
	@Autowired
	private WorkOperationRepository workOperationRepository;

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

	@Test
	@Transactional
	void returnsCompletedWorkOperationsInWorkAnalytics() throws Exception {
		var workType = workTypeRepository.save(new WorkType(
				"ANALYTICS_MEMO", "분석 메모", WorkTypeTemplate.MEMO, false, false, true, 100));
		var operation = new WorkOperation(
				workType,
				"분석 대상 작업",
				LocalDate.of(2026, 7, 15),
				null,
				WorkSourceScopeType.FARM,
				null,
				null,
				null,
				"테스터",
				"분석 메모",
				LocalDateTime.of(2026, 7, 15, 1, 0));
		operation.complete(LocalDateTime.of(2026, 7, 15, 2, 0));
		workOperationRepository.save(operation);

		mockMvc.perform(get("/api/analytics/work")
				.param("from", "2026-07-01")
				.param("to", "2026-07-31"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalCount").value(1))
				.andExpect(jsonPath("$.data.recentRecords[0].id").value(operation.getId()))
				.andExpect(jsonPath("$.data.recentRecords[0].title").value("분석 대상 작업"))
				.andExpect(jsonPath("$.data.recentRecords[0].status").value("COMPLETED"));
	}
}
