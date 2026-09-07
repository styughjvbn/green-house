package com.greenhouse.backend.analytics.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AnalyticsDateRangeTest {

	private final LocalDate today = LocalDate.of(2026, 9, 6);

	@ParameterizedTest
	@CsvSource({
			"2026-03-30, 2026-03-31, 2026-03-30, 2026-02-28, 2026-02-28",
			"2024-03-30, 2024-03-31, 2024-03-30, 2024-02-29, 2024-02-29",
			"2026-01-20, 2026-01-31, 2026-01-20, 2025-12-20, 2025-12-31",
			"2025-08-01, 2026-09-07, 2026-09-01, 2026-08-01, 2026-08-07",
			"2026-03-10, 2026-04-08, 2026-04-01, 2026-03-01, 2026-03-08",
			"2026-02-28, 2026-02-28, 2026-02-28, 2026-01-28, 2026-01-28",
			"2024-02-01, 2024-02-29, 2024-02-01, 2024-01-01, 2024-01-29"
	})
	void comparesOnlyTheEndingMonthAndClampsEachPreviousMonthBoundary(
			LocalDate from, LocalDate to, LocalDate currentFrom, LocalDate previousFrom, LocalDate previousTo) {
		var range = new AnalyticsDateRange(from, to);
		assertThat(range.endingMonth()).isEqualTo(new AnalyticsDateRange(currentFrom, to));
		assertThat(range.previousMonthComparison()).isEqualTo(new AnalyticsDateRange(previousFrom, previousTo));
	}

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
