package com.greenhouse.backend.demo;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;

/**
 * Per-process, per-client fixed UTC windows. Rejected minute requests do not consume the
 * daily quota.
 */
@RequiredArgsConstructor
final class DemoRequestLimiter {

	private final DemoProperties properties;

	private final Clock clock;

	private final ConcurrentHashMap<String, RequestWindow> windows = new ConcurrentHashMap<>();

	private final ConcurrentHashMap<String, DailyMutationWindow> dailyWindows = new ConcurrentHashMap<>();

	DemoRequestLimit record(String client, boolean mutation) {
		long currentMinute = clock.millis() / 60_000;
		RequestWindow window = windows.compute(client,
				(key, previous) -> previous == null || previous.minute() != currentMinute
						? new RequestWindow(currentMinute, 1, mutation ? 1 : 0) : previous.record(mutation));
		if (window.requests() > properties.requestLimitPerMinute()
				|| window.mutations() > properties.mutationLimitPerMinute()) {
			return DemoRequestLimit.MINUTE_EXCEEDED;
		}

		if (mutation && recordDailyMutation(client) > properties.mutationLimitPerDay()) {
			return DemoRequestLimit.DAY_EXCEEDED;
		}
		removeExpiredWindows(currentMinute);
		return DemoRequestLimit.ALLOWED;
	}

	private int recordDailyMutation(String client) {
		long currentDay = clock.millis() / 86_400_000;
		return dailyWindows.compute(client,
				(key, previous) -> previous == null || previous.day() != currentDay
						? new DailyMutationWindow(currentDay, 1)
						: new DailyMutationWindow(currentDay, previous.mutations() + 1))
			.mutations();
	}

	private void removeExpiredWindows(long currentMinute) {
		if (windows.size() <= 10_000)
			return;
		windows.entrySet().removeIf(entry -> entry.getValue().minute() < currentMinute);
		long currentDay = clock.millis() / 86_400_000;
		dailyWindows.entrySet().removeIf(entry -> entry.getValue().day() < currentDay);
	}

	private record RequestWindow(long minute, int requests, int mutations) {
		RequestWindow record(boolean mutation) {
			return new RequestWindow(minute, requests + 1, mutations + (mutation ? 1 : 0));
		}
	}

	private record DailyMutationWindow(long day, int mutations) {
	}

}
