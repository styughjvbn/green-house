package com.greenhouse.backend.common.persistence;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.SimpleExpression;
import java.util.Collection;

/** Repository predicate support: preserve the complete set without expanding thousands of binds. */
public final class LongIdMembership {
  private static final int IN_LIMIT = 500;

  private LongIdMembership() {}

  public static BooleanExpression contains(SimpleExpression<Long> column, Collection<Long> ids) {
    if (ids.size() <= IN_LIMIT) return column.in(ids);
    return Expressions.booleanTemplate(
        "cast(sql('(? = any(?))', {0}, {1}) as boolean) = true",
        column, Expressions.constant(ids.toArray(Long[]::new)));
  }
}
