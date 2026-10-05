package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

final class PostgresWriteTestSupport {
  private PostgresWriteTestSupport() {}

  static void assertStandaloneCheckFailure(ThrowingCallable action, String constraint) {
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    Throwable failure = catchThrowable(action);
    assertThat(failure).isInstanceOf(DataIntegrityViolationException.class);
    Throwable root = failure;
    while (root.getCause() != null) root = root.getCause();
    assertThat(root)
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("\"" + constraint + "\"");
    assertThat(((SQLException) root).getSQLState()).isEqualTo("23514");
  }

  // Read committed rows through a fresh transaction, independent of the writer's persistence
  // context.
  static Map<String, List<String>> snapshot(
      JdbcTemplate jdbc, PlatformTransactionManager manager, List<String> tables) {
    var transaction = new TransactionTemplate(manager);
    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    transaction.setReadOnly(true);
    return transaction.execute(
        status -> {
          var rows = new LinkedHashMap<String, List<String>>();
          for (String table : tables) {
            if (!table.matches("[a-z_]+")) throw new IllegalArgumentException("Invalid test table");
            rows.put(
                table,
                jdbc.queryForList(
                    "SELECT to_jsonb(row)::text FROM " + table + " row ORDER BY 1", String.class));
          }
          return rows;
        });
  }
}
