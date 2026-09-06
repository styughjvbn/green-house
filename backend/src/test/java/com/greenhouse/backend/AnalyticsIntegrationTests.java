package com.greenhouse.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.operation.WorkSourceScopeType;
import com.greenhouse.backend.work.domain.operation.WorkType;
import com.greenhouse.backend.work.domain.operation.WorkTypeTemplate;
import com.greenhouse.backend.work.repository.WorkOperationRepository;
import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.partner.domain.PartnerType;
import com.greenhouse.backend.partner.repository.BusinessPartnerRepository;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesSlipItem;
import com.greenhouse.backend.sales.domain.SalesType;
import com.greenhouse.backend.sales.repository.SalesSlipRepository;
import com.greenhouse.backend.settlement.application.PartnerBalanceService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class AnalyticsIntegrationTests extends AbstractBackendIntegrationTest {
	@Autowired
	private WorkOperationRepository workOperationRepository;
	@Autowired
	private BusinessPartnerRepository businessPartnerRepository;
	@Autowired
	private SalesSlipRepository salesSlipRepository;
	@Autowired
	private PartnerBalanceService partnerBalanceService;

	@Test
	@Transactional
	void joinsPartnerBalancesByIdIncludingPartnersWithoutPeriodSales() throws Exception {
		var partner = businessPartnerRepository.saveAndFlush(new BusinessPartner(
				"잔액 거래처", PartnerType.WHOLESALE, null, null, null, null));
		partnerBalanceService.updateReceivable(partner.getId(), 12_345L, null);
		partner.update("변경된 거래처명", PartnerType.WHOLESALE, null, null, null, null);

		mockMvc.perform(get("/api/analytics/partners")
				.param("from", "2026-07-01")
				.param("to", "2026-07-31"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.partnerStats.length()").value(1))
				.andExpect(jsonPath("$.data.partnerStats[0].partnerId").value(partner.getId()))
				.andExpect(jsonPath("$.data.partnerStats[0].partnerName").value("변경된 거래처명"))
				.andExpect(jsonPath("$.data.partnerStats[0].totalSales").value(0))
				.andExpect(jsonPath("$.data.partnerStats[0].receivableBalance").value(12_345));
		mockMvc.perform(get("/api/business-partners/{id}/balance-summary", partner.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.partnerName").value("변경된 거래처명"))
				.andExpect(jsonPath("$.data.receivableBalance").value(12_345));
	}

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
	@Transactional
	void aggregatesOnlyCompletedSalesSlips() throws Exception {
		var partner = businessPartnerRepository.save(new BusinessPartner(
				"분석 거래처",
				PartnerType.WHOLESALE,
				null,
				null,
				null,
				null));
		var completed = salesSlipRepository.save(salesSlip(
				"ANALYTICS-COMPLETED",
				LocalDate.of(2026, 7, 10),
				partner,
				"출고 완료",
				2,
				1000));
		salesSlipRepository.save(salesSlip(
				"ANALYTICS-DRAFT",
				LocalDate.of(2026, 7, 11),
				partner,
				"작성중",
				7,
				1000));

		mockMvc.perform(get("/api/analytics/sales")
				.param("from", "2026-07-01")
				.param("to", "2026-07-31"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.currentMonthSales").value(2000))
				.andExpect(jsonPath("$.data.previousMonthSales").value(0))
				.andExpect(jsonPath("$.data.shippedQuantity").value(2))
				.andExpect(jsonPath("$.data.unpaidAmount").value(2000))
				.andExpect(jsonPath("$.data.recentSlips.length()").value(1))
				.andExpect(jsonPath("$.data.recentSlips[0].id").value(completed.getId()));
	}

	@Test
	void rejectsInvalidAnalyticsDateRange() throws Exception {
		mockMvc.perform(get("/api/analytics/sales")
				.param("from", "2026-08-01")
				.param("to", "2026-07-01"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/analytics/sales")
				.param("from", "2024-06-30")
				.param("to", "2026-07-01"))
				.andExpect(status().isBadRequest());
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

	private SalesSlip salesSlip(
			String slipNumber,
			LocalDate saleDate,
			BusinessPartner partner,
			String status,
			int quantity,
			int unitPrice) {
		var slip = new SalesSlip(
				slipNumber,
				saleDate,
				SalesType.DIRECT,
				null,
				partner.getId(),
				"미입금",
				status,
				null,
				null);
		slip.addItem(new SalesSlipItem(
				null,
				"분석 품종",
				"카틀레야",
				null,
				quantity,
				unitPrice,
				null));
		return slip;
	}
}
