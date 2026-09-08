package com.greenhouse.backend.auction.repository;

import static com.greenhouse.backend.auction.domain.QAuctionAttempt.auctionAttempt;
import static com.greenhouse.backend.auction.domain.QAuctionResultLine.auctionResultLine;
import static com.greenhouse.backend.auction.domain.QAuctionShipment.auctionShipment;
import static com.greenhouse.backend.auction.domain.QAuctionShipmentLot.auctionShipmentLot;

import com.greenhouse.backend.auction.domain.AuctionInspectionStatus;
import com.greenhouse.backend.auction.domain.AuctionLotSearchCriteria;
import com.greenhouse.backend.auction.domain.AuctionLotStatus;
import com.greenhouse.backend.auction.domain.AuctionShipmentLot;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@RequiredArgsConstructor
public class AuctionShipmentLotRepositoryImpl implements AuctionShipmentLotRepositoryCustom {

	private final JPAQueryFactory queryFactory;

	@Override
	public Page<AuctionShipmentLot> search(AuctionLotSearchCriteria criteria, Pageable pageable) {
		BooleanBuilder conditions = conditions(criteria);
		List<AuctionShipmentLot> content = queryFactory.selectFrom(auctionShipmentLot)
			.join(auctionShipmentLot.shipment, auctionShipment)
			.fetchJoin()
			.where(conditions)
			.orderBy(auctionShipmentLot.id.desc())
			.offset(pageable.getOffset())
			.limit(pageable.getPageSize())
			.fetch();
		Long total = queryFactory.select(auctionShipmentLot.id.count())
			.from(auctionShipmentLot)
			.join(auctionShipmentLot.shipment, auctionShipment)
			.where(conditions)
			.fetchOne();

		return new PageImpl<>(content, pageable, total == null ? 0 : total);
	}

	@Override
	public AuctionTrackingSummaryProjection summarize() {
		Tuple lotSummary = queryFactory
			.select(auctionShipmentLot.id.count(), auctionShipmentLot.shippedQuantity.sum(),
					auctionShipmentLot.soldQuantity.sum(), auctionShipmentLot.waitingQuantity.sum(),
					auctionShipmentLot.returnedQuantity.sum())
			.from(auctionShipmentLot)
			.fetchOne();
		Long reviewRequiredCount = queryFactory.select(auctionShipmentLot.id.count())
			.from(auctionShipmentLot)
			.where(reviewRequired(AuctionLotStatus.reviewStatuses(), AuctionInspectionStatus.reviewStatuses()))
			.fetchOne();
		Integer totalAmount = queryFactory.select(auctionResultLine.amount.sum()).from(auctionResultLine).fetchOne();

		return new SummaryProjection(number(lotSummary, auctionShipmentLot.id.count()),
				number(lotSummary, auctionShipmentLot.shippedQuantity.sum()),
				number(lotSummary, auctionShipmentLot.soldQuantity.sum()),
				number(lotSummary, auctionShipmentLot.waitingQuantity.sum()),
				number(lotSummary, auctionShipmentLot.returnedQuantity.sum()),
				reviewRequiredCount == null ? 0 : reviewRequiredCount, totalAmount == null ? 0 : totalAmount);
	}

	private BooleanBuilder conditions(AuctionLotSearchCriteria criteria) {
		return new BooleanBuilder().and(shipmentDateGoe(criteria.from()))
			.and(shipmentDateLoe(criteria.to()))
			.and(criteria.marketIds() == null ? null : auctionShipment.auctionHouseId.in(criteria.marketIds()))
			.and(varietyContains(criteria.variety()))
			.and(gradeEq(criteria.grade()))
			.and(statusEq(criteria.status()))
			.and(criteria.returnOnly() ? auctionShipmentLot.currentStatus.in(AuctionLotStatus.returnStatuses()) : null)
			.and(criteria.waitingOnly() ? auctionShipmentLot.currentStatus.in(AuctionLotStatus.waitingStatuses())
					: null)
			.and(criteria.reviewOnly()
					? reviewRequired(AuctionLotStatus.reviewStatuses(), AuctionInspectionStatus.reviewStatuses())
					: null)
			.and(keywordContains(criteria));
	}

	private BooleanExpression shipmentDateGoe(LocalDate from) {
		return from == null ? null : auctionShipment.shipmentDate.goe(from);
	}

	private BooleanExpression shipmentDateLoe(LocalDate to) {
		return to == null ? null : auctionShipment.shipmentDate.loe(to);
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

	private BooleanExpression reviewRequired(List<AuctionLotStatus> reviewStatuses,
			List<AuctionInspectionStatus> reviewInspections) {
		return auctionShipmentLot.currentStatus.in(reviewStatuses)
			.or(JPAExpressions.selectOne()
				.from(auctionResultLine)
				.join(auctionResultLine.auctionAttempt, auctionAttempt)
				.where(auctionAttempt.shipmentLot.eq(auctionShipmentLot),
						auctionResultLine.inspectionStatus.in(reviewInspections))
				.exists());
	}

	private BooleanBuilder keywordContains(AuctionLotSearchCriteria criteria) {
		if (isBlank(criteria.keyword()))
			return null;
		var text = auctionShipmentLot.itemName.concat(" ").concat(auctionShipmentLot.varietyName).lower();
		var matches = new BooleanBuilder(text.contains(criteria.keyword()))
			.or(auctionShipment.auctionHouseId.in(criteria.keywordMarketIds()));
		// Preserve matches spanning the separator before the market name in the original
		// concatenated text.
		criteria.boundaryMarketIds()
			.forEach((prefix, ids) -> matches.or(text.endsWith(prefix).and(auctionShipment.auctionHouseId.in(ids))));
		return matches;
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

	private record SummaryProjection(Number lotCount, Number shippedQuantity, Number soldQuantity,
			Number waitingQuantity, Number returnedQuantity, Number reviewRequiredCount,
			Number totalAmount) implements AuctionTrackingSummaryProjection {
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
