package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerReconciliationService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationFingerprint;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutation;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntry;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupStateSnapshotFactory;
import com.greenhouse.backend.farm.orchid.domain.OrchidGroup;
import com.greenhouse.backend.farm.orchid.repository.OrchidGroupRepository;
import com.greenhouse.backend.support.OrchidGroupLedgerTestFixture;
import com.greenhouse.backend.work.effect.application.WorkOrchidGroupLedgerRehearsalInspector;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("work-e2e")
class LedgerReconciliationStreamPostgresE2ETest extends WorkE2ETestBase {
  private static final long BASE = 92000000;
  private static final LocalDate DATE = LocalDate.of(2026, 8, 20);
  @Autowired WorkTestDataSeeder seeder;
  @Autowired JdbcTemplate jdbc;
  @Autowired OrchidGroupRepository groups;
  @Autowired OrchidGroupLedgerTestFixture ledgerFixture;
  @Autowired OrchidGroupLedgerReconciliationService reconciliation;
  @Autowired OrchidGroupMutationFingerprint fingerprint;
  @Autowired EntityManagerFactory emf;
  @Autowired TransactionTemplate transactions;
  @MockitoSpyBean WorkOrchidGroupLedgerRehearsalInspector workInspector;
  private long group;
  private UUID cutover;

