package com.greenhouse.backend.farm.mutation.ledger.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OrchidGroupWriteFenceRepository {

  private static final String CONTEXT_SETTING = "greenhouse.orchid_group_mutation";

  private final JdbcTemplate jdbcTemplate;

  private volatile Boolean postgresqlDatabase;

  public void authorizeMutation(Long mutationId) {
    if (mutationId == null) {
      throw new IllegalArgumentException("Write fence에 전달할 Mutation ID가 필요합니다.");
    }
    setTransactionContext("MUTATION:" + mutationId);
  }

  private void setTransactionContext(String context) {
    if (isPostgresqlDatabase()) {
      jdbcTemplate.queryForObject(
          "SELECT set_config(?, ?, TRUE)", String.class, CONTEXT_SETTING, context);
    }
  }

  private boolean isPostgresqlDatabase() {
    Boolean cached = postgresqlDatabase;
    if (cached != null) {
      return cached;
    }
    Boolean detected =
        jdbcTemplate.execute(
            (ConnectionCallback<Boolean>)
                connection ->
                    connection.getMetaData().getDatabaseProductName().startsWith("PostgreSQL"));
    postgresqlDatabase = Boolean.TRUE.equals(detected);
    return postgresqlDatabase;
  }
}
