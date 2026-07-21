package com.greenhouse.backend.work.application;

import com.greenhouse.backend.common.config.TimeConfig;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkOperationSupport {

	private final Clock clock;

	public LocalDate today() {
		return TimeConfig.farmToday(clock);
	}

	public LocalDateTime now() {
		return TimeConfig.utcNow(clock);
	}

	public LocalDateTime completionTime(LocalDate completedDate) {
		LocalDate today = today();
		LocalDate date = completedDate == null ? today : completedDate;
		if (date.isAfter(today)) {
			throw new IllegalArgumentException("완료일은 오늘 이후로 입력할 수 없습니다.");
		}
		return TimeConfig.farmDateTimeToUtc(date, clock);
	}

	public void validateDates(LocalDate startDate, LocalDate endDate) {
		if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
			throw new IllegalArgumentException("예정 종료일은 예정 시작일보다 빠를 수 없습니다.");
		}
	}

	public String normalize(String value) {
		if (value == null) {
			return null;
		}
		String normalized = value.trim();
		return normalized.isEmpty() ? null : normalized;
	}

	public String normalizeRequired(String value) {
		String normalized = normalize(value);
		if (normalized == null) {
			throw new IllegalArgumentException("필수 문자열 값은 비워둘 수 없습니다.");
		}
		return normalized;
	}
}
