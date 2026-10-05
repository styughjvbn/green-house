package com.greenhouse.backend.support;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/** Isolated test-context measurement. Stores counters only, never SQL or parameter values. */
public final class JdbcMeasurement {
  private boolean active;
  private long executions;
  private long batches;
  private long rows;
  private long failures;
  private long commits;
  private long rollbacks;
  private long executionNanos;
  private long commitNanos;

  public synchronized void start() {
    if (active) throw new IllegalStateException("A JDBC measurement is already active");
    executions = batches = rows = failures = commits = rollbacks = executionNanos = commitNanos = 0;
    active = true;
  }

  public synchronized Sample stop() {
    if (!active) throw new IllegalStateException("No JDBC measurement is active");
    active = false;
    return new Sample(
        executions, batches, rows, failures, commits, rollbacks, executionNanos, commitNanos);
  }

  public record Sample(
      long executions,
      long batches,
      long rows,
      long failures,
      long commits,
      long rollbacks,
      long executionNanos,
      long commitNanos) {}

  private synchronized void completed(String operation, long nanos, boolean failed) {
    if (!active) return;
    switch (operation) {
      case "executeBatch", "executeLargeBatch" -> {
        executions++;
        batches++;
        executionNanos += nanos;
      }
      case "execute", "executeQuery", "executeUpdate", "executeLargeUpdate" -> {
        executions++;
        executionNanos += nanos;
      }
      case "commit" -> {
        commits++;
        commitNanos += nanos;
      }
      case "rollback" -> rollbacks++;
      default -> {}
    }
    if (failed) failures++;
  }

  private synchronized void row() {
    if (active) rows++;
  }

  DataSource wrap(DataSource source) {
    return new MeasuredDataSource(source);
  }

  private Connection connection(Connection delegate) {
    return proxy(
        Connection.class,
        (method, arguments) -> {
          Object result;
          if (method.getName().equals("commit") || method.getName().equals("rollback")) {
            result = timed(delegate, method, arguments);
          } else {
            result = invoke(delegate, method, arguments);
          }
          return result instanceof Statement statement ? statement(statement) : result;
        });
  }

  private Statement statement(Statement delegate) {
    Class<? extends Statement> type =
        delegate instanceof CallableStatement
            ? CallableStatement.class
            : delegate instanceof PreparedStatement ? PreparedStatement.class : Statement.class;
    return proxy(
        type,
        (method, arguments) -> {
          Object result =
              method.getName().startsWith("execute")
                  ? timed(delegate, method, arguments)
                  : invoke(delegate, method, arguments);
          return result instanceof ResultSet resultSet ? resultSet(resultSet) : result;
        });
  }

  private ResultSet resultSet(ResultSet delegate) {
    return proxy(
        ResultSet.class,
        (method, arguments) -> {
          Object result = invoke(delegate, method, arguments);
          if (method.getName().equals("next") && Boolean.TRUE.equals(result)) row();
          return result;
        });
  }

  private Object timed(Object delegate, Method method, Object[] arguments) throws Throwable {
    long started = System.nanoTime();
    boolean failed = true;
    try {
      Object result = invoke(delegate, method, arguments);
      failed = false;
      return result;
    } finally {
      completed(method.getName(), System.nanoTime() - started, failed);
    }
  }

  private static Object invoke(Object delegate, Method method, Object[] arguments)
      throws Throwable {
    try {
      return method.invoke(delegate, arguments);
    } catch (InvocationTargetException exception) {
      throw exception.getCause();
    }
  }

  private static <T> T proxy(Class<T> type, Invocation invocation) {
    return type.cast(
        Proxy.newProxyInstance(
            JdbcMeasurement.class.getClassLoader(),
            new Class<?>[] {type},
            (self, method, arguments) -> {
              if (method.getName().equals("unwrap") && ((Class<?>) arguments[0]).isInstance(self)) {
                return self;
              }
              if (method.getName().equals("isWrapperFor")
                  && ((Class<?>) arguments[0]).isInstance(self)) return true;
              return invocation.invoke(method, arguments);
            }));
  }

  @FunctionalInterface
  private interface Invocation {
    Object invoke(Method method, Object[] arguments) throws Throwable;
  }

  private final class MeasuredDataSource extends DelegatingDataSource implements AutoCloseable {
    MeasuredDataSource(DataSource delegate) {
      super(delegate);
    }

    @Override
    public Connection getConnection() throws SQLException {
      return connection(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
      return connection(super.getConnection(username, password));
    }

    @Override
    public void close() throws Exception {
      if (getTargetDataSource() instanceof AutoCloseable closeable) closeable.close();
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  public static class Configuration {
    @Bean
    static JdbcMeasurement jdbcMeasurement() {
      return new JdbcMeasurement();
    }

    @Bean
    static BeanPostProcessor measuredDataSources(JdbcMeasurement measurement) {
      return new BeanPostProcessor() {
        @Override
        public Object postProcessAfterInitialization(Object bean, String name) {
          return bean instanceof DataSource source ? measurement.wrap(source) : bean;
        }
      };
    }
  }
}
