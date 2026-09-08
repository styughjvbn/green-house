package com.greenhouse.backend.analytics.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.analytics.dto.AnalyticsInsightResponse;
import com.greenhouse.backend.analytics.dto.AnalyticsRankedValueResponse;
import com.greenhouse.backend.sales.domain.SalesPaymentCategory;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

class SalesAnalyticsResponseAssemblerTest {

	@Test
	void fillsTheLastSixCalendarMonthsInOrderAcrossTheYearBoundary() {
		var values = Map.of(YearMonth.of(2025, 8), 999L, YearMonth.of(2025, 9), 100L, YearMonth.of(2025, 12), 300L,
				YearMonth.of(2026, 2), 500L, YearMonth.of(2026, 3), 999L);
		assertThat(SalesAnalyticsResponseAssembler.monthlySales(LocalDate.of(2026, 2, 3), values)).containsExactly(
				new AnalyticsRankedValueResponse("9월", 100L), new AnalyticsRankedValueResponse("10월", 0L),
				new AnalyticsRankedValueResponse("11월", 0L), new AnalyticsRankedValueResponse("12월", 300L),
				new AnalyticsRankedValueResponse("1월", 0L), new AnalyticsRankedValueResponse("2월", 500L));
	}

	@Test
	void rendersPaymentGroupsInTheExistingOrderWithoutLosingLargeAmounts() {
		assertThat(SalesAnalyticsResponseAssembler.paymentBreakdown(Map.of(SalesPaymentCategory.UNPAID, 500L,
				SalesPaymentCategory.PARTIAL, 3_000_000_000L, SalesPaymentCategory.PAID, 2_000_000_000L)))
			.containsExactly(new AnalyticsRankedValueResponse("입금 완료", 2_000_000_000L),
					new AnalyticsRankedValueResponse("부분입금", 3_000_000_000L),
					new AnalyticsRankedValueResponse("미입금", 500L));
	}

	@Test
	void keepsZeroGroupsAndRemovesTheActionWhenNoAmountIsUnpaid() {
		assertThat(SalesAnalyticsResponseAssembler.paymentBreakdown(Map.of())).containsExactly(
				new AnalyticsRankedValueResponse("입금 완료", 0L), new AnalyticsRankedValueResponse("부분입금", 0L),
				new AnalyticsRankedValueResponse("미입금", 0L));
		assertThat(SalesAnalyticsResponseAssembler.salesInsights(0))
			.containsExactly(new AnalyticsInsightResponse("green", "현재 기간 미수 전표 없음", null, null));
	}

	@Test
	@ResourceLock("java.util.Locale")
	void preservesTheExistingInsightTextActionAndFormattingLocale() {
		Locale original = Locale.getDefault(Locale.Category.FORMAT);
		try {
			Locale.setDefault(Locale.Category.FORMAT, Locale.KOREA);
			assertThat(SalesAnalyticsResponseAssembler.salesInsights(3_000_000_000L))
				.containsExactly(new AnalyticsInsightResponse("red", "미수 전표 확인 필요: 3,000,000,000원", "판매 관리", "/sales"));
			Locale.setDefault(Locale.Category.FORMAT, Locale.GERMANY);
			assertThat(SalesAnalyticsResponseAssembler.salesInsights(12_345).getFirst().text())
				.isEqualTo("미수 전표 확인 필요: 12.345원");
		}
		finally {
			Locale.setDefault(Locale.Category.FORMAT, original);
		}
	}

}
