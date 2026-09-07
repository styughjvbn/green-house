package com.greenhouse.backend.analytics.application;

import com.greenhouse.backend.analytics.domain.AnalyticsDateRange;
import com.greenhouse.backend.analytics.dto.AnalyticsInsightResponse;
import com.greenhouse.backend.analytics.dto.AnalyticsRankedValueResponse;
import com.greenhouse.backend.analytics.dto.AnalyticsSlipSummaryResponse;
import com.greenhouse.backend.analytics.dto.PartnerAnalyticsResponse;
import com.greenhouse.backend.analytics.dto.PartnerAnalyticsStatResponse;
import com.greenhouse.backend.analytics.dto.SalesAnalyticsResponse;
import com.greenhouse.backend.analytics.dto.VarietyInventoryAnalyticsResponse;
import com.greenhouse.backend.analytics.dto.WorkAnalyticsItemResponse;
import com.greenhouse.backend.analytics.dto.WorkAnalyticsResponse;
import com.greenhouse.backend.sales.application.SalesMetricsReader;
import com.greenhouse.backend.sales.application.SalesMetricsReader.NamedAmount;
import com.greenhouse.backend.sales.application.SalesMetricsReader.PartnerSales;
import com.greenhouse.backend.sales.application.SalesMetricsReader.SlipSummary;
import com.greenhouse.backend.partner.application.BusinessPartnerReader;
import com.greenhouse.backend.partner.application.BusinessPartnerReader.Identity;
import com.greenhouse.backend.settlement.application.PartnerBalanceService;
import com.greenhouse.backend.settlement.application.PartnerBalanceService.Balance;
import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.farm.application.status.FarmMetricsReader;
import com.greenhouse.backend.work.application.operation.WorkOperationMetricsReader;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.HashSet;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
@RequiredArgsConstructor
public class AnalyticsQueryService {

	private final SalesMetricsReader salesMetrics;
	private final FarmMetricsReader farmMetricsReader;
	private final WorkOperationMetricsReader workMetricsReader;
	private final BusinessPartnerReader partnerReader;
	private final PartnerBalanceService balanceService;
	private final Clock clock;

