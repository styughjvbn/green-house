package com.greenhouse.backend.work.e2e;

import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

/** Reset before subclass fixture setup, including when Spring reuses a previous class's context. */
public final class PostgresE2eResetListener extends AbstractTestExecutionListener {
  @Override
  public int getOrder() {
    return 100;
  }

  @Override
  public void beforeTestClass(TestContext testContext) {
    testContext.getApplicationContext().getBean(PostgresE2eReset.class).reset();
  }
}
