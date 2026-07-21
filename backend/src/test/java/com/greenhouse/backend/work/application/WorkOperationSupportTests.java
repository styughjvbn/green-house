package com.greenhouse.backend.work.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class WorkOperationSupportTests {

	private final WorkOperationSupport support = new WorkOperationSupport(Clock.fixed(
			Instant.parse("2026-07-21T01:02:03Z"),
			ZoneId.of("Asia/Seoul")));

	@Test
	void usesTheFarmClockForTodayAndCompletionTime() {
		assertThat(support.today()).isEqualTo(LocalDate.of(2026, 7, 21));
		assertThat(support.now()).isEqualTo(LocalDateTime.of(2026, 7, 21, 10, 2, 3));
		assertThat(support.completionTime(LocalDate.of(2026, 7, 20)))
				.isEqualTo(LocalDateTime.of(2026, 7, 20, 10, 2, 3));
	}

	@Test
	void rejectsAFutureCompletionDateAgainstTheFarmClock() {
		assertThatThrownBy(() -> support.completionTime(LocalDate.of(2026, 7, 22)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("완료일은 오늘 이후로 입력할 수 없습니다.");
	}
}
