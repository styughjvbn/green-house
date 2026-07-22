package com.greenhouse.backend.analytics.repository;

import static com.greenhouse.backend.partner.domain.QBusinessPartner.businessPartner;
import static com.greenhouse.backend.sales.domain.QSalesSlip.salesSlip;
import static com.greenhouse.backend.sales.domain.QSalesSlipItem.salesSlipItem;
import static com.greenhouse.backend.settlement.domain.QPartnerBalanceSummary.partnerBalanceSummary;
import static com.greenhouse.backend.work.domain.operation.QWorkOperation.workOperation;
import static com.greenhouse.backend.work.domain.operation.QWorkType.workType;

import com.greenhouse.backend.analytics.dto.AnalyticsSlipSummaryResponse;
import com.greenhouse.backend.analytics.dto.PartnerAnalyticsStatResponse;
import com.greenhouse.backend.analytics.dto.WorkAnalyticsItemResponse;
import com.greenhouse.backend.work.domain.operation.WorkOperationStatus;
import com.greenhouse.backend.work.domain.operation.WorkTypeTemplate;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class SalesAnalyticsRepository {

	private final JPAQueryFactory queryFactory;

	public SalesAnalyticsRepository(EntityManager entityManager) {
		this.queryFactory = new JPAQueryFactory(entityManager);
	}

	public Long sumSales(LocalDate from, LocalDate to) {
		return nullToZero(queryFactory
				.select(salesSlip.totalAmount.sum().longValue())
				.from(salesSlip)
				.where(saleDateBetween(from, to), activeSalesSlip())
				.fetchOne());
	}

	public Long sumShippedQuantity(LocalDate from, LocalDate to) {
		return nullToZero(queryFactory
				.select(salesSlipItem.quantity.sum().longValue())
				.from(salesSlipItem)
				.join(salesSlipItem.salesSlip, salesSlip)
				.where(saleDateBetween(from, to), activeSalesSlip())
				.fetchOne());
	}

	public Long sumUnpaidAmount(LocalDate from, LocalDate to) {
		return nullToZero(queryFactory
				.select(salesSlip.remainingAmount.sum())
				.from(salesSlip)
				.where(saleDateBetween(from, to), activeSalesSlip(), salesSlip.remainingAmount.gt(0L))
				.fetchOne());
	}

	public List<Object[]> monthlySales(LocalDate from, LocalDate to) {
		var year = salesSlip.saleDate.year();
		var month = salesSlip.saleDate.month();
		var totalAmount = salesSlip.totalAmount.sum().longValue();
		return queryFactory
				.select(year, month, totalAmount)
				.from(salesSlip)
				.where(saleDateBetween(from, to), activeSalesSlip())
				.groupBy(year, month)
				.orderBy(year.asc(), month.asc())
				.fetch()
				.stream()
				.map(tuple -> new Object[] { tuple.get(year), tuple.get(month), nullToZero(tuple.get(totalAmount)) })
				.toList();
	}

	public List<Object[]> varietySales(LocalDate from, LocalDate to, int limit) {
		var amount = salesSlipItem.amount.sum().longValue();
		return queryFactory
				.select(salesSlipItem.itemName, amount)
				.from(salesSlipItem)
				.join(salesSlipItem.salesSlip, salesSlip)
				.where(saleDateBetween(from, to), activeSalesSlip())
				.groupBy(salesSlipItem.itemName)
				.orderBy(amount.desc())
				.limit(limit)
				.fetch()
				.stream()
				.map(tuple -> new Object[] { tuple.get(salesSlipItem.itemName), nullToZero(tuple.get(amount)) })
				.toList();
	}

	public List<Object[]> partnerSales(LocalDate from, LocalDate to, int limit) {
		var totalAmount = salesSlip.totalAmount.sum().longValue();
		return queryFactory
				.select(businessPartner.name, totalAmount)
				.from(salesSlip)
				.join(salesSlip.partner, businessPartner)
				.where(saleDateBetween(from, to), activeSalesSlip())
				.groupBy(businessPartner.name)
				.orderBy(totalAmount.desc())
				.limit(limit)
				.fetch()
				.stream()
				.map(tuple -> new Object[] { tuple.get(businessPartner.name), nullToZero(tuple.get(totalAmount)) })
				.toList();
	}

	public List<Object[]> paymentBreakdown(LocalDate from, LocalDate to) {
		var totalAmount = salesSlip.totalAmount.sum().longValue();
		return queryFactory
				.select(salesSlip.paymentStatus, totalAmount)
				.from(salesSlip)
				.where(saleDateBetween(from, to), activeSalesSlip())
				.groupBy(salesSlip.paymentStatus)
				.fetch()
				.stream()
				.map(tuple -> new Object[] { tuple.get(salesSlip.paymentStatus), nullToZero(tuple.get(totalAmount)) })
				.toList();
	}

	public List<AnalyticsSlipSummaryResponse> recentSlips(LocalDate from, LocalDate to, int limit) {
		return slipSummaryQuery(from, to)
				.orderBy(salesSlip.saleDate.desc(), salesSlip.id.desc())
				.limit(limit)
				.fetch();
	}

	public List<AnalyticsSlipSummaryResponse> unpaidSlips(LocalDate from, LocalDate to, int limit) {
		return slipSummaryQuery(from, to)
				.where(salesSlip.remainingAmount.gt(0L))
				.orderBy(salesSlip.remainingAmount.desc(), salesSlip.saleDate.desc())
				.limit(limit)
				.fetch();
	}

	public List<PartnerAnalyticsStatResponse> partnerStats(LocalDate from, LocalDate to) {
		var totalSales = salesSlip.totalAmount.sum().longValue();
		var transactionCount = salesSlip.id.count();
		var unpaidAmount = salesSlip.remainingAmount.sum();
		var paidAmount = salesSlip.paidAmount.sum();
		var latestSaleDate = salesSlip.saleDate.max();
		return queryFactory
				.select(
						businessPartner.id,
						businessPartner.name,
						businessPartner.partnerType,
						totalSales,
						transactionCount,
						unpaidAmount,
						paidAmount,
						partnerBalanceSummary.receivableBalance,
						partnerBalanceSummary.creditBalance,
						partnerBalanceSummary.unappliedPaymentAmount,
						latestSaleDate)
				.from(businessPartner)
				.leftJoin(salesSlip).on(
						salesSlip.partner.eq(businessPartner),
						saleDateBetween(from, to),
						activeSalesSlip())
				.leftJoin(partnerBalanceSummary).on(partnerBalanceSummary.partner.eq(businessPartner))
				.groupBy(
						businessPartner.id,
						businessPartner.name,
						businessPartner.partnerType,
						partnerBalanceSummary.receivableBalance,
						partnerBalanceSummary.creditBalance,
						partnerBalanceSummary.unappliedPaymentAmount)
				.having(
						transactionCount.gt(0L)
								.or(partnerBalanceSummary.receivableBalance.coalesce(0L).gt(0L))
								.or(partnerBalanceSummary.creditBalance.coalesce(0L).gt(0L))
								.or(partnerBalanceSummary.unappliedPaymentAmount.coalesce(0L).gt(0L)))
				.orderBy(totalSales.desc(), transactionCount.desc())
				.fetch()
				.stream()
				.map(tuple -> partnerStat(tuple, totalSales, transactionCount, unpaidAmount, paidAmount, latestSaleDate))
				.toList();
	}

	public Long countWorkOperations(LocalDate from, LocalDate to) {
		return nullToZero(queryFactory
				.select(workOperation.id.count())
				.from(workOperation)
				.where(workDateBetween(from, to), completedWorkOperation())
				.fetchOne());
	}

	public Long countWorkOperationsByTemplate(LocalDate from, LocalDate to, String template) {
		return nullToZero(queryFactory
				.select(workOperation.id.count())
				.from(workOperation)
				.join(workOperation.workType, workType)
				.where(
						workDateBetween(from, to),
						completedWorkOperation(),
						workType.template.eq(WorkTypeTemplate.valueOf(template)))
				.fetchOne());
	}

	public LocalDate latestWorkDate(LocalDate from, LocalDate to) {
		return queryFactory
				.select(workOperation.plannedStartDate.max())
				.from(workOperation)
				.where(workDateBetween(from, to), completedWorkOperation())
				.fetchOne();
	}

	public List<Object[]> workTypeCounts(LocalDate from, LocalDate to) {
		var count = workOperation.id.count();
		return queryFactory
				.select(workType.name, count)
				.from(workOperation)
				.join(workOperation.workType, workType)
				.where(workDateBetween(from, to), completedWorkOperation())
				.groupBy(workType.name)
				.orderBy(count.desc())
				.fetch()
				.stream()
				.map(tuple -> new Object[] { tuple.get(workType.name), nullToZero(tuple.get(count)) })
				.toList();
	}

	public List<WorkAnalyticsItemResponse> recentWorkOperations(LocalDate from, LocalDate to, int limit) {
		return queryFactory
				.select(Projections.constructor(
						WorkAnalyticsItemResponse.class,
						workOperation.id,
						workOperation.plannedStartDate,
						workType.name,
						workType.template,
						workOperation.title,
						workOperation.sourceScopeType,
						workOperation.worker,
						workOperation.memo,
						workOperation.status))
				.from(workOperation)
				.join(workOperation.workType, workType)
				.where(workDateBetween(from, to), completedWorkOperation())
				.orderBy(workOperation.plannedStartDate.desc(), workOperation.id.desc())
				.limit(limit)
				.fetch();
	}

	private com.querydsl.jpa.impl.JPAQuery<AnalyticsSlipSummaryResponse> slipSummaryQuery(LocalDate from, LocalDate to) {
		return queryFactory
				.select(Projections.constructor(
						AnalyticsSlipSummaryResponse.class,
						salesSlip.id,
						salesSlip.slipNumber,
						salesSlip.saleDate,
						businessPartner.name,
						salesSlip.totalAmount,
						salesSlip.paidAmount,
						salesSlip.remainingAmount,
						salesSlip.paymentStatus,
						salesSlip.salesStatus))
				.from(salesSlip)
				.join(salesSlip.partner, businessPartner)
				.where(saleDateBetween(from, to), activeSalesSlip());
	}

	private PartnerAnalyticsStatResponse partnerStat(
			Tuple tuple,
			NumberExpression<Long> totalSales,
			NumberExpression<Long> transactionCount,
			NumberExpression<Long> unpaidAmount,
			NumberExpression<Long> paidAmount,
			com.querydsl.core.types.dsl.DateExpression<LocalDate> latestSaleDate) {
		return new PartnerAnalyticsStatResponse(
				tuple.get(businessPartner.id),
				tuple.get(businessPartner.name),
				tuple.get(businessPartner.partnerType),
				nullToZero(tuple.get(totalSales)),
				nullToZero(tuple.get(transactionCount)),
				nullToZero(tuple.get(unpaidAmount)),
				nullToZero(tuple.get(paidAmount)),
				nullToZero(tuple.get(partnerBalanceSummary.receivableBalance)),
				nullToZero(tuple.get(partnerBalanceSummary.creditBalance)),
				nullToZero(tuple.get(partnerBalanceSummary.unappliedPaymentAmount)),
				tuple.get(latestSaleDate));
	}

	private BooleanExpression saleDateBetween(LocalDate from, LocalDate to) {
		return salesSlip.saleDate.between(from, to);
	}

	private BooleanExpression activeSalesSlip() {
		return salesSlip.salesStatus.ne("취소");
	}

	private BooleanExpression workDateBetween(LocalDate from, LocalDate to) {
		return workOperation.plannedStartDate.between(from, to);
	}

	private BooleanExpression completedWorkOperation() {
		return workOperation.status.in(WorkOperationStatus.COMPLETED, WorkOperationStatus.CORRECTED);
	}

	private Long nullToZero(Long value) {
		return value == null ? 0L : value;
	}
}
