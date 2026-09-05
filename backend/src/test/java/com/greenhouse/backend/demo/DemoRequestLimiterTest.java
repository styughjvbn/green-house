package com.greenhouse.backend.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class DemoRequestLimiterTest {

	private final MutableClock clock = new MutableClock("2026-09-05T23:58:00Z");

	@Test
	void resetsMinuteWindowsAndKeepsClientsIndependent() {
		var limiter = limiter(1, 1, 10);
		assertThat(limiter.record("a", false)).isEqualTo(DemoRequestLimit.ALLOWED);
		assertThat(limiter.record("a", false)).isEqualTo(DemoRequestLimit.MINUTE_EXCEEDED);
		assertThat(limiter.record("b", false)).isEqualTo(DemoRequestLimit.ALLOWED);
		clock.advanceSeconds(60);
		assertThat(limiter.record("a", false)).isEqualTo(DemoRequestLimit.ALLOWED);
	}

	@Test
	void aMinuteRejectionDoesNotConsumeTheDailyMutationQuota() {
		var limiter = limiter(10, 1, 2);
		assertThat(limiter.record("a", true)).isEqualTo(DemoRequestLimit.ALLOWED);
		assertThat(limiter.record("a", true)).isEqualTo(DemoRequestLimit.MINUTE_EXCEEDED);
		clock.advanceSeconds(60);
		assertThat(limiter.record("a", true)).isEqualTo(DemoRequestLimit.ALLOWED);
	}

	@Test
	void resetsDailyQuotaAtUtcMidnightAndAllowsReadsAfterMutationLimit() {
		var limiter = limiter(10, 10, 1);
		assertThat(limiter.record("a", true)).isEqualTo(DemoRequestLimit.ALLOWED);
		assertThat(limiter.record("a", true)).isEqualTo(DemoRequestLimit.DAY_EXCEEDED);
		assertThat(limiter.record("a", false)).isEqualTo(DemoRequestLimit.ALLOWED);
		clock.advanceSeconds(120);
		assertThat(limiter.record("a", true)).isEqualTo(DemoRequestLimit.ALLOWED);
	}

	@Test
	void concurrentRequestsCannotExceedTheMutationLimit() throws Exception {
		var limiter = limiter(100, 10, 100);
		try (var executor = Executors.newFixedThreadPool(8)) {
			var requests = IntStream.range(0, 50)
					.<Callable<DemoRequestLimit>>mapToObj(index -> () -> limiter.record("a", true)).toList();
			int allowed = 0;
			for (var response : executor.invokeAll(requests)) {
				if (response.get() == DemoRequestLimit.ALLOWED) allowed++;
			}
			assertThat(allowed).isEqualTo(10);
		}
	}

	private DemoRequestLimiter limiter(int requests, int mutations, int dailyMutations) {
		return new DemoRequestLimiter(new DemoProperties(true, "demo", requests, mutations, dailyMutations, 1024), clock);
	}

	private static final class MutableClock extends Clock {
		private Instant current;

		MutableClock(String current) {
			this.current = Instant.parse(current);
		}

		void advanceSeconds(long seconds) {
			current = current.plusSeconds(seconds);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return Clock.fixed(current, zone);
		}

		@Override
		public Instant instant() {
			return current;
		}
	}
}
