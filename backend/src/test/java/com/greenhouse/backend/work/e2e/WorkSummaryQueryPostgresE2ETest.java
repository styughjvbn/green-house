package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.target.WorkOperationTarget;
import jakarta.persistence.EntityManagerFactory;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("work-e2e")
class WorkSummaryQueryPostgresE2ETest extends WorkE2ETestBase {
  @Autowired WorkTestDataSeeder seeder;
  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManagerFactory emf;

  @ParameterizedTest
  @CsvSource({"20,1", "200,10", "2000,50"})
  void summaryDoesNotLoadTargetsOrChildrenAsFanOutGrows(int targets, int children)
      throws Exception {
    seeder.resetKeepingSequences();
    var scenario = seeder.seedBenchmark(10, targets);
    var roots = jdbc.queryForList("select id from work_operations order by id", Long.class);
    long firstId = scenario.firstOperationId();
    jdbc.update(
        """
        insert into work_operations (work_type_id, title, status, planned_start_date,
          source_scope_type, target_snapshot_at, version, created_at, updated_at,
          parent_operation_id, relation_type, details)
        select o.work_type_id, '연관 요약 회귀', 'CANCELED', o.planned_start_date,
          'NONE', o.target_snapshot_at, 0, o.created_at, o.updated_at, o.id,
          'MOVEMENT_DISCARD', jsonb_build_object('large', repeat('x', 2000))
        from work_operations o cross join generate_series(1, ?) s
        where o.parent_operation_id is null
        """,
        children);
    var inboundIds =
        jdbc.queryForList(
            """
        insert into inbound_records (created_at, updated_at, inbound_date, inbound_type,
          variety_id, status, estimated_quantity)
        select timestamp '2026-07-01', timestamp '2026-07-01', date '2026-07-01',
          'PRODUCT_POT', (select min(id) from varieties), 'PLACED', 50
        from generate_series(1, 2) returning id
        """,
            Long.class);
    var targetIds =
        jdbc.queryForList(
            "select id from work_operation_targets where work_operation_id = ? order by id limit 3",
            Long.class,
            firstId);
    for (int i = 0; i < 3; i++)
      jdbc.update(
          "update work_operation_targets set orchid_group_id = null, target_reference_type = 'INBOUND_RECORD', inbound_record_id = ? where id = ?",
          inboundIds.get(i % 2 == 0 ? 1 : 0),
          targetIds.get(i));
    jdbc.update(
        "update work_operation_targets set excluded_at = timestamp '2026-07-02', exclusion_reason = '이력 보존' where id = ?",
        targetIds.getFirst());
    long childId =
        jdbc.queryForObject(
            "select min(id) from work_operations where parent_operation_id = ?",
            Long.class,
            firstId);
    String receiptIds = objectMapper.writeValueAsString(List.of(firstId, roots.get(1), childId));
    jdbc.update(
        "insert into work_command_receipts (receipt_key, request_fingerprint, result_operation_ids, created_at) values ('summary-query', ?, ?::jsonb, timestamp '2026-07-01')",
        "a".repeat(64),
        receiptIds);
    for (long id : List.of(firstId, roots.get(1), childId))
      jdbc.update(
          "insert into work_command_receipt_memberships (receipt_key, operation_id) values ('summary-query', ?)",
          id);

    var stats = emf.unwrap(SessionFactory.class).getStatistics();
    stats.clear();
    var response =
        get(
            "/api/work-operations?view=ALL&size=10&keyword="
                + URLEncoder.encode("벤치마크 작업", StandardCharsets.UTF_8));
    long queries = stats.getPrepareStatementCount();
    long targetLoads =
        stats.getEntityStatistics(WorkOperationTarget.class.getName()).getLoadCount();
    long operationLoads = stats.getEntityStatistics(WorkOperation.class.getName()).getLoadCount();
    var report =
        Path.of("build/work-query-count/work-summary-" + targets + "-" + children + ".json");
    Files.createDirectories(report.getParent());
    objectMapper
        .writerWithDefaultPrettyPrinter()
        .writeValue(
            report.toFile(),
            Map.of(
                "preparedStatements",
                queries,
                "targetLoads",
                targetLoads,
                "operationLoads",
                operationLoads,
                "entityLoads",
                stats.getEntityLoadCount()));
    assertThat(response.status()).as(response.body().toString()).isEqualTo(200);
    assertThat(response.data().path("totalElements").asLong()).isEqualTo(10);
    assertThat(response.data().path("content")).hasSize(10);
    for (var operation : response.data().path("content")) {
      long id = operation.path("id").asLong();
      assertThat(operation.has("targets")).isFalse();
      assertThat(operation.path("progress").path("total").asInt())
          .isEqualTo(targets - (id == firstId ? 1 : 0));
      var relation = operation.path("relationSummary");
      assertThat(relation.path("hasLinkedOperations").asBoolean()).isTrue();
      assertThat(relation.path("linkedOperationCount").asInt()).isEqualTo(children);
      assertThat(relation.path("creationBatchSize").asInt())
          .isEqualTo(id == firstId || id == roots.get(1) ? 3 : 1);
      if (id == firstId) {
        assertThat(relation.path("originType").asText()).isEqualTo("INBOUND");
        var actualInboundIds = new ArrayList<Long>();
        relation.path("inboundRecordIds").forEach(idNode -> actualInboundIds.add(idNode.asLong()));
        assertThat(actualInboundIds).containsExactly(inboundIds.get(1), inboundIds.get(0));
      } else {
        assertThat(relation.path("originType").asText()).isEqualTo("WORK_MANAGEMENT");
        assertThat(relation.path("inboundRecordIds")).isEmpty();
      }
    }
    assertThat(queries).isLessThanOrEqualTo(9);
    assertThat(targetLoads).as("summary must not load target snapshots").isZero();
    assertThat(operationLoads).as("only the root page is materialized").isLessThanOrEqualTo(10);
  }
}
