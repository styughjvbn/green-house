package com.greenhouse.backend.auction.repository;

import static com.greenhouse.backend.auction.domain.QAuctionAttempt.auctionAttempt;
import static com.greenhouse.backend.auction.domain.QAuctionResultLine.auctionResultLine;
import static com.greenhouse.backend.auction.domain.QAuctionShipment.auctionShipment;
import static com.greenhouse.backend.auction.domain.QAuctionShipmentLot.auctionShipmentLot;
import static com.greenhouse.backend.partner.domain.QBusinessPartner.businessPartner;

import com.greenhouse.backend.auction.domain.AuctionInspectionStatus;
import com.greenhouse.backend.auction.domain.AuctionLotStatus;
import com.greenhouse.backend.auction.domain.AuctionShipmentLot;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

public class AuctionShipmentLotRepositoryImpl implements AuctionShipmentLotRepositoryCustom {

	private static final List<AuctionLotStatus> SUMMARY_REVIEW_STATUSES = List.of(
			AuctionLotStatus.REVIEW_REQUIRED,
			AuctionLotStatus.QUANTITY_MISMATCH,
			AuctionLotStatus.RETURN_INFERRED,
			AuctionLotStatus.PARTIALLY_RETURNED);
	private static final List<AuctionInspectionStatus> SUMMARY_REVIEW_INSPECTIONS = List.of(
			AuctionInspectionStatus.MANUAL_REVIEW,
			AuctionInspectionStatus.MATCH_FAILED,
			AuctionInspectionStatus.QUANTITY_MISMATCH,
			AuctionInspectionStatus.RETURN_INFERRED,
			AuctionInspectionStatus.SOURCE_ERROR);

	private final JPAQueryFactory queryFactory;

	public AuctionShipmentLotRepositoryImpl(EntityManager entityManager) {
		this.queryFactory = new JPAQueryFactory(entityManager);
	}

	@Override
	public Page<AuctionShipmentLot> search(
			LocalDate from,
			LocalDate to,
			String market,
			String variety,
			String grade,
			AuctionLotStatus status,
			boolean reviewOnly,
			boolean returnOnly,
			boolean waitingOnly,
			String keyword,
			List<AuctionLotStatus> returnStatuses,
			List<AuctionLotStatus> waitingStatuses,
			List<AuctionLotStatus> reviewStatuses,
			List<AuctionInspectionStatus> reviewInspections,
			Pageable pageable) {
		BooleanBuilder conditions = conditions(
				from,
				to,
				market,
				variety,
				grade,
				status,
				reviewOnly,
				returnOnly,
				waitingOnly,
				keyword,
				returnStatuses,
				waitingStatuses,
				reviewStatuses,
				reviewInspections);
		List<AuctionShipmentLot> content = queryFactory
				.selectFrom(auctionShipmentLot)
				.join(auctionShipmentLot.shipment, auctionShipment).fetchJoin()
				.join(auctionShipment.auctionHouse, businessPartner).fetchJoin()
				.where(conditions)
				.orderBy(auctionShipmentLot.id.desc())
				.offset(pageable.getOffset())
				.limit(pageable.getPageSize())
				.fetch();
		Long total = queryFactory
				.select(auctionShipmentLot.id.count())
				.from(auctionShipmentLot)
				.join(auctionShipmentLot.shipment, auctionShipment)
				.join(auctionShipment.auctionHouse, businessPartner)
				.where(conditions)
				.fetchOne();

		return new PageImpl<>(content, pageable, total == null ? 0 : total);
	}

	@Override
	public AuctionTrackingSummaryProjection summarize() {
		Tuple lotSummary = queryFactory
				.select(
						auctionShipmentLot.id.count(),
						auctionShipmentLot.shippedQuantity.sum(),
						auctionShipmentLot.soldQuantity.sum(),
						auctionShipmentLot.waitingQuantity.sum(),
						auctionShipmentLot.returnedQuantity.sum())
				.from(auctionShipmentLot)
				.fetchOne();
		Long reviewRequiredCount = queryFactory
				.select(auctionShipmentLot.id.count())
				.from(auctionShipmentLot)
				.where(reviewRequired(SUMMARY_REVIEW_STATUSES, SUMMARY_REVIEW_INSPECTIONS))
				.fetchOne();
		Integer totalAmount = queryFactory
				.select(auctionResultLine.amount.sum())
				.from(auctionResultLine)
				.fetchOne();

		return new SummaryProjection(
				number(lotSummary, auctionShipmentLot.id.count()),
				number(lotSummary, auctionShipmentLot.shippedQuantity.sum()),
				number(lotSummary, auctionShipmentLot.soldQuantity.sum()),
				number(lotSummary, auctionShipmentLot.waitingQuantity.sum()),
				number(lotSummary, auctionShipmentLot.returnedQuantity.sum()),
				reviewRequiredCount == null ? 0 : reviewRequiredCount,
				totalAmount == null ? 0 : totalAmount);
	}

