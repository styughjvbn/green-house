package com.greenhouse.backend.analytics.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.greenhouse.backend.farm.application.status.FarmMetricsReader;
import com.greenhouse.backend.partner.application.BusinessPartnerReader;
import com.greenhouse.backend.sales.application.SalesMetricsReader;
import com.greenhouse.backend.settlement.application.PartnerBalanceService;
import com.greenhouse.backend.work.application.operation.WorkOperationMetricsReader;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalyticsQueryServiceTest {

	@Test
	void queriesTheKoreanBusinessDateWhenItIsStillThePreviousUtcDate() {
		var repository = mock(SalesMetricsReader.class);
		var farmMetrics = mock(FarmMetricsReader.class);
		var workMetrics = mock(WorkOperationMetricsReader.class);
		var from = LocalDate.of(2025, 10, 1);
		var to = LocalDate.of(2026, 9, 6);
		when(workMetrics.getSummary(from, to))
			.thenReturn(new WorkOperationMetricsReader.Summary(0, 0, 0, null, List.of(), List.of()));
		Clock clock = Clock.fixed(Instant.parse("2026-09-05T15:00:00Z"), ZoneOffset.UTC);

		new AnalyticsQueryService(repository, farmMetrics, workMetrics, mock(BusinessPartnerReader.class),
				mock(PartnerBalanceService.class), clock)
			.getWorkAnalytics(null, null);

		verify(workMetrics).getSummary(from, to);
	}

}
