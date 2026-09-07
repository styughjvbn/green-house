package com.greenhouse.backend.analytics.application;

import com.greenhouse.backend.analytics.domain.AnalyticsDateRange;
import com.greenhouse.backend.analytics.dto.AnalyticsRankedValueResponse;
import com.greenhouse.backend.analytics.dto.AnalyticsSlipSummaryResponse;
import com.greenhouse.backend.analytics.dto.PartnerAnalyticsResponse;
import com.greenhouse.backend.analytics.dto.PartnerAnalyticsStatResponse;
import com.greenhouse.backend.analytics.dto.SalesAnalyticsResponse;
import com.greenhouse.backend.analytics.dto.VarietyInventoryAnalyticsResponse;
import com.greenhouse.backend.analytics.dto.WorkAnalyticsItemResponse;
import com.greenhouse.backend.analytics.dto.WorkAnalyticsResponse;
import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.farm.application.status.FarmMetricsReader;
import com.greenhouse.backend.partner.application.BusinessPartnerReader.Identity;
import com.greenhouse.backend.partner.application.BusinessPartnerReader;
import com.greenhouse.backend.sales.application.SalesMetricsReader.NamedAmount;
import com.greenhouse.backend.sales.application.SalesMetricsReader.PartnerSales;
import com.greenhouse.backend.sales.application.SalesMetricsReader.SlipSummary;
import com.greenhouse.backend.sales.application.SalesMetricsReader;
import com.greenhouse.backend.settlement.application.PartnerBalanceService.Balance;
import com.greenhouse.backend.settlement.application.PartnerBalanceService;
import com.greenhouse.backend.work.application.operation.WorkOperationMetricsReader;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

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
		var currentMonth = range.endingMonth();
		var previousMonth = range.previousMonthComparison();
		Long currentMonthSales = salesMetrics.sumSales(currentMonth.from(), currentMonth.to());
		Long previousMonthSales = salesMetrics.sumSales(previousMonth.from(), previousMonth.to());
		Long shippedQuantity = salesMetrics.sumShippedQuantity(currentMonth.from(), currentMonth.to());
		Long previousMonthShippedQuantity = salesMetrics.sumShippedQuantity(previousMonth.from(), previousMonth.to());
		Long unpaidAmount = salesMetrics.sumUnpaidAmount(range.from(), range.to());
		var monthlySales = SalesAnalyticsResponseAssembler.monthlySales(
				range.to(), salesMetrics.monthlySales(range.from(), range.to()));
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
				SalesAnalyticsResponseAssembler.paymentBreakdown(salesMetrics.paymentBreakdown(range.from(), range.to())),
				recentSlips,
				unpaidSlips,
				SalesAnalyticsResponseAssembler.salesInsights(unpaidAmount));
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

	private AnalyticsDateRange dateRange(LocalDate from, LocalDate to) {
		return AnalyticsDateRange.resolve(from, to, TimeConfig.farmToday(clock));
	}
}
