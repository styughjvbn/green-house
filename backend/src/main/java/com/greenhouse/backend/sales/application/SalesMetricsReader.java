package com.greenhouse.backend.sales.application;

import static com.greenhouse.backend.sales.domain.QSalesSlip.salesSlip;
import static com.greenhouse.backend.sales.domain.QSalesSlipItem.salesSlipItem;

import com.greenhouse.backend.sales.domain.SalesSlip;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SalesMetricsReader {

	private final JPAQueryFactory queryFactory;

	public long sumSales(LocalDate from, LocalDate to) {
		return queryFactory.select(salesSlip.totalAmount.sum().longValue().coalesce(0L))
				.from(salesSlip).where(completedInPeriod(from, to)).fetchOne();
	}

	public long sumShippedQuantity(LocalDate from, LocalDate to) {
		return queryFactory.select(salesSlipItem.quantity.sum().longValue().coalesce(0L))
				.from(salesSlipItem).join(salesSlipItem.salesSlip, salesSlip)
				.where(completedInPeriod(from, to)).fetchOne();
	}

	public long sumUnpaidAmount(LocalDate from, LocalDate to) {
		return queryFactory.select(salesSlip.remainingAmount.sum().coalesce(0L))
				.from(salesSlip).where(completedInPeriod(from, to), salesSlip.remainingAmount.gt(0L)).fetchOne();
	}

	public Map<YearMonth, Long> monthlySales(LocalDate from, LocalDate to) {
		var year = salesSlip.saleDate.year();
		var month = salesSlip.saleDate.month();
		var amount = salesSlip.totalAmount.sum().longValue();
		return queryFactory.select(year, month, amount)
				.from(salesSlip).where(completedInPeriod(from, to))
				.groupBy(year, month).fetch().stream()
				.collect(Collectors.toUnmodifiableMap(
						row -> YearMonth.of(row.get(year), row.get(month)), row -> row.get(amount)));
	}

	public List<NamedAmount> varietySales(LocalDate from, LocalDate to) {
		var amount = salesSlipItem.amount.sum().longValue();
		return queryFactory.select(Projections.constructor(NamedAmount.class, salesSlipItem.itemName, amount))
				.from(salesSlipItem).join(salesSlipItem.salesSlip, salesSlip)
				.where(completedInPeriod(from, to)).groupBy(salesSlipItem.itemName)
				.orderBy(amount.desc(), salesSlipItem.itemName.asc()).limit(10).fetch();
	}

	public Map<Long, PartnerSales> partnerSales(LocalDate from, LocalDate to) {
		return queryFactory.select(Projections.constructor(PartnerSales.class,
						salesSlip.partnerId, salesSlip.totalAmount.sum().longValue(), salesSlip.id.count(),
						salesSlip.remainingAmount.sum().coalesce(0L), salesSlip.paidAmount.sum().coalesce(0L),
						salesSlip.saleDate.max()))
				.from(salesSlip).where(completedInPeriod(from, to))
				.groupBy(salesSlip.partnerId).fetch().stream()
				.collect(Collectors.toUnmodifiableMap(PartnerSales::partnerId, Function.identity()));
	}

	public List<NamedAmount> paymentBreakdown(LocalDate from, LocalDate to) {
		return queryFactory.select(Projections.constructor(NamedAmount.class,
						salesSlip.paymentStatus, salesSlip.totalAmount.sum().longValue()))
				.from(salesSlip).where(completedInPeriod(from, to))
				.groupBy(salesSlip.paymentStatus).fetch();
	}

	public List<SlipSummary> recentSlips(LocalDate from, LocalDate to) {
		return slipSummaryQuery(from, to).orderBy(salesSlip.saleDate.desc(), salesSlip.id.desc()).limit(5).fetch();
	}

	public List<SlipSummary> unpaidSlips(LocalDate from, LocalDate to) {
		return slipSummaryQuery(from, to).where(salesSlip.remainingAmount.gt(0L))
				.orderBy(salesSlip.remainingAmount.desc(), salesSlip.saleDate.desc(), salesSlip.id.desc()).limit(5).fetch();
	}

	private JPAQuery<SlipSummary> slipSummaryQuery(LocalDate from, LocalDate to) {
		return queryFactory.select(Projections.constructor(SlipSummary.class,
						salesSlip.id, salesSlip.slipNumber, salesSlip.saleDate, salesSlip.partnerId, salesSlip.totalAmount,
						salesSlip.paidAmount, salesSlip.remainingAmount, salesSlip.paymentStatus, salesSlip.salesStatus))
				.from(salesSlip).where(completedInPeriod(from, to));
	}

	private BooleanExpression completedInPeriod(LocalDate from, LocalDate to) {
		return salesSlip.saleDate.between(from, to).and(salesSlip.salesStatus.in(
				SalesSlip.STATUS_DIRECT_OUTBOUND_COMPLETED, SalesSlip.STATUS_AUCTION_SHIPMENT_COMPLETED));
	}

	public record NamedAmount(String name, long amount) {
	}

	public record PartnerSales(Long partnerId, long totalSales, long transactionCount,
			long unpaidAmount, long paidAmount, LocalDate latestSaleDate) {
	}

	public record SlipSummary(Long id, String slipNumber, LocalDate saleDate, Long partnerId, Integer totalAmount,
			Long paidAmount, Long remainingAmount, String paymentStatus, String salesStatus) {
	}
}
