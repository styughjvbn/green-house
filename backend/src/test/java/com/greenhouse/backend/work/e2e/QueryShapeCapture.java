package com.greenhouse.backend.work.e2e;

import java.util.ArrayList;
import java.util.List;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Captures SQL shape only during measurements, without recording parameter values. */
class QueryShapeCapture implements StatementInspector {
  private final List<String> statements = new ArrayList<>();
  private boolean enabled;

  synchronized void start() {
    statements.clear();
    enabled = true;
  }

  synchronized List<String> stop() {
    enabled = false;
    return List.copyOf(statements);
  }

  static long maxParameters(List<String> statements) {
    return statements.stream()
        .mapToLong(sql -> sql.chars().filter(c -> c == '?').count())
        .max()
        .orElse(0);
  }

  @Override
  public synchronized String inspect(String sql) {
    if (enabled) statements.add(sql);
    return sql;
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class Configuration {
    @Bean
    QueryShapeCapture queryShapeCapture() {
      return new QueryShapeCapture();
    }

    @Bean
    HibernatePropertiesCustomizer queryShapeCustomizer(QueryShapeCapture capture) {
      return properties -> properties.put("hibernate.session_factory.statement_inspector", capture);
    }
  }
}