	private BooleanBuilder conditions(
			LocalDate from,
			LocalDate to,
			String market,
			String variety,
			String grade,
			AuctionLotStatus status,
			boolean reviewOnly,
			boolean returnOnly,
			boolean waitingOnly,
			String keyword,
			List<AuctionLotStatus> returnStatuses,
			List<AuctionLotStatus> waitingStatuses,
			List<AuctionLotStatus> reviewStatuses,
			List<AuctionInspectionStatus> reviewInspections) {
		return new BooleanBuilder()
				.and(shipmentDateGoe(from))
				.and(shipmentDateLoe(to))
				.and(marketEq(market))
				.and(varietyContains(variety))
				.and(gradeEq(grade))
				.and(statusEq(status))
				.and(returnOnly ? auctionShipmentLot.currentStatus.in(returnStatuses) : null)
				.and(waitingOnly ? auctionShipmentLot.currentStatus.in(waitingStatuses) : null)
				.and(reviewOnly ? reviewRequired(reviewStatuses, reviewInspections) : null)
				.and(keywordContains(keyword));
	}

	private BooleanExpression shipmentDateGoe(LocalDate from) {
		return from == null ? null : auctionShipment.shipmentDate.goe(from);
	}

	private BooleanExpression shipmentDateLoe(LocalDate to) {
		return to == null ? null : auctionShipment.shipmentDate.loe(to);
	}

	private BooleanExpression marketEq(String market) {
		return isBlank(market) ? null : businessPartner.name.equalsIgnoreCase(market);
	}

	private BooleanExpression varietyContains(String variety) {
		return isBlank(variety) ? null : auctionShipmentLot.varietyName.lower().contains(variety.trim().toLowerCase());
	}

	private BooleanExpression gradeEq(String grade) {
		return isBlank(grade) ? null : auctionShipmentLot.shipmentGrade.eq(grade);
	}

	private BooleanExpression statusEq(AuctionLotStatus status) {
		return status == null ? null : auctionShipmentLot.currentStatus.eq(status);
	}

	private BooleanExpression reviewRequired(
			List<AuctionLotStatus> reviewStatuses,
			List<AuctionInspectionStatus> reviewInspections) {
		return auctionShipmentLot.currentStatus.in(reviewStatuses)
				.or(JPAExpressions
						.selectOne()
						.from(auctionResultLine)
						.join(auctionResultLine.auctionAttempt, auctionAttempt)
						.where(
								auctionAttempt.shipmentLot.eq(auctionShipmentLot),
								auctionResultLine.inspectionStatus.in(reviewInspections))
						.exists());
	}

	private BooleanBuilder keywordContains(String keyword) {
		if (isBlank(keyword)) {
			return null;
		}
		String normalizedKeyword = keyword.trim().toLowerCase();
		return new BooleanBuilder().and(auctionShipmentLot.itemName
				.concat(" ")
				.concat(auctionShipmentLot.varietyName)
				.concat(" ")
				.concat(businessPartner.name)
				.lower()
				.contains(normalizedKeyword));
	}

	private Number number(Tuple tuple, com.querydsl.core.types.Expression<? extends Number> expression) {
		if (tuple == null) {
			return 0;
		}
		Number value = tuple.get(expression);
		return value == null ? 0 : value;
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private record SummaryProjection(
			Number lotCount,
			Number shippedQuantity,
			Number soldQuantity,
			Number waitingQuantity,
			Number returnedQuantity,
			Number reviewRequiredCount,
			Number totalAmount)
			implements AuctionTrackingSummaryProjection {
		@Override
		public Number getLotCount() {
			return lotCount;
		}

		@Override
		public Number getShippedQuantity() {
			return shippedQuantity;
		}

		@Override
		public Number getSoldQuantity() {
			return soldQuantity;
		}

		@Override
		public Number getWaitingQuantity() {
			return waitingQuantity;
		}

		@Override
		public Number getReturnedQuantity() {
			return returnedQuantity;
		}

		@Override
		public Number getReviewRequiredCount() {
			return reviewRequiredCount;
		}

		@Override
		public Number getTotalAmount() {
			return totalAmount;
		}
	}
}
