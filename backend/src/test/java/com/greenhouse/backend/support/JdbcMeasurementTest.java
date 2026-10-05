package com.greenhouse.backend.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcMeasurementTest {
  @Test
  void countsJdbcTemplateExecutionsDeliveredRowsBatchesAndTransactionBoundaries() throws Exception {
    var measurement = new JdbcMeasurement();
    var source =
        measurement.wrap(
            new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", ""));
    var jdbc = new JdbcTemplate(source);
    try {
      jdbc.execute("create table sample (id int primary key)");
      measurement.start();
      jdbc.batchUpdate(
          "insert into sample values (?)", List.of(new Object[] {1}, new Object[] {2}));
      assertThat(jdbc.queryForList("select id from sample order by id", Integer.class))
          .containsExactly(1, 2);
      try (Connection connection = source.getConnection()) {
        connection.setAutoCommit(false);
        try (var statement = connection.prepareStatement("insert into sample values (?)")) {
          statement.setInt(1, 3);
          statement.executeUpdate();
        }
        connection.commit();
        try (var statement = connection.createStatement()) {
          statement.executeUpdate("insert into sample values (4)");
        }
        connection.rollback();
        assertThat(connection.unwrap(Connection.class)).isSameAs(connection);
      }
      var sample = measurement.stop();
      assertThat(sample.executions()).isEqualTo(4);
      assertThat(sample.batches()).isOne();
      assertThat(sample.rows()).isEqualTo(2);
      assertThat(sample.commits()).isOne();
      assertThat(sample.rollbacks()).isOne();
      assertThat(sample.failures()).isZero();
      assertThat(sample.executionNanos()).isPositive();
      assertThat(sample.commitNanos()).isPositive();
      assertThat(jdbc.queryForList("select id from sample order by id", Integer.class))
          .containsExactly(1, 2, 3);
    } finally {
      // SHUTDOWN closes H2 before JdbcTemplate's DEBUG warning inspection runs.
      try (var connection = source.getConnection();
          var statement = connection.createStatement()) {
        statement.execute("shutdown");
      }
    }
  }

  @Test
  void propagatesDriverFailureAndRejectsOverlappingMeasurements() throws Exception {
    var measurement = new JdbcMeasurement();
    var source =
        measurement.wrap(new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID(), "sa", ""));
    measurement.start();
    assertThatThrownBy(measurement::start).isInstanceOf(IllegalStateException.class);
    try (var connection = source.getConnection();
        var statement = connection.createStatement()) {
      assertThatThrownBy(() -> statement.executeQuery("select * from missing_table"))
          .isInstanceOf(SQLException.class);
    }
    var sample = measurement.stop();
    assertThat(sample.executions()).isOne();
    assertThat(sample.failures()).isOne();
    assertThatThrownBy(measurement::stop).isInstanceOf(IllegalStateException.class);
    measurement.start();
    assertThat(measurement.stop().executions()).isZero();
  }
}
