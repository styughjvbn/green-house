package com.greenhouse.backend.analytics.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.greenhouse.backend.analytics.repository.SalesAnalyticsRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AnalyticsQueryServiceTest {

	@Test
	void queriesTheKoreanBusinessDateWhenItIsStillThePreviousUtcDate() {
		var repository = mock(SalesAnalyticsRepository.class);
		Clock clock = Clock.fixed(Instant.parse("2026-09-05T15:00:00Z"), ZoneOffset.UTC);

		new AnalyticsQueryService(repository, clock).getWorkAnalytics(null, null);

		verify(repository).countWorkOperations(LocalDate.of(2025, 10, 1), LocalDate.of(2026, 9, 6));
	}
}
