package com.greenhouse.backend.analytics.application;

import com.greenhouse.backend.analytics.dto.AnalyticsInsightResponse;
import com.greenhouse.backend.analytics.dto.AnalyticsRankedValueResponse;
import com.greenhouse.backend.sales.domain.SalesPaymentCategory;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

final class SalesAnalyticsResponseAssembler {

	private SalesAnalyticsResponseAssembler() {
	}

	static List<AnalyticsRankedValueResponse> monthlySales(LocalDate to, Map<YearMonth, Long> values) {
		YearMonth end = YearMonth.from(to);
		return IntStream.rangeClosed(0, 5)
			.mapToObj(index -> end.minusMonths(5L - index))
			.map(month -> new AnalyticsRankedValueResponse(month.getMonthValue() + "월", values.getOrDefault(month, 0L)))
			.toList();
	}

	static List<AnalyticsRankedValueResponse> paymentBreakdown(Map<SalesPaymentCategory, Long> values) {
		return List.of(new AnalyticsRankedValueResponse("입금 완료", values.getOrDefault(SalesPaymentCategory.PAID, 0L)),
				new AnalyticsRankedValueResponse("부분입금", values.getOrDefault(SalesPaymentCategory.PARTIAL, 0L)),
				new AnalyticsRankedValueResponse("미입금", values.getOrDefault(SalesPaymentCategory.UNPAID, 0L)));
	}

	static List<AnalyticsInsightResponse> salesInsights(long unpaidAmount) {
		if (unpaidAmount <= 0) {
			return List.of(new AnalyticsInsightResponse("green", "현재 기간 미수 전표 없음", null, null));
		}
		String amount = NumberFormat.getNumberInstance().format(unpaidAmount);
		return List.of(new AnalyticsInsightResponse("red", "미수 전표 확인 필요: " + amount + "원", "판매 관리", "/sales"));
	}

}
