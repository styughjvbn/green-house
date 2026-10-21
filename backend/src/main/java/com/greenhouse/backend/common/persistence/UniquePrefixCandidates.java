package com.greenhouse.backend.common.persistence;

import com.querydsl.core.types.EntityPath;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Function;

/** Count every query prefix in one aggregate query, before applying the candidate limit. */
public final class UniquePrefixCandidates {
  private UniquePrefixCandidates() {}

  public static List<Long> findIds(
      JPAQueryFactory factory,
      EntityPath<?> source,
      NumberExpression<Long> id,
      String query,
      Function<String, BooleanExpression> matches,
      Predicate scope) {
    var fields = new ArrayList<Expression<?>>();
    for (int end = query.offsetByCodePoints(0, 1);
        end < query.length();
        end = query.offsetByCodePoints(end, 1)) {
      var matchingId =
          new CaseBuilder()
              .when(matches.apply(query.substring(0, end)))
              .then(id)
              .otherwise(Expressions.nullExpression(Long.class));
      fields.add(matchingId.count());
      fields.add(matchingId.min());
    }
    if (fields.isEmpty()) return List.of();
    var row =
        factory
            .select(fields.toArray(Expression[]::new))
            .from(source)
            .where(scope, matches.apply(query.substring(0, query.offsetByCodePoints(0, 1))))
            .fetchOne();
    if (row == null) return List.of();
    var ids = new LinkedHashSet<Long>();
    for (int i = 0; i < fields.size(); i += 2) {
      if (Long.valueOf(1).equals(row.get(i, Long.class))) ids.add(row.get(i + 1, Long.class));
    }
    return List.copyOf(ids);
  }
}
