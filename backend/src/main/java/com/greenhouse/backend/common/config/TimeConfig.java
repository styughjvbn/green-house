package com.greenhouse.backend.common.config;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

	public static final ZoneId FARM_TIME_ZONE = ZoneId.of("Asia/Seoul");
	public static final ZoneId STORAGE_TIME_ZONE = ZoneOffset.UTC;

	@Bean
	public Clock farmClock() {
		return Clock.systemUTC();
	}

	public static LocalDate farmToday(Clock clock) {
		return clock.instant().atZone(FARM_TIME_ZONE).toLocalDate();
	}

	public static LocalDateTime utcNow(Clock clock) {
		return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
	}

	public static LocalDateTime farmDateTimeToUtc(LocalDate date, Clock clock) {
		LocalTime farmTime = clock.instant().atZone(FARM_TIME_ZONE).toLocalTime();
		return LocalDateTime.of(date, farmTime)
				.atZone(FARM_TIME_ZONE)
				.withZoneSameInstant(STORAGE_TIME_ZONE)
				.toLocalDateTime();
	}

	public static LocalDateTime farmDayStartUtc(LocalDate date) {
		return date.atStartOfDay(FARM_TIME_ZONE)
				.withZoneSameInstant(STORAGE_TIME_ZONE)
				.toLocalDateTime();
	}

	public static LocalDateTime toFarmTime(LocalDateTime utcDateTime) {
		if (utcDateTime == null) {
			return null;
		}
		return utcDateTime.atZone(STORAGE_TIME_ZONE)
				.withZoneSameInstant(FARM_TIME_ZONE)
				.toLocalDateTime();
	}
}
