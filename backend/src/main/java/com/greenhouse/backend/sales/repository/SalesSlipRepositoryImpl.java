package com.greenhouse.backend.sales.repository;

import static com.greenhouse.backend.sales.domain.QSalesSlip.salesSlip;

import com.greenhouse.backend.sales.domain.SalesSlip;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@RequiredArgsConstructor
public class SalesSlipRepositoryImpl implements SalesSlipRepositoryCustom {

	private final JPAQueryFactory queryFactory;

	@Override
	public List<SalesSlip> search(Long partnerId, LocalDate from, LocalDate to, int limit) {
		List<Long> salesSlipIds = queryFactory.select(salesSlip.id)
			.from(salesSlip)
			.where(conditions(partnerId, from, to, null, null, null, List.of()))
			.orderBy(defaultOrder())
			.limit(limit)
			.fetch();
		if (salesSlipIds.isEmpty()) {
			return List.of();
		}
		return baseQuery().leftJoin(salesSlip.items)
			.fetchJoin()
			.where(salesSlip.id.in(salesSlipIds))
			.orderBy(defaultOrder())
			.distinct()
			.fetch();
	}

	@Override
	public Page<SalesSlip> searchPage(Long partnerId, LocalDate from, LocalDate to, String paymentStatus,
			String salesStatus, String keyword, List<Long> matchingPartnerIds, Pageable pageable) {
		BooleanBuilder conditions = conditions(partnerId, from, to, paymentStatus, salesStatus, keyword,
				matchingPartnerIds);
		List<SalesSlip> content = baseQuery().where(conditions)
			.orderBy(defaultOrder())
			.offset(pageable.getOffset())
			.limit(pageable.getPageSize())
			.fetch();
		Long total = queryFactory.select(salesSlip.count()).from(salesSlip).where(conditions).fetchOne();

		return new PageImpl<>(content, pageable, total == null ? 0 : total);
	}

	private JPAQuery<SalesSlip> baseQuery() {
		return queryFactory.selectFrom(salesSlip);
	}

	private BooleanBuilder conditions(Long partnerId, LocalDate from, LocalDate to, String paymentStatus,
			String salesStatus, String keyword, List<Long> matchingPartnerIds) {
		return new BooleanBuilder().and(partnerIdEq(partnerId))
			.and(saleDateGoe(from))
			.and(saleDateLoe(to))
			.and(paymentStatusEq(paymentStatus))
			.and(salesStatusEq(salesStatus))
			.and(keywordContains(keyword, matchingPartnerIds));
	}

	private BooleanExpression partnerIdEq(Long partnerId) {
		return partnerId == null ? null : salesSlip.partnerId.eq(partnerId);
	}

	private BooleanExpression saleDateGoe(LocalDate from) {
		return from == null ? null : salesSlip.saleDate.goe(from);
	}

	private BooleanExpression saleDateLoe(LocalDate to) {
		return to == null ? null : salesSlip.saleDate.loe(to);
	}

	private BooleanExpression paymentStatusEq(String paymentStatus) {
		return isBlank(paymentStatus) ? null : salesSlip.paymentStatus.eq(paymentStatus);
	}

	private BooleanExpression salesStatusEq(String salesStatus) {
		return isBlank(salesStatus) ? null : salesSlip.salesStatus.eq(salesStatus);
	}

	private BooleanBuilder keywordContains(String keyword, List<Long> matchingPartnerIds) {
		if (isBlank(keyword)) {
			return null;
		}
		String normalizedKeyword = keyword.trim().toLowerCase();
		return new BooleanBuilder().or(salesSlip.slipNumber.lower().contains(normalizedKeyword))
			.or(salesSlip.partnerId.in(matchingPartnerIds))
			.or(salesSlip.memo.lower().contains(normalizedKeyword));
	}

	private OrderSpecifier<?>[] defaultOrder() {
		return new OrderSpecifier<?>[] { salesSlip.saleDate.desc(), salesSlip.id.desc() };
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

}
