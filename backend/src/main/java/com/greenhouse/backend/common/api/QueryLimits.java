package com.greenhouse.backend.common.api;

import com.greenhouse.backend.common.exception.QueryLimitExceededException;

public final class QueryLimits {
  public static final int LEGACY_ROWS = 500;
  public static final int CALENDAR_ROWS = 1000;

  private QueryLimits() {}

  public static void requireWithin(long rows, int maximum) {
    if (rows > maximum) throw new QueryLimitExceededException();
  }
}
