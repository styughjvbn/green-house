package com.greenhouse.backend.farm.repository;

import static com.greenhouse.backend.farm.domain.QVariety.variety;

import com.greenhouse.backend.farm.domain.Variety;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

public class VarietyRepositoryImpl implements VarietyRepositoryCustom {

	private final JPAQueryFactory queryFactory;

	public VarietyRepositoryImpl(EntityManager entityManager) {
		this.queryFactory = new JPAQueryFactory(entityManager);
	}

	@Override
	public Page<Variety> search(String keyword, String genus, Boolean saleEnabled, Boolean active, Pageable pageable) {
		BooleanBuilder conditions = conditions(keyword, genus, saleEnabled, active);
		List<Variety> content = queryFactory
				.selectFrom(variety)
				.where(conditions)
				.orderBy(variety.active.desc(), variety.genus.asc(), variety.name.asc())
				.offset(pageable.getOffset())
				.limit(pageable.getPageSize())
				.fetch();
		Long total = queryFactory
				.select(variety.id.count())
				.from(variety)
				.where(conditions)
				.fetchOne();

		return new PageImpl<>(content, pageable, total == null ? 0 : total);
	}

	@Override
	public List<String> findDistinctGenera() {
		return queryFactory
				.select(variety.genus)
				.distinct()
				.from(variety)
				.orderBy(variety.genus.asc())
				.fetch();
	}

	private BooleanBuilder conditions(String keyword, String genus, Boolean saleEnabled, Boolean active) {
		return new BooleanBuilder()
				.and(keywordContains(keyword))
				.and(genusEq(genus))
				.and(saleEnabledEq(saleEnabled))
				.and(activeEq(active));
	}

	private BooleanBuilder keywordContains(String keyword) {
		if (isBlank(keyword)) {
			return null;
		}
		String normalizedKeyword = keyword.trim().toLowerCase();
		return new BooleanBuilder()
				.or(variety.code.lower().contains(normalizedKeyword))
				.or(variety.name.lower().contains(normalizedKeyword))
				.or(variety.alias.lower().contains(normalizedKeyword));
	}

	private BooleanExpression genusEq(String genus) {
		return isBlank(genus) ? null : variety.genus.eq(genus);
	}

	private BooleanExpression saleEnabledEq(Boolean saleEnabled) {
		return saleEnabled == null ? null : variety.saleEnabled.eq(saleEnabled);
	}

	private BooleanExpression activeEq(Boolean active) {
		return active == null ? null : variety.active.eq(active);
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
