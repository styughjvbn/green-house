package com.greenhouse.backend.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TimeConfigTests {

	private final Clock clock = Clock.fixed(Instant.parse("2026-07-21T16:02:03Z"), ZoneOffset.UTC);

	@Test
	void usesKoreanDateWithoutChangingUtcStorageTime() {
		assertThat(TimeConfig.farmToday(clock)).isEqualTo(LocalDate.of(2026, 7, 22));
		assertThat(TimeConfig.utcNow(clock)).isEqualTo(LocalDateTime.of(2026, 7, 21, 16, 2, 3));
		assertThat(TimeConfig.farmDateTimeToUtc(LocalDate.of(2026, 7, 20), clock))
				.isEqualTo(LocalDateTime.of(2026, 7, 19, 16, 2, 3));
	}

	@Test
	void convertsUtcStorageTimeToKoreanResponseTime() {
		assertThat(TimeConfig.toFarmTime(LocalDateTime.of(2026, 7, 21, 1, 2, 3)))
				.isEqualTo(LocalDateTime.of(2026, 7, 21, 10, 2, 3));
		assertThat(TimeConfig.toFarmTime(null)).isNull();
	}

	@Test
	void convertsKoreanDayStartToUtcForQueries() {
		assertThat(TimeConfig.farmDayStartUtc(LocalDate.of(2026, 7, 21)))
				.isEqualTo(LocalDateTime.of(2026, 7, 20, 15, 0));
	}
}
