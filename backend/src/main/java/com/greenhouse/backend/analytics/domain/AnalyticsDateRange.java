package com.greenhouse.backend.analytics.domain;

import java.time.LocalDate;

public record AnalyticsDateRange(LocalDate from, LocalDate to) {

	public AnalyticsDateRange {
		if (from.isAfter(to)) {
			throw new IllegalArgumentException("조회 시작일은 종료일보다 늦을 수 없습니다.");
		}
		if (from.isBefore(to.minusYears(2))) {
			throw new IllegalArgumentException("분석 기간은 최대 2년까지 조회할 수 있습니다.");
		}
	}

	public static AnalyticsDateRange resolve(LocalDate from, LocalDate to, LocalDate businessDate) {
		LocalDate end = to == null ? businessDate : to;
		LocalDate start = from == null ? end.minusMonths(11).withDayOfMonth(1) : from;
		return new AnalyticsDateRange(start, end);
	}
}
