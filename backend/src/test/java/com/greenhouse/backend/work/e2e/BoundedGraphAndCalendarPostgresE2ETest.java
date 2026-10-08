package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationGraphQueryService;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationRelation;
import com.greenhouse.backend.farm.domain.transformation.OrchidGroupLineage;
import com.greenhouse.backend.work.correction.domain.WorkOperationCorrection;
import com.greenhouse.backend.work.effect.domain.WorkAppliedEffect;
import com.greenhouse.backend.work.operation.application.WorkOperationGraphQueryService;
import com.greenhouse.backend.work.operation.domain.WorkOperation;
import com.greenhouse.backend.work.operation.web.dto.WorkOperationGraphDetail;
import com.greenhouse.backend.work.target.domain.WorkOperationTarget;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("work-e2e")
@Import(QueryShapeCapture.Configuration.class)
class BoundedGraphAndCalendarPostgresE2ETest extends WorkE2ETestBase {
  @Autowired WorkTestDataSeeder seeder;
  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManagerFactory emf;
  @Autowired WorkOperationGraphQueryService workGraph;
  @Autowired OrchidGroupMutationGraphQueryService mutationGraph;
  @Autowired QueryShapeCapture capture;

  @ParameterizedTest
  @ValueSource(ints = {999, 1000, 1001, 5001})
  void workGraphBoundsTargetLoadingAndSignalsIncompleteReferences(int targets) {
    seeder.resetKeepingSequences();
    var fixture = seeder.seedBenchmark(1, targets);
    var stats = statistics();
    capture.start();
    var response = workGraph.get(fixture.firstOperationId(), WorkOperationGraphDetail.WORK, 0, 10);
    assertThat(capture.stop()).hasSizeLessThanOrEqualTo(5);
    assertThat(response.truncated()).isEqualTo(targets > 1000);
    assertThat(response.nodes()).hasSize(2);
    var operation =
        response.nodes().stream()
            .filter(n -> n.workOperationId() != null)
            .findFirst()
            .orElseThrow();
    assertThat(operation.orchidGroupIds()).hasSize(Math.min(targets, 1000));
    assertThat(stats.getEntityStatistics(WorkOperationTarget.class.getName()).getLoadCount())
        .isLessThanOrEqualTo(Math.min(targets, 1001));
    assertEdges(
        response.nodes().stream().map(n -> n.id()).collect(Collectors.toSet()),
        response.edges().stream().map(e -> List.of(e.sourceNodeId(), e.targetNodeId())).toList());
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 50, 5001})
  void workGraphBoundsChildrenBeforeLoadingTheirReferences(int children) {
    seeder.resetKeepingSequences();
    var fixture = seeder.seedBenchmark(1, 1);
    jdbc.update(
        """
        insert into work_operations (work_type_id, title, status, planned_start_date,
          source_scope_type, target_snapshot_at, version, created_at, updated_at,
          parent_operation_id, relation_type)
        select work_type_id, '자식', 'CANCELED', planned_start_date,
          'NONE', target_snapshot_at, 0, created_at, updated_at, id, 'MOVEMENT_DISCARD'
        from work_operations cross join generate_series(1, ?) where id = ?
        """,
        children,
        fixture.firstOperationId());
    var stats = statistics();
    var response = workGraph.get(fixture.firstOperationId(), WorkOperationGraphDetail.WORK, 0, 10);
    assertThat(response.nodes()).hasSize(Math.min(children + 2, 10));
    assertThat(response.truncated()).isEqualTo(children + 2 > 10);
    assertThat(stats.getEntityStatistics(WorkOperation.class.getName()).getLoadCount())
        .isLessThanOrEqualTo(12);
    assertEdges(
        response.nodes().stream().map(n -> n.id()).collect(Collectors.toSet()),
        response.edges().stream().map(e -> List.of(e.sourceNodeId(), e.targetNodeId())).toList());
  }

  @ParameterizedTest
  @ValueSource(ints = {1000, 1001, 5001})
  void calendarReturnsCompleteResultsOrAnExplicitLimitError(int rows) throws Exception {
    seeder.resetKeepingSequences();
    seeder.seedBenchmark(rows, 1);
    var stats = statistics();
    var response = get("/api/work-operations/calendar?from=2026-07-01&to=2026-07-31");
    assertThat(response.status()).isEqualTo(rows <= 1000 ? 200 : 422);
    assertThat(stats.getEntityStatistics(WorkOperation.class.getName()).getLoadCount())
        .isLessThanOrEqualTo(Math.min(rows, 1001));
    assertThat(stats.getEntityStatistics(WorkOperationTarget.class.getName()).getLoadCount())
        .isZero();
    if (rows <= 1000) assertThat(response.data()).hasSize(rows);
    else {
      assertThat(response.body().path("error").path("code").asText())
          .isEqualTo("QUERY_LIMIT_EXCEEDED");
      assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(2);
    }
  }

  @Test
  void calendarIncludesBothBoundaryDatesAndRejectsWiderRanges() throws Exception {
    seeder.resetKeepingSequences();
    var fixture = seeder.seedBenchmark(2, 1);
    jdbc.update(
        "update work_operations set planned_start_date = date '2026-01-01' where id = ?",
        fixture.firstOperationId());
    jdbc.update(
        "update work_operations set planned_start_date = date '2027-01-01' where id <> ?",
        fixture.firstOperationId());
    assertThat(get("/api/work-operations/calendar?from=2026-01-01&to=2027-01-01").data())
        .hasSize(2);
    var stats = statistics();
    assertThat(get("/api/work-operations/calendar?from=2026-01-01&to=2027-01-02").status())
        .isEqualTo(400);
    assertThat(stats.getPrepareStatementCount()).isZero();
  }

  @Test
  void legacyHistoryReturnsLatest500WhilePageHistoryPreservesTheWholeHistory() throws Exception {
    seeder.resetKeepingSequences();
    var fixture = seeder.seedBenchmark(501, 1);
    jdbc.update(
        "update work_operation_targets set orchid_group_id = ?", fixture.firstOrchidGroupId());
    var ids = jdbc.queryForList("select id from work_operations order by id desc", Long.class);
    var legacy = get("/api/orchid-groups/" + fixture.firstOrchidGroupId() + "/work-history");
    assertThat(legacy.status()).isEqualTo(200);
    assertThat(legacy.data()).hasSize(500);
    for (int i = 0; i < 500; i++)
      assertThat(legacy.data().get(i).path("workOperationId").asLong()).isEqualTo(ids.get(i));
    var page =
        get(
            "/api/work-history?historyScopeType=ORCHID_GROUP&historyScopeId="
                + fixture.firstOrchidGroupId()
                + "&page=5&size=100");
    assertThat(page.status()).isEqualTo(200);
    assertThat(page.data().path("totalElements").asLong()).isEqualTo(501);
    assertThat(page.data().path("content")).hasSize(1);
    assertThat(page.data().path("content").get(0).path("workOperationId").asLong())
        .isEqualTo(ids.getLast());
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 100, 5001})
  void mutationGraphDoesNotLoadRelationsOrMutationsOutsideVisibleNodes(int outside) {
    seeder.resetKeepingSequences();
    var fixture = seeder.seedBenchmark(1, 1);
    var visible = seedMutations(2, "visible");
    seedChain(visible, fixture.firstOrchidGroupId());
    var external = seedMutations(outside, "outside");
    jdbc.update(
        "insert into orchid_group_mutation_relations (mutation_id, related_mutation_id, relation_type) values (?, ?, 'CORRECTS')",
        visible.get(0),
        visible.get(1));
    for (var id : external)
      jdbc.update(
          "insert into orchid_group_mutation_relations (mutation_id, related_mutation_id, relation_type) values (?, ?, 'CORRECTS')",
          id,
          visible.get(0));
    var stats = statistics();
    capture.start();
    var graph = mutationGraph.getGraph(fixture.firstOrchidGroupId(), 0, 10);
    assertThat(capture.stop()).hasSizeLessThanOrEqualTo(3);
    assertThat(graph.truncated()).isFalse();
    assertThat(graph.nodes()).hasSize(4);
    assertThat(
            stats.getEntityStatistics(OrchidGroupMutationRelation.class.getName()).getLoadCount())
        .isEqualTo(1);
    assertThat(graph.edges().stream().filter(e -> e.id().startsWith("mutation-relation-")))
        .hasSize(1);
  }

  @Test
  void denseVisibleRelationsSignalTruncationAndKeepEveryEdgeEndpointVisible() {
    seeder.resetKeepingSequences();
    var fixture = seeder.seedBenchmark(1, 1);
    var visible = seedMutations(12, "dense");
    seedChain(visible, fixture.firstOrchidGroupId());
    jdbc.update(
        """
        insert into orchid_group_mutation_relations (mutation_id, related_mutation_id, relation_type)
        select a.id, b.id, 'CORRECTS' from orchid_group_mutations a
          cross join orchid_group_mutations b where a.id <> b.id
        """);
    var stats = statistics();
    var graph = mutationGraph.getGraph(fixture.firstOrchidGroupId(), 0, 30);
    assertThat(graph.truncated()).isTrue();
    assertThat(graph.nodes()).hasSize(24);
    assertThat(
            stats.getEntityStatistics(OrchidGroupMutationRelation.class.getName()).getLoadCount())
        .isLessThanOrEqualTo(121);
    assertThat(graph.edges().stream().filter(e -> e.id().startsWith("mutation-relation-")))
        .hasSize(120);
    assertEdges(
        graph.nodes().stream().map(n -> n.id()).collect(Collectors.toSet()),
        graph.edges().stream().map(e -> List.of(e.sourceNodeId(), e.targetNodeId())).toList());
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 100, 5001})
  void lineageLabelsLoadOneScalarPerVisibleResultInsteadOfAllSourceEntities(int sources) {
    seeder.resetKeepingSequences();
    var fixture = seeder.seedBenchmark(1, sources + 1);
    var mutations = seedMutations(1, "lineage");
    seedChain(mutations, fixture.firstOrchidGroupId());
    jdbc.update("update orchid_group_mutation_entries set role = 'RESULT'");
    jdbc.update(
        """
        insert into orchid_group_lineage (source_orchid_group_id, result_orchid_group_id,
          relation_type, work_operation_id, source_quantity, result_quantity, mutation_id, created_at)
        select id, ?, case when sort_order = 2 then 'SPLIT_TO' else 'REPOTTED_TO' end,
          ?, 1, 1, ?, current_timestamp from orchid_groups where id <> ? order by id
        """,
        fixture.firstOrchidGroupId(),
        fixture.firstOperationId(),
        mutations.getFirst(),
        fixture.firstOrchidGroupId());
    var stats = statistics();
    var graph = mutationGraph.getGraph(fixture.firstOrchidGroupId(), 0, 10);
    assertThat(graph.truncated()).isFalse();
    assertThat(graph.edges().getFirst().lineageRelationType().name()).isEqualTo("SPLIT_TO");
    assertThat(stats.getEntityStatistics(OrchidGroupLineage.class.getName()).getLoadCount())
        .isZero();
    assertThat(stats.getEntityLoadCount()).isLessThanOrEqualTo(2);
  }

  @ParameterizedTest
  @CsvSource({"EFFECT,1001", "EFFECT,5001", "CORRECTION,1001", "CORRECTION,5001"})
  void workGraphBoundsMutationReferencesBeforeFarmExpansion(String kind, int rows) {
    seeder.resetKeepingSequences();
    var fixture = seeder.seedBenchmark(1, 1);
    seedMutations(rows, "refs");
    jdbc.update(
        """
        insert into orchid_group_mutation_entries (mutation_id, orchid_group_id, entry_kind,
          role, state_revision_before, state_revision_after, before_state, after_state)
        select id, ?, case when n = 1 then 'BASELINE' else 'CHANGE' end,
          'AFFECTED', case when n = 1 then null else n - 2 end, n - 1,
          case when n = 1 then null else '{}'::jsonb end, '{}'::jsonb
        from (select id, row_number() over (order by id) n from orchid_group_mutations) chain
        """,
        fixture.firstOrchidGroupId());
    if (kind.equals("EFFECT"))
      jdbc.update(
          """
        insert into work_applied_effects (work_operation_id, effect_key, effect_kind,
          handler_code, applied_at, created_at, updated_at, mutation_id, correlation_id,
          command_details, result_details)
        select ?, 'ref-' || id, 'ATTRIBUTE_CHANGE', 'BE034', current_timestamp,
          current_timestamp, current_timestamp, id, correlation_id,
          jsonb_build_object('large', repeat('x', 2000)), '{}'::jsonb from orchid_group_mutations
        """,
          fixture.firstOperationId());
    else
      jdbc.update(
          """
        insert into work_operation_corrections (original_work_operation_id, reason,
          created_at, result_details, mutation_id, correlation_id)
        select ?, '보정', current_timestamp, jsonb_build_object('large', repeat('x', 2000)),
          id, correlation_id from orchid_group_mutations
        """,
          fixture.firstOperationId());
    var stats = statistics();
    capture.start();
    var response =
        workGraph.get(fixture.firstOperationId(), WorkOperationGraphDetail.MUTATION, 0, 10);
    assertThat(response.truncated()).isTrue();
    assertThat(response.nodes()).hasSizeLessThanOrEqualTo(10);
    assertThat(
            stats
                .getEntityStatistics(
                    (kind.equals("EFFECT")
                            ? WorkAppliedEffect.class
                            : WorkOperationCorrection.class)
                        .getName())
                .getLoadCount())
        .isLessThanOrEqualTo(1001);
    assertThat(capture.stop()).hasSizeLessThanOrEqualTo(8);
    assertEdges(
        response.nodes().stream().map(n -> n.id()).collect(Collectors.toSet()),
        response.edges().stream().map(e -> List.of(e.sourceNodeId(), e.targetNodeId())).toList());
  }

  private Statistics statistics() {
    var stats = emf.unwrap(SessionFactory.class).getStatistics();
    stats.clear();
    return stats;
  }

  private List<Long> seedMutations(int count, String prefix) {
    return jdbc.queryForList(
        """
        insert into orchid_group_mutations (mutation_type, source_domain, source_type,
          source_reference_id, source_operation_key, correlation_id, command_fingerprint,
          occurred_at, recorded_at, effective_business_date, schema_version)
        select 'MOVE', 'FARM', 'BE034', ? || n, ? || n,
          '00000000-0000-0000-0000-000000000034'::uuid, repeat('a', 64),
          timestamp '2026-07-15', timestamp '2026-07-15', date '2026-07-15', 1
        from generate_series(1, ?) n returning id
        """,
        Long.class,
        prefix,
        prefix,
        count);
  }

  private void seedChain(List<Long> mutations, long group) {
    for (int i = 0; i < mutations.size(); i++)
      jdbc.update(
          """
        insert into orchid_group_mutation_entries (mutation_id, orchid_group_id, entry_kind,
          role, state_revision_before, state_revision_after, before_state, after_state)
        values (?, ?, ?, 'AFFECTED', ?, ?, ?::jsonb, '{}'::jsonb)
        """,
          mutations.get(i),
          group,
          i == 0 ? "BASELINE" : "CHANGE",
          i == 0 ? null : i - 1,
          i,
          i == 0 ? null : "{}");
  }

  private void assertEdges(Set<String> nodes, List<List<String>> endpoints) {
    endpoints.forEach(edge -> assertThat(nodes).containsAll(edge));
  }
}
