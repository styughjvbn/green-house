package com.greenhouse.backend.farm.variety.repository;

import static com.greenhouse.backend.farm.variety.domain.QVariety.variety;

import com.greenhouse.backend.farm.variety.domain.Variety;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.StringExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@RequiredArgsConstructor
public class VarietyRepositoryImpl implements VarietyRepositoryCustom {

  private static final List<String> SEARCH_SPACES =
      List.of(" ", "\t", "\n", "\r", "\u00a0", "\u3000");

  private final JPAQueryFactory queryFactory;

  @Override
  public List<Variety> searchCandidates(String keyword, String genusHint, int limit) {
    if (isBlank(keyword)) return List.of();
    String query = keyword.trim().toLowerCase(Locale.ROOT);
    String compactQuery = query;
    for (String space : SEARCH_SPACES) compactQuery = compactQuery.replace(space, "");
    if (compactQuery.isEmpty()) return List.of();
    var compactName = withoutSearchSpaces(variety.name.lower());
    var compactAlias = withoutSearchSpaces(variety.alias.lower());
    var nameContains = compactName.contains(compactQuery);
    var aliasContains = compactAlias.contains(compactQuery);
    var relevance =
        new CaseBuilder()
            .when(compactName.eq(compactQuery))
            .then(0)
            .when(compactAlias.eq(compactQuery))
            .then(1)
            .when(nameContains)
            .then(2)
            .otherwise(3);
    var originalMatch =
        new CaseBuilder()
            .when(variety.name.lower().eq(query).or(variety.alias.lower().eq(query)))
            .then(0)
            .when(variety.name.lower().contains(query).or(variety.alias.lower().contains(query)))
            .then(1)
            .otherwise(2);
    var genusMatch =
        new CaseBuilder()
            .when(variety.genus.eq(isBlank(genusHint) ? "" : genusHint.trim()))
            .then(0)
            .otherwise(1);
    return queryFactory
        .selectFrom(variety)
        .where(nameContains.or(aliasContains))
        .orderBy(
            relevance.asc(),
            originalMatch.asc(),
            genusMatch.asc(),
            variety.active.desc(),
            variety.name.asc(),
            variety.genus.asc(),
            variety.id.asc())
        .limit(Math.clamp(limit, 1, 20))
        .fetch();
  }

  private StringExpression withoutSearchSpaces(StringExpression value) {
    for (String space : SEARCH_SPACES)
      value = Expressions.stringTemplate("replace({0}, {1}, '')", value, space);
    return value;
  }

  @Override
  public Page<Variety> search(String keyword, String genus, Boolean active, Pageable pageable) {
    BooleanBuilder conditions = conditions(keyword, genus, active);
    List<Variety> content =
        queryFactory
            .selectFrom(variety)
            .where(conditions)
            .orderBy(variety.active.desc(), variety.genus.asc(), variety.name.asc())
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();
    Long total = queryFactory.select(variety.id.count()).from(variety).where(conditions).fetchOne();

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

  @Override
  public List<VarietyNameProjection> findActiveNames() {
    return queryFactory
        .select(
            Projections.constructor(
                VarietyNameProjection.class, variety.id, variety.genus, variety.name))
        .from(variety)
        .where(variety.active.isTrue())
        .orderBy(variety.genus.asc(), variety.name.asc())
        .fetch();
  }

  private BooleanBuilder conditions(String keyword, String genus, Boolean active) {
    return new BooleanBuilder()
        .and(keywordContains(keyword))
        .and(genusEq(genus))
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

  private BooleanExpression activeEq(Boolean active) {
    return active == null ? null : variety.active.eq(active);
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
