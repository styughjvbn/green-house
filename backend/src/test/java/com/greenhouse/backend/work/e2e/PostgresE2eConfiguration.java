package com.greenhouse.backend.work.e2e;

import jakarta.persistence.EntityManagerFactory;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.springframework.boot.flyway.autoconfigure.FlywayConnectionDetails;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** One disposable server per test JVM, one database per cached application context. */
@TestConfiguration(proxyBeanMethods = false)
class PostgresE2eConfiguration {
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

  @Bean
  TestDatabase postgresTestDatabase() throws SQLException {
    synchronized (POSTGRES) {
      if (!POSTGRES.isRunning()) POSTGRES.start();
    }
    String name = "e2e_" + UUID.randomUUID().toString().replace("-", "");
    try (var connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var statement = connection.createStatement()) {
      statement.execute("CREATE DATABASE " + name);
    }
    System.getLogger(PostgresE2eConfiguration.class.getName())
        .log(System.Logger.Level.INFO, "Created E2E context database {0}", name);
    return new TestDatabase(name);
  }

  @Bean
  PostgresE2eReset postgresE2eReset(
      JdbcTemplate jdbc, EntityManagerFactory entityManagerFactory, TestDatabase database) {
    return new PostgresE2eReset(jdbc, entityManagerFactory, database.getJdbcUrl());
  }

  record TestDatabase(String name)
      implements JdbcConnectionDetails, FlywayConnectionDetails, AutoCloseable {
    @Override
    public String getJdbcUrl() {
      return POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + name);
    }

    @Override
    public String getDriverClassName() {
      return "org.postgresql.Driver";
    }

    @Override
    public String getUsername() {
      return POSTGRES.getUsername();
    }

    @Override
    public String getPassword() {
      return POSTGRES.getPassword();
    }

    @Override
    public void close() throws SQLException {
      try (var connection =
              DriverManager.getConnection(
                  POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
          var statement = connection.createStatement()) {
        statement.execute("DROP DATABASE " + name + " WITH (FORCE)");
      }
    }
  }
}
