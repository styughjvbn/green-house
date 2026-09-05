package com.greenhouse.backend.analytics.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AnalyticsDateRangeTest {

	private final LocalDate today = LocalDate.of(2026, 9, 6);

	@Test
	void defaultsToTwelveCalendarMonthsEndingOnTheBusinessDate() {
		assertThat(AnalyticsDateRange.resolve(null, null, today))
				.isEqualTo(new AnalyticsDateRange(LocalDate.of(2025, 10, 1), today));
	}

	@Test
	void usesAnExplicitEndDateForTheDefaultStart() {
		LocalDate end = LocalDate.of(2024, 2, 29);
		assertThat(AnalyticsDateRange.resolve(null, end, today))
				.isEqualTo(new AnalyticsDateRange(LocalDate.of(2023, 3, 1), end));
	}

	@Test
	void acceptsExactlyTwoYearsButRejectsLongerOrReversedRanges() {
		assertThat(AnalyticsDateRange.resolve(today.minusYears(2), today, today).from())
				.isEqualTo(today.minusYears(2));
		assertThatThrownBy(() -> AnalyticsDateRange.resolve(today.minusYears(2).minusDays(1), today, today))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("최대 2년");
		assertThatThrownBy(() -> AnalyticsDateRange.resolve(today.plusDays(1), today, today))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("시작일");
	}
}
