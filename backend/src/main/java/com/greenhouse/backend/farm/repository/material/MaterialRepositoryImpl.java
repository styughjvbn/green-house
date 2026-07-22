package com.greenhouse.backend.farm.repository.material;

import static com.greenhouse.backend.farm.domain.material.QMaterial.material;

import com.greenhouse.backend.farm.domain.material.Material;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

public class MaterialRepositoryImpl implements MaterialRepositoryCustom {

	private final JPAQueryFactory queryFactory;

	public MaterialRepositoryImpl(EntityManager entityManager) {
		this.queryFactory = new JPAQueryFactory(entityManager);
	}

	@Override
	public Page<Material> search(String keyword, String category, String manufacturer, Boolean active, Pageable pageable) {
		BooleanBuilder conditions = conditions(keyword, category, manufacturer, active);
		List<Material> content = queryFactory
				.selectFrom(material)
				.where(conditions)
				.orderBy(material.active.desc(), material.category.asc(), material.name.asc())
				.offset(pageable.getOffset())
				.limit(pageable.getPageSize())
				.fetch();
		Long total = queryFactory
				.select(material.id.count())
				.from(material)
				.where(conditions)
				.fetchOne();

		return new PageImpl<>(content, pageable, total == null ? 0 : total);
	}

	private BooleanBuilder conditions(String keyword, String category, String manufacturer, Boolean active) {
		return new BooleanBuilder()
				.and(keywordContains(keyword))
				.and(categoryEq(category))
				.and(manufacturerContains(manufacturer))
				.and(activeEq(active));
	}

	private BooleanBuilder keywordContains(String keyword) {
		if (isBlank(keyword)) {
			return null;
		}
		String normalizedKeyword = keyword.trim().toLowerCase();
		return new BooleanBuilder()
				.or(material.code.lower().contains(normalizedKeyword))
				.or(material.name.lower().contains(normalizedKeyword));
	}

	private BooleanExpression categoryEq(String category) {
		return isBlank(category) ? null : material.category.eq(category);
	}

	private BooleanExpression manufacturerContains(String manufacturer) {
		return isBlank(manufacturer) ? null : material.manufacturer.lower().contains(manufacturer.trim().toLowerCase());
	}

	private BooleanExpression activeEq(Boolean active) {
		return active == null ? null : material.active.eq(active);
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
