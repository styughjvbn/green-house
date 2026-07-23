package com.greenhouse.backend.analytics.application;

import com.greenhouse.backend.analytics.dto.AnalyticsInsightResponse;
import com.greenhouse.backend.analytics.dto.AnalyticsRankedValueResponse;
import com.greenhouse.backend.analytics.dto.PartnerAnalyticsResponse;
import com.greenhouse.backend.analytics.dto.SalesAnalyticsResponse;
import com.greenhouse.backend.analytics.dto.WorkAnalyticsResponse;
import com.greenhouse.backend.analytics.repository.SalesAnalyticsRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.text.NumberFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AnalyticsQueryService {

	private final SalesAnalyticsRepository salesAnalyticsRepository;

	public SalesAnalyticsResponse getSalesAnalytics(LocalDate from, LocalDate to) {
		DateRange range = normalizeRange(from, to);
		YearMonth currentMonth = YearMonth.from(range.to());
		LocalDate currentMonthFrom = currentMonth.atDay(1);
		LocalDate currentMonthTo = currentMonth.atEndOfMonth();
		Long currentMonthSales = salesAnalyticsRepository.sumSales(currentMonthFrom, currentMonthTo);
		Long shippedQuantity = salesAnalyticsRepository.sumShippedQuantity(currentMonthFrom, currentMonthTo);
		Long unpaidAmount = salesAnalyticsRepository.sumUnpaidAmount(range.from(), range.to());
		List<AnalyticsRankedValueResponse> monthlySales = monthlySales(range.from(), range.to());
		List<AnalyticsRankedValueResponse> varietySales = ranked(salesAnalyticsRepository.varietySales(range.from(), range.to(), 10));
		List<AnalyticsRankedValueResponse> partnerSales = ranked(salesAnalyticsRepository.partnerSales(range.from(), range.to(), 10));
		var recentSlips = salesAnalyticsRepository.recentSlips(range.from(), range.to(), 5);
		var unpaidSlips = salesAnalyticsRepository.unpaidSlips(range.from(), range.to(), 5);
		String formattedUnpaidAmount = NumberFormat.getNumberInstance().format(unpaidAmount);
		return new SalesAnalyticsResponse(
				currentMonthSales,
				shippedQuantity,
				unpaidAmount,
				monthlySales,
				varietySales,
				partnerSales,
				paymentBreakdown(range.from(), range.to()),
				recentSlips,
				unpaidSlips,
				List.of(new AnalyticsInsightResponse(
						unpaidAmount > 0 ? "red" : "green",
						unpaidAmount > 0
								? "미수 전표 확인 필요: " + formattedUnpaidAmount + "원"
								: "현재 기간 미수 전표 없음",
						unpaidAmount > 0 ? "판매 관리" : null,
						unpaidAmount > 0 ? "/sales" : null)));
	}

	public PartnerAnalyticsResponse getPartnerAnalytics(LocalDate from, LocalDate to) {
		DateRange range = normalizeRange(from, to);
		var partnerStats = salesAnalyticsRepository.partnerStats(range.from(), range.to());
		var partnerSales = partnerStats.stream()
				.limit(10)
				.map(stat -> new AnalyticsRankedValueResponse(stat.partnerName(), stat.totalSales()))
				.toList();
		return new PartnerAnalyticsResponse(partnerStats, partnerSales);
	}

	public WorkAnalyticsResponse getWorkAnalytics(LocalDate from, LocalDate to) {
		DateRange range = normalizeRange(from, to);
		var recentRecords = salesAnalyticsRepository.recentWorkOperations(range.from(), range.to(), 10);
		return new WorkAnalyticsResponse(
				salesAnalyticsRepository.countWorkOperations(range.from(), range.to()),
				salesAnalyticsRepository.countWorkOperationsByTemplate(range.from(), range.to(), "MOVEMENT"),
				salesAnalyticsRepository.countWorkOperationsByTemplate(range.from(), range.to(), "STATUS"),
				salesAnalyticsRepository.latestWorkDate(range.from(), range.to()),
				ranked(salesAnalyticsRepository.workTypeCounts(range.from(), range.to())),
				recentRecords);
	}

	private List<AnalyticsRankedValueResponse> monthlySales(LocalDate from, LocalDate to) {
		Map<String, Long> values = salesAnalyticsRepository.monthlySales(from, to).stream()
				.collect(Collectors.toMap(
						row -> String.format("%04d-%02d", ((Number) row[0]).intValue(), ((Number) row[1]).intValue()),
						row -> ((Number) row[2]).longValue()));
		YearMonth end = YearMonth.from(to);
		return java.util.stream.IntStream.rangeClosed(0, 5)
				.mapToObj(index -> end.minusMonths(5L - index))
				.map(month -> new AnalyticsRankedValueResponse(
						month.getMonthValue() + "월",
						values.getOrDefault(month.toString(), 0L)))
				.toList();
	}

	private List<AnalyticsRankedValueResponse> ranked(List<Object[]> rows) {
		return rows.stream()
				.map(row -> new AnalyticsRankedValueResponse(String.valueOf(row[0]), ((Number) row[1]).longValue()))
				.toList();
	}

	private List<AnalyticsRankedValueResponse> paymentBreakdown(LocalDate from, LocalDate to) {
		Map<String, Long> values = salesAnalyticsRepository.paymentBreakdown(from, to).stream()
				.collect(Collectors.toMap(row -> normalizePaymentStatus(String.valueOf(row[0])), row -> ((Number) row[1]).longValue(), Long::sum));
		return List.of(
				new AnalyticsRankedValueResponse("입금 완료", values.getOrDefault("입금 완료", 0L)),
				new AnalyticsRankedValueResponse("부분입금", values.getOrDefault("부분입금", 0L)),
				new AnalyticsRankedValueResponse("미입금", values.getOrDefault("미입금", 0L)));
	}

	private String normalizePaymentStatus(String status) {
		if (status.contains("부분")) return "부분입금";
		if (status.contains("완료") || status.equals("PAID")) return "입금 완료";
		return "미입금";
	}

	private DateRange normalizeRange(LocalDate from, LocalDate to) {
		LocalDate normalizedTo = to == null ? LocalDate.now() : to;
		LocalDate normalizedFrom = from == null ? normalizedTo.minusMonths(11).withDayOfMonth(1) : from;
		if (normalizedFrom.isAfter(normalizedTo)) {
			return new DateRange(normalizedTo, normalizedFrom);
		}
		return new DateRange(normalizedFrom, normalizedTo);
	}

	private record DateRange(LocalDate from, LocalDate to) {
	}
}
