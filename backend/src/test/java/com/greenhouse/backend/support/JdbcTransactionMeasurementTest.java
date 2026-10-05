package com.greenhouse.backend.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcTransactionMeasurementTest {
  @Test
  void observesPreparedAndPlainLocksCommitRollbackAndImplicitCompletion() throws Exception {
    var measurement = new JdbcTransactionMeasurement();
    var source =
        measurement.wrap(new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID(), "sa", ""));
    try (var connection = source.getConnection();
        var statement = connection.createStatement()) {
      statement.execute("create table sample (id int primary key)");
      statement.execute("insert into sample values (1)");
      measurement.start();
      connection.setAutoCommit(false);
      try (var locked = connection.prepareStatement("select * from sample where id=? for update")) {
        locked.setInt(1, 1);
        try (var rows = locked.executeQuery()) {
          assertThat(rows.next()).isTrue();
        }
      }
      connection.commit();
      statement.executeQuery("select * from sample for update").close();
      connection.rollback();
      statement.executeQuery("select * from sample").close();
      connection.setAutoCommit(true);
      // Auto-commit selects must not become measured explicit transactions.
      statement.executeQuery("select * from sample for update").close();
      var sample = measurement.stop();
      assertThat(sample.started()).isEqualTo(3);
      assertThat(sample.completed()).isEqualTo(3);
      assertThat(sample.rollbacks()).isOne();
      assertThat(sample.implicitCommits()).isOne();
      assertThat(sample.transactionsWithObservedRowLocks()).isEqualTo(2);
      assertThat(sample.lockingStatements()).isEqualTo(2);
      assertThat(sample.totalObservedLockHoldNanos()).isPositive();
      assertThat(sample.maximumTransactionNanos()).isPositive();
      assertThat(sample.openAtEnd()).isZero();
      assertThat(sample.closedWithoutObservedCompletion()).isZero();
      assertThat(connection.unwrap(Connection.class)).isSameAs(connection);
      measurement.start();
      assertThat(measurement.stop().started()).isZero();
    }
  }

  @Test
  void preservesSqlFailuresAndReportsOpenOrUnobservedTransactionsInsteadOfInventingCommit()
      throws Exception {
    var measurement = new JdbcTransactionMeasurement();
    var source =
        measurement.wrap(new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID(), "sa", ""));
    measurement.start();
    assertThatThrownBy(measurement::start).isInstanceOf(IllegalStateException.class);
    try (var connection = source.getConnection();
        var statement = connection.createStatement()) {
      connection.setAutoCommit(false);
      assertThatThrownBy(() -> statement.execute("select * from missing_table"))
          .isInstanceOf(SQLException.class);
      assertThat(measurement.stop().openAtEnd()).isOne();
      assertThatThrownBy(measurement::stop).isInstanceOf(IllegalStateException.class);
      connection.rollback();
      measurement.start();
      statement.execute("select 1");
    }
    var closed = measurement.stop();
    assertThat(closed.completed()).isZero();
    assertThat(closed.closedWithoutObservedCompletion()).isOne();
    assertThat(closed.openAtEnd()).isZero();
  }
}