  @BeforeEach
  void setup() {
    reset(workInspector);
    seeder.reset();
    group = seeder.seedContractScenario().orchidGroupId();
    jdbc.update("update orchid_groups set quantity=0 where id=?", group);
    cutover = UUID.randomUUID();
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 500, 501, 5001})
  void readsLongChainsWithoutLoadingEntitiesAndKeepsLegacyFingerprints(int revisions) {
    importBaseline();
    extendChain(revisions);
    var expected = legacyFingerprints();
    var stats = emf.unwrap(SessionFactory.class).getStatistics();
    stats.clear();
    var report = reconciliation.reconcile();
    assertThat(report.ready()).isTrue();
    assertThat(report.issues()).isEmpty();
    assertThat(report.entryCount()).isEqualTo(revisions + 1);
    assertThat(report.baselineGroupCount()).isEqualTo(1);
    assertThat(report.baselineFingerprint()).isEqualTo(expected.get("baseline"));
    assertThat(report.currentStateFingerprint()).isEqualTo(expected.get("current"));
    for (var type :
        List.of(OrchidGroup.class, OrchidGroupMutationEntry.class, OrchidGroupMutation.class))
      assertThat(stats.getEntityStatistics(type.getName()).getLoadCount())
          .as(type.getSimpleName())
          .isZero();
    long queries = stats.getPrepareStatementCount();
    assertThat(queries)
        .as("query count independent of one group's revision history")
        .isLessThanOrEqualTo(24);
    jdbc.update(
        "update orchid_group_mutation_entries set before_state=jsonb_set(before_state,'{memo}','\"경계 오류\"'::jsonb) where orchid_group_id=? and state_revision_after=?",
        group,
        Math.min(revisions, 501));
    stats.clear();
    var corrupted = reconciliation.reconcile();
    assertThat(corrupted.ready()).isFalse();
    assertThat(corrupted.issues()).extracting("code").containsExactly("SNAPSHOT_CHAIN_MISMATCH");
    assertThat(stats.getPrepareStatementCount()).isEqualTo(queries);
  }

  @ParameterizedTest
  @ValueSource(ints = {500, 501})
  void scalarGroupBatchesKeepCountsAndFingerprintOrder(int count) {
    var original =
        jdbc.queryForObject(
            "select to_jsonb(g)::text from orchid_groups g where id=?", String.class, group);
    // Clone archived rows before import. No placement or downstream reference is attached.
    jdbc.update(
        "insert into orchid_groups select (jsonb_populate_record(null::orchid_groups, ?::jsonb || jsonb_build_object('id', ?+n,'sort_order',n))).* from generate_series(1,?) n",
        original,
        BASE,
        count - 1);
    importBaseline();
    var expected = legacyFingerprints();
    var stats = emf.unwrap(SessionFactory.class).getStatistics();
    stats.clear();
    var report = reconciliation.reconcile();
    assertThat(report.ready()).isTrue();
    assertThat(report.orchidGroupCount()).isEqualTo(count);
    assertThat(report.baselineGroupCount()).isEqualTo(count);
    assertThat(report.baselineFingerprint()).isEqualTo(expected.get("baseline"));
    assertThat(report.currentStateFingerprint()).isEqualTo(expected.get("current"));
    assertThat(stats.getEntityStatistics(OrchidGroup.class.getName()).getLoadCount()).isZero();
    assertThat(stats.getEntityStatistics(OrchidGroupMutationEntry.class.getName()).getLoadCount())
        .isZero();
  }

  @Test
  void validatesDeletedChainsAcrossFetchBoundariesAndDetectsMissingTombstones() {
    importBaseline();
    extendChain(1001);
    jdbc.update("delete from orchid_groups where id=?", group);
    var missing = reconciliation.reconcile();
    assertThat(missing.issues()).extracting("code").containsExactly("MISSING_DELETE_TOMBSTONE");
    jdbc.update(
        "update orchid_group_mutation_entries set entry_kind='DELETE',after_state=null where orchid_group_id=? and state_revision_after=1001",
        group);
    assertThat(reconciliation.reconcile().ready()).isTrue();
    jdbc.update(
        "update orchid_group_mutation_entries set before_state=jsonb_set(before_state,'{memo}','\"삭제 경계 오류\"'::jsonb) where orchid_group_id=? and state_revision_after=501",
        group);
    assertThat(reconciliation.reconcile().issues())
        .extracting("code")
        .containsExactly("SNAPSHOT_CHAIN_MISMATCH");
  }

  @Test
  void rootReconciliationReadsOneRepeatableSnapshotDuringConcurrentChanges() throws Exception {
    importBaseline();
    var before = reconciliation.reconcile();
    doAnswer(
            call -> {
              assertThat(
                      jdbc.queryForObject(
                          "select current_setting('transaction_isolation')", String.class))
                  .isEqualTo("repeatable read");
              try (var executor = Executors.newSingleThreadExecutor()) {
                executor
                    .submit(
                        () ->
                            transactions.execute(
                                tx -> {
                                  jdbc.update(
                                      "update orchid_groups set memo='동시 변경' where id=?", group);
                                  jdbc.update(
                                      "update orchid_group_mutation_entries set after_state=jsonb_set(after_state,'{memo}','\"동시 변경\"'::jsonb) where orchid_group_id=?",
                                      group);
                                  return null;
                                }))
                    .get(10, TimeUnit.SECONDS);
              }
              return call.callRealMethod();
            })
        .when(workInspector)
        .inspect();
    var during = reconciliation.reconcile();
    assertThat(during.ready()).isTrue();
    assertThat(during.currentStateFingerprint()).isEqualTo(before.currentStateFingerprint());
    assertThat(during.baselineFingerprint()).isEqualTo(before.baselineFingerprint());
    reset(workInspector);
    var after = reconciliation.reconcile();
    assertThat(after.ready()).isTrue();
    assertThat(after.currentStateFingerprint()).isNotEqualTo(before.currentStateFingerprint());
  }

  private void importBaseline() {
    ledgerFixture.seedBaseline(cutover, DATE, "1.0.0");
  }

  private void extendChain(int count) {
    var snapshot =
        jdbc.queryForObject(
            "select after_state::text from orchid_group_mutation_entries where orchid_group_id=?",
            String.class,
            group);
    jdbc.update(
        "insert into orchid_group_mutations (id, mutation_type, source_domain, source_type, source_reference_id, source_operation_key, correlation_id, command_fingerprint, occurred_at, recorded_at, effective_business_date, schema_version) select ?+n,'UPDATE_DETAILS','FARM','BE036',n::text,n::text,?::uuid,repeat('a',64),now(),now(),?,1 from generate_series(1,?) n",
        BASE,
        cutover.toString(),
        DATE,
        count);
    jdbc.update(
        "insert into orchid_group_mutation_entries (id, mutation_id, orchid_group_id, entry_kind, role, state_revision_before, state_revision_after, before_state, after_state) select ?+n,?+n,?,'CHANGE','AFFECTED',n-1,n,?::jsonb,?::jsonb from generate_series(1,?) n",
        BASE,
        BASE,
        group,
        snapshot,
        snapshot,
        count);
    jdbc.update("update orchid_groups set state_revision=? where id=?", count, group);
  }

  private Map<String, String> legacyFingerprints() {
    var current =
        groups.findDetailsByIds(
            jdbc.queryForList("select id from orchid_groups order by id", Long.class));
    current = current.stream().sorted(Comparator.comparing(OrchidGroup::getId)).toList();
    var baselineEntries =
        current.stream()
            .map(
                g ->
                    Map.of(
                        "orchidGroupId",
                        g.getId(),
                        "snapshot",
                        OrchidGroupStateSnapshotFactory.from(g).canonical()))
            .toList();
    var currentEntries =
        current.stream()
            .map(
                g ->
                    Map.of(
                        "orchidGroupId",
                        g.getId(),
                        "stateRevision",
                        g.getStateRevision(),
                        "snapshot",
                        OrchidGroupStateSnapshotFactory.from(g).canonical()))
            .toList();
    return Map.of(
        "baseline",
        fingerprint.calculate(
            Map.of(
                "cutoverKey", cutover, "effectiveBusinessDate", DATE, "groups", baselineEntries)),
        "current",
        fingerprint.calculate(Map.of("groups", currentEntries)));
  }
}
