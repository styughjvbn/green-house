package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("work-e2e")
class WorkDetailQueryPostgresE2ETest extends WorkE2ETestBase {

  @Autowired WorkTestDataSeeder seeder;

  @Autowired JdbcTemplate jdbc;

  @Autowired EntityManagerFactory entityManagerFactory;

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 10, 50})
  void correctionDetailsUseBoundedQueriesAndKeepMissingHistory(int count) throws Exception {
    seeder.reset();
    List<Long> operations =
        jdbc.queryForList(
            """
				INSERT INTO work_operations (work_type_id, title, status, planned_start_date,
				 source_scope_type, target_snapshot_at, worker, version, created_at, updated_at)
				SELECT (SELECT id FROM work_types WHERE code = 'REPOT'), '상세 ' || n, 'COMPLETED',
				 DATE '2026-08-20', 'MANUAL_SELECTION', TIMESTAMP '2026-08-20 00:00:00', '담당자', 0,
				 TIMESTAMP '2026-08-20 00:00:00', TIMESTAMP '2026-08-20 00:00:00'
				FROM generate_series(0, ?) n RETURNING id
				""",
            Long.class,
            count);
    for (int index = 1; index <= count; index++) {
      jdbc.update(
          """
					INSERT INTO work_operation_corrections
					(original_work_operation_id, reason, worker, result_details, created_at)
					VALUES (?, ?, '담당자', CAST(? AS jsonb), TIMESTAMP '2026-08-20 00:00:00')
					""",
          operations.getFirst(),
          "보정 " + index,
          index >= count - 1
              ? "{}"
              : """
					{"adjustments":[{"orchidGroupId":99,"beforeQuantity":12,"afterQuantity":10,
					 "beforeStatus":"관리","afterStatus":"정상"}]}
					""");
    }
    var stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    stats.clear();
    var result = get("/api/work-operations/" + operations.getFirst() + "/details");
    assertThat(result.status()).as(result.body().toString()).isEqualTo(200);
    assertThat(stats.getPrepareStatementCount()).isEqualTo(4);
    var corrections = result.data().path("corrections");
    assertThat(corrections).hasSize(count);
    for (int index = 0; index < count; index++) {
      assertThat(corrections.get(index).path("reason").asText()).isEqualTo("보정 " + (index + 1));
      assertThat(corrections.get(index).path("adjustments")).hasSize(index >= count - 2 ? 0 : 1);
    }
  }
}
