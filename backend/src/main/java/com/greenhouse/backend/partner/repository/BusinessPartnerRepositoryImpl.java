package com.greenhouse.backend.partner.repository;

import static com.greenhouse.backend.partner.domain.QBusinessPartner.businessPartner;

import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.partner.domain.PartnerType;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

public class BusinessPartnerRepositoryImpl implements BusinessPartnerRepositoryCustom {

	private final JPAQueryFactory queryFactory;

	public BusinessPartnerRepositoryImpl(EntityManager entityManager) {
		this.queryFactory = new JPAQueryFactory(entityManager);
	}

	@Override
	public Page<BusinessPartner> searchPage(
			String keyword,
			PartnerType partnerType,
			Boolean active,
			Pageable pageable) {
		BooleanBuilder conditions = conditions(keyword, partnerType, active);
		List<BusinessPartner> content = queryFactory
				.selectFrom(businessPartner)
				.where(conditions)
				.orderBy(businessPartner.name.asc(), businessPartner.id.asc())
				.offset(pageable.getOffset())
				.limit(pageable.getPageSize())
				.fetch();
		Long total = queryFactory
				.select(businessPartner.id.count())
				.from(businessPartner)
				.where(conditions)
				.fetchOne();

		return new PageImpl<>(content, pageable, total == null ? 0 : total);
	}

	private BooleanBuilder conditions(String keyword, PartnerType partnerType, Boolean active) {
		return new BooleanBuilder()
				.and(keywordContains(keyword))
				.and(partnerTypeEq(partnerType))
				.and(activeEq(active));
	}

	private BooleanBuilder keywordContains(String keyword) {
		if (keyword == null || keyword.isBlank()) {
			return null;
		}
		String normalized = keyword.trim().toLowerCase();
		return new BooleanBuilder()
				.or(businessPartner.name.lower().contains(normalized))
				.or(businessPartner.ownerName.lower().contains(normalized))
				.or(businessPartner.phone.lower().contains(normalized))
				.or(businessPartner.address.lower().contains(normalized))
				.or(businessPartner.memo.lower().contains(normalized));
	}

	private BooleanExpression partnerTypeEq(PartnerType partnerType) {
		return partnerType == null ? null : businessPartner.partnerType.eq(partnerType);
	}

	private BooleanExpression activeEq(Boolean active) {
		return active == null ? null : businessPartner.active.eq(active);
	}
}
