package com.greenhouse.backend.sales.document.application;

import static com.greenhouse.backend.sales.document.domain.QSalesSlip.salesSlip;
import static com.greenhouse.backend.sales.document.domain.QSalesSlipItem.salesSlipItem;

import com.greenhouse.backend.sales.api.document.SalesMetricsApi;
import com.greenhouse.backend.sales.api.document.SalesPaymentCategory;
import com.greenhouse.backend.sales.document.domain.SalesSlip;
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
public class SalesMetricsReader implements SalesMetricsApi {

  private final JPAQueryFactory queryFactory;

  @Override
  public long sumSales(LocalDate from, LocalDate to) {
    return queryFactory
        .select(salesSlip.totalAmount.sum().longValue().coalesce(0L))
        .from(salesSlip)
        .where(completedInPeriod(from, to))
        .fetchOne();
  }

  @Override
  public long sumShippedQuantity(LocalDate from, LocalDate to) {
    return queryFactory
        .select(salesSlipItem.quantity.sum().longValue().coalesce(0L))
        .from(salesSlipItem)
        .join(salesSlipItem.salesSlip, salesSlip)
        .where(completedInPeriod(from, to))
        .fetchOne();
  }

  @Override
  public long sumUnpaidAmount(LocalDate from, LocalDate to) {
    return queryFactory
        .select(salesSlip.remainingAmount.sum().coalesce(0L))
        .from(salesSlip)
        .where(completedInPeriod(from, to), salesSlip.remainingAmount.gt(0L))
        .fetchOne();
  }

  @Override
  public Map<YearMonth, Long> monthlySales(LocalDate from, LocalDate to) {
    var year = salesSlip.saleDate.year();
    var month = salesSlip.saleDate.month();
    var amount = salesSlip.totalAmount.sum().longValue();
    return queryFactory
        .select(year, month, amount)
        .from(salesSlip)
        .where(completedInPeriod(from, to))
        .groupBy(year, month)
        .fetch()
        .stream()
        .collect(
            Collectors.toUnmodifiableMap(
                row -> YearMonth.of(row.get(year), row.get(month)), row -> row.get(amount)));
  }

  @Override
  public List<NamedAmount> varietySales(LocalDate from, LocalDate to) {
    var amount = salesSlipItem.amount.sum().longValue();
    return queryFactory
        .select(Projections.constructor(NamedAmount.class, salesSlipItem.itemName, amount))
        .from(salesSlipItem)
        .join(salesSlipItem.salesSlip, salesSlip)
        .where(completedInPeriod(from, to))
        .groupBy(salesSlipItem.itemName)
        .orderBy(amount.desc(), salesSlipItem.itemName.asc())
        .limit(10)
        .fetch();
  }

  @Override
  public Map<Long, PartnerSales> partnerSales(LocalDate from, LocalDate to) {
    return queryFactory
        .select(
            Projections.constructor(
                PartnerSales.class,
                salesSlip.partnerId,
                salesSlip.totalAmount.sum().longValue(),
                salesSlip.id.count(),
                salesSlip.remainingAmount.sum().coalesce(0L),
                salesSlip.paidAmount.sum().coalesce(0L),
                salesSlip.saleDate.max()))
        .from(salesSlip)
        .where(completedInPeriod(from, to))
        .groupBy(salesSlip.partnerId)
        .fetch()
        .stream()
        .collect(Collectors.toUnmodifiableMap(PartnerSales::partnerId, Function.identity()));
  }

  @Override
  public Map<SalesPaymentCategory, Long> paymentBreakdown(LocalDate from, LocalDate to) {
    return queryFactory
        .select(
            Projections.constructor(
                NamedAmount.class,
                salesSlip.paymentStatus,
                salesSlip.totalAmount.sum().longValue()))
        .from(salesSlip)
        .where(completedInPeriod(from, to))
        .groupBy(salesSlip.paymentStatus)
        .fetch()
        .stream()
        .collect(
            Collectors.toUnmodifiableMap(
                row -> SalesPaymentCategory.fromStoredStatus(row.name()),
                NamedAmount::amount,
                Long::sum));
  }

  @Override
  public List<SlipSummary> recentSlips(LocalDate from, LocalDate to) {
    return slipSummaryQuery(from, to)
        .orderBy(salesSlip.saleDate.desc(), salesSlip.id.desc())
        .limit(5)
        .fetch();
  }

  @Override
  public List<SlipSummary> unpaidSlips(LocalDate from, LocalDate to) {
    return slipSummaryQuery(from, to)
        .where(salesSlip.remainingAmount.gt(0L))
        .orderBy(salesSlip.remainingAmount.desc(), salesSlip.saleDate.desc(), salesSlip.id.desc())
        .limit(5)
        .fetch();
  }

  private JPAQuery<SlipSummary> slipSummaryQuery(LocalDate from, LocalDate to) {
    return queryFactory
        .select(
            Projections.constructor(
                SlipSummary.class,
                salesSlip.id,
                salesSlip.slipNumber,
                salesSlip.saleDate,
                salesSlip.partnerId,
                salesSlip.totalAmount,
                salesSlip.paidAmount,
                salesSlip.remainingAmount,
                salesSlip.paymentStatus,
                salesSlip.salesStatus))
        .from(salesSlip)
        .where(completedInPeriod(from, to));
  }

  private BooleanExpression completedInPeriod(LocalDate from, LocalDate to) {
    return salesSlip
        .saleDate
        .between(from, to)
        .and(
            salesSlip.salesStatus.in(
                SalesSlip.STATUS_DIRECT_OUTBOUND_COMPLETED,
                SalesSlip.STATUS_AUCTION_SHIPMENT_COMPLETED));
  }
}