	public SalesAnalyticsResponse getSalesAnalytics(LocalDate from, LocalDate to) {
		AnalyticsDateRange range = dateRange(from, to);
		YearMonth currentMonth = YearMonth.from(range.to());
		LocalDate currentMonthFrom = range.from().isAfter(currentMonth.atDay(1))
				? range.from()
				: currentMonth.atDay(1);
		LocalDate currentMonthTo = range.to();
		YearMonth previousMonth = currentMonth.minusMonths(1);
		LocalDate previousMonthFrom = previousMonth.atDay(
				Math.min(currentMonthFrom.getDayOfMonth(), previousMonth.lengthOfMonth()));
		LocalDate previousMonthTo = previousMonth.atDay(
				Math.min(currentMonthTo.getDayOfMonth(), previousMonth.lengthOfMonth()));
		Long currentMonthSales = salesMetrics.sumSales(currentMonthFrom, currentMonthTo);
		Long previousMonthSales = salesMetrics.sumSales(previousMonthFrom, previousMonthTo);
		Long shippedQuantity = salesMetrics.sumShippedQuantity(currentMonthFrom, currentMonthTo);
		Long previousMonthShippedQuantity = salesMetrics.sumShippedQuantity(
				previousMonthFrom,
				previousMonthTo);
		Long unpaidAmount = salesMetrics.sumUnpaidAmount(range.from(), range.to());
		List<AnalyticsRankedValueResponse> monthlySales = monthlySales(range.from(), range.to());
		var varietySales = ranked(salesMetrics.varietySales(range.from(), range.to()));
		var salesByPartner = salesMetrics.partnerSales(range.from(), range.to());
		var partners = partnerReader.getIdentities(salesByPartner.keySet());
		var partnerSales = salesByPartner.values().stream().collect(Collectors.toMap(
				row -> partners.get(row.partnerId()).name(), PartnerSales::totalSales, Long::sum))
				.entrySet().stream().map(entry -> new AnalyticsRankedValueResponse(entry.getKey(), entry.getValue()))
				.sorted(Comparator.comparing(AnalyticsRankedValueResponse::value).reversed()
						.thenComparing(AnalyticsRankedValueResponse::label))
				.limit(10).toList();
		var recentSlips = slips(salesMetrics.recentSlips(range.from(), range.to()), partners);
		var unpaidSlips = slips(salesMetrics.unpaidSlips(range.from(), range.to()), partners);
		var inventory = farmMetricsReader.getInventorySummary();
		String formattedUnpaidAmount = NumberFormat.getNumberInstance().format(unpaidAmount);
		return new SalesAnalyticsResponse(
				currentMonthSales,
				previousMonthSales,
				shippedQuantity,
				previousMonthShippedQuantity,
				unpaidAmount,
				inventory.saleableQuantity(),
				monthlySales,
				varietySales,
				inventory.varieties().stream()
						.map(row -> new VarietyInventoryAnalyticsResponse(
								row.varietyName(), row.saleableQuantity(), row.warningGroupCount()))
						.toList(),
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
		AnalyticsDateRange range = dateRange(from, to);
		var sales = salesMetrics.partnerSales(range.from(), range.to());
		var balances = balanceService.getNonzeroBalances();
		var ids = new HashSet<>(sales.keySet());
		balances.forEach((id, balance) -> {
			if (balance.hasPositiveBalance()) {
				ids.add(id);
			}
		});
		var partners = partnerReader.getIdentities(ids);
		var partnerStats = ids.stream().map(id -> {
			var partner = partners.get(id);
			var totals = sales.getOrDefault(id, new PartnerSales(id, 0, 0, 0, 0, null));
			var balance = balances.getOrDefault(id, Balance.ZERO);
			return new PartnerAnalyticsStatResponse(id, partner.name(), partner.partnerType(),
					totals.totalSales(), totals.transactionCount(), totals.unpaidAmount(), totals.paidAmount(),
					balance.receivableBalance(), balance.creditBalance(), balance.unappliedPaymentAmount(), totals.latestSaleDate());
		})
				// Preserve PostgreSQL's DESC NULLS FIRST for partners without period sales.
				.sorted(Comparator.comparing(
						(PartnerAnalyticsStatResponse row) -> row.transactionCount() == 0 ? null : row.totalSales(),
						Comparator.nullsFirst(Comparator.reverseOrder()))
						.thenComparing(PartnerAnalyticsStatResponse::transactionCount, Comparator.reverseOrder())
						.thenComparing(PartnerAnalyticsStatResponse::partnerId))
				.toList();
		var partnerSales = partnerStats.stream()
				.limit(10)
				.map(stat -> new AnalyticsRankedValueResponse(stat.partnerName(), stat.totalSales()))
				.toList();
		return new PartnerAnalyticsResponse(partnerStats, partnerSales);
	}

	public WorkAnalyticsResponse getWorkAnalytics(LocalDate from, LocalDate to) {
		AnalyticsDateRange range = dateRange(from, to);
		var summary = workMetricsReader.getSummary(range.from(), range.to());
		var recentRecords = summary.recentRecords().stream()
				.map(row -> new WorkAnalyticsItemResponse(
						row.id(), row.workDate(), row.workType(), row.workTypeTemplate(), row.title(),
						row.sourceScopeType(), row.worker(), row.memo(), row.status()))
				.toList();
		return new WorkAnalyticsResponse(
				summary.totalCount(),
				summary.movementCount(),
				summary.statusCount(),
				summary.latestWorkDate(),
				summary.typeCounts().stream()
						.map(count -> new AnalyticsRankedValueResponse(count.name(), count.count())).toList(),
				recentRecords);
	}

	private List<AnalyticsRankedValueResponse> monthlySales(LocalDate from, LocalDate to) {
		var values = salesMetrics.monthlySales(from, to);
		YearMonth end = YearMonth.from(to);
		return java.util.stream.IntStream.rangeClosed(0, 5)
				.mapToObj(index -> end.minusMonths(5L - index))
				.map(month -> new AnalyticsRankedValueResponse(
						month.getMonthValue() + "월",
						values.getOrDefault(month, 0L)))
				.toList();
	}

	private List<AnalyticsRankedValueResponse> ranked(List<NamedAmount> rows) {
		return rows.stream()
				.map(row -> new AnalyticsRankedValueResponse(row.name(), row.amount()))
				.toList();
	}

	private List<AnalyticsSlipSummaryResponse> slips(List<SlipSummary> rows, Map<Long, Identity> partners) {
		return rows.stream().map(row -> new AnalyticsSlipSummaryResponse(
				row.id(), row.slipNumber(), row.saleDate(), partners.get(row.partnerId()).name(), row.totalAmount(),
				row.paidAmount(), row.remainingAmount(), row.paymentStatus(), row.salesStatus())).toList();
	}

	private List<AnalyticsRankedValueResponse> paymentBreakdown(LocalDate from, LocalDate to) {
		Map<String, Long> values = salesMetrics.paymentBreakdown(from, to).stream()
				.collect(Collectors.toMap(row -> normalizePaymentStatus(row.name()), NamedAmount::amount, Long::sum));
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

	private AnalyticsDateRange dateRange(LocalDate from, LocalDate to) {
		return AnalyticsDateRange.resolve(from, to, TimeConfig.farmToday(clock));
	}
}
