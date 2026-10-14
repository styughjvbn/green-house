package com.greenhouse.backend.support;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/** JDBC observation windows, not application transaction or server lock-wait timings. */
public final class JdbcTransactionMeasurement {
  private static final Pattern ROW_LOCK =
      Pattern.compile(
          "\\bfor\\s+(?:(?:no\\s+key|key)\\s+)?(?:update|share)\\b", Pattern.CASE_INSENSITIVE);
  private boolean active;
  private long generation;
  private long started, completed, rollbacks, implicitCommits, closedWithoutCompletion;
  private long totalNanos, maximumNanos, lockedTransactions, totalLockNanos, maximumLockNanos;
  private long lockingStatements, lockingStatementNanos, maximumLockingStatementNanos;

  public synchronized void start() {
    if (active) throw new IllegalStateException("A transaction measurement is already active");
    started = completed = rollbacks = implicitCommits = closedWithoutCompletion = 0;
    totalNanos = maximumNanos = lockedTransactions = totalLockNanos = maximumLockNanos = 0;
    lockingStatements = lockingStatementNanos = maximumLockingStatementNanos = 0;
    generation++;
    active = true;
  }

  public synchronized Sample stop() {
    if (!active) throw new IllegalStateException("No transaction measurement is active");
    active = false;
    return new Sample(
        started,
        completed,
        rollbacks,
        implicitCommits,
        closedWithoutCompletion,
        started - completed - closedWithoutCompletion,
        totalNanos,
        maximumNanos,
        lockedTransactions,
        totalLockNanos,
        maximumLockNanos,
        lockingStatements,
        lockingStatementNanos,
        maximumLockingStatementNanos);
  }

  public record Sample(
      long started,
      long completed,
      long rollbacks,
      long implicitCommits,
      long closedWithoutObservedCompletion,
      long openAtEnd,
      long totalTransactionNanos,
      long maximumTransactionNanos,
      long transactionsWithObservedRowLocks,
      long totalObservedLockHoldNanos,
      long maximumObservedLockHoldNanos,
      long lockingStatements,
      long totalLockingStatementNanos,
      long maximumLockingStatementNanos) {}

  private final class Window {
    long epoch = -1, begin, lock;
  }

  private synchronized void begin(Window window, long now) {
    if (!active) return;
    if (window.epoch != generation) {
      window.epoch = generation;
      window.begin = window.lock = 0;
    }
    if (window.begin == 0) {
      window.begin = now;
      started++;
    }
  }

  private synchronized void locked(Window window, long duration, long end) {
    if (!active || window.epoch != generation || window.begin == 0) return;
    lockingStatements++;
    lockingStatementNanos += duration;
    maximumLockingStatementNanos = Math.max(maximumLockingStatementNanos, duration);
    if (window.lock == 0) window.lock = end;
  }

  private synchronized void finish(Window window, String kind, long now) {
    if (!active || window.epoch != generation || window.begin == 0) return;
    if (kind.equals("close")) closedWithoutCompletion++;
    else {
      completed++;
      if (kind.equals("rollback")) rollbacks++;
      if (kind.equals("setAutoCommit")) implicitCommits++;
      long elapsed = now - window.begin;
      totalNanos += elapsed;
      maximumNanos = Math.max(maximumNanos, elapsed);
      if (window.lock != 0) {
        lockedTransactions++;
        long held = now - window.lock;
        totalLockNanos += held;
        maximumLockNanos = Math.max(maximumLockNanos, held);
      }
    }
    window.begin = window.lock = 0;
  }

  DataSource wrap(DataSource source) {
    return new TimedDataSource(source);
  }

  private final class TimedDataSource extends DelegatingDataSource implements AutoCloseable {
    TimedDataSource(DataSource source) {
      super(source);
    }

    @Override
    public Connection getConnection() throws SQLException {
      return connection(super.getConnection());
    }

    @Override
    public Connection getConnection(String user, String password) throws SQLException {
      return connection(super.getConnection(user, password));
    }

    @Override
    public void close() throws Exception {
      if (getTargetDataSource() instanceof AutoCloseable closeable) closeable.close();
    }
  }

  private Connection connection(Connection delegate) {
    var window = new Window();
    return proxy(
        Connection.class,
        (method, arguments) -> {
          Object value = invoke(delegate, method, arguments);
          String name = method.getName();
          if (name.equals("commit")
              || name.equals("rollback")
              || name.equals("close")
              || name.equals("setAutoCommit") && Boolean.TRUE.equals(arguments[0]))
            finish(window, name, System.nanoTime());
          if (value instanceof Statement statement) {
            String sql =
                arguments != null && arguments.length > 0 && arguments[0] instanceof String text
                    ? text
                    : null;
            Class<?> type = method.getReturnType();
            return proxy(
                type,
                (operation, parameters) -> {
                  if (!operation.getName().startsWith("execute"))
                    return invoke(statement, operation, parameters);
                  String actual =
                      parameters != null
                              && parameters.length > 0
                              && parameters[0] instanceof String text
                          ? text
                          : sql;
                  long start = System.nanoTime();
                  boolean transactional = !delegate.getAutoCommit();
                  if (transactional) begin(window, start);
                  Object result = invoke(statement, operation, parameters);
                  long end = System.nanoTime();
                  if (transactional && actual != null && ROW_LOCK.matcher(actual).find())
                    locked(window, end - start, end);
                  return result;
                });
          }
          return value;
        });
  }

  private static Object invoke(Object receiver, Method method, Object[] args) throws Throwable {
    try {
      return method.invoke(receiver, args);
    } catch (InvocationTargetException failure) {
      throw failure.getCause();
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> T proxy(Class<T> type, Invocation call) {
    return (T)
        Proxy.newProxyInstance(
            JdbcTransactionMeasurement.class.getClassLoader(),
            new Class<?>[] {type},
            (self, method, arguments) -> {
              if (method.getName().equals("unwrap") && ((Class<?>) arguments[0]).isInstance(self))
                return self;
              if (method.getName().equals("isWrapperFor")
                  && ((Class<?>) arguments[0]).isInstance(self)) return true;
              return call.invoke(method, arguments);
            });
  }

  @FunctionalInterface
  private interface Invocation {
    Object invoke(Method method, Object[] arguments) throws Throwable;
  }

  @TestConfiguration(proxyBeanMethods = false)
  public static class Configuration {
    @Bean
    static JdbcTransactionMeasurement transactionMeasurement() {
      return new JdbcTransactionMeasurement();
    }

    @Bean
    static BeanPostProcessor timedTransactions(JdbcTransactionMeasurement measurement) {
      return new BeanPostProcessor() {
        @Override
        public Object postProcessAfterInitialization(Object bean, String name) {
          return bean instanceof DataSource source ? measurement.wrap(source) : bean;
        }
      };
    }
  }
}
