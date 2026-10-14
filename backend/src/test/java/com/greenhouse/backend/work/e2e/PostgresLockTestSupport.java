package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

final class PostgresLockTestSupport {
  private PostgresLockTestSupport() {}

  static int backendPid(Connection connection) throws SQLException {
    try (var statement = connection.createStatement();
        var rows = statement.executeQuery("SELECT pg_backend_pid()")) {
      assertThat(rows.next()).isTrue();
      return rows.getInt(1);
    }
  }

  static boolean isBlockedBy(JdbcTemplate jdbc, int worker, int owner) {
    // A queued row lock may wait on another waiter. Follow only this worker's blocking chain.
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            """
            WITH RECURSIVE blockers(pid) AS (
              SELECT unnest(pg_blocking_pids(?))
              UNION
              SELECT unnest(pg_blocking_pids(b.pid)) FROM blockers b
            )
            SELECT EXISTS (SELECT 1 FROM blockers WHERE pid = ?)
            """,
            Boolean.class,
            worker,
            owner));
  }

  static final class Worker {
    private final AtomicInteger pid = new AtomicInteger();
    private final CountDownLatch entered = new CountDownLatch(1);

    // Called by a service spy inside the real application transaction, before its unchanged body.
    void capture(JdbcTemplate jdbc) {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      if (pid.get() == 0) {
        pid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
        entered.countDown();
      }
    }

    boolean isBlockedByOwner(JdbcTemplate jdbc, int owner) {
      return isBlockedBy(jdbc, pid.get(), owner);
    }

    void awaitBlockedBy(JdbcTemplate jdbc, int owner, Future<?> future) throws Exception {
      assertThat(entered.await(5, TimeUnit.SECONDS))
          .as("Worker entered application transaction")
          .isTrue();
      assertThat(pid.get()).as("Worker uses a separate DB connection").isNotEqualTo(owner);
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
      while (System.nanoTime() < deadline) {
        assertThat(future.isDone())
            .as("Worker %s completed before blocking on %s", pid.get(), owner)
            .isFalse();
        if (isBlockedByOwner(jdbc, owner)) return;
        Thread.sleep(25);
      }
      throw new AssertionError("Worker " + pid.get() + " did not block on backend " + owner);
    }
  }
}
