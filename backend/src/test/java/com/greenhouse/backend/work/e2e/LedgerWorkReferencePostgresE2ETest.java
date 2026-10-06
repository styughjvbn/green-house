package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerReconciliationService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupStateChainMigrationService;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutation;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.partner.repository.BusinessPartnerRepository;
import com.greenhouse.backend.support.JdbcMeasurement;
import com.greenhouse.backend.work.application.effect.WorkOrchidGroupLedgerRehearsalInspector;
import com.greenhouse.backend.work.domain.correction.WorkOperationCorrection;
import jakarta.persistence.EntityManagerFactory;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("work-e2e")
@Import({JdbcMeasurement.Configuration.class, QueryShapeCapture.Configuration.class})
class LedgerWorkReferencePostgresE2ETest extends WorkE2ETestBase {
  @Autowired WorkTestDataSeeder seeder;
  @Autowired JdbcTemplate jdbc;
  @Autowired BusinessPartnerRepository partners;
  @Autowired OrchidGroupRepository groups;
  @Autowired OrchidGroupStateChainMigrationService migration;
  @Autowired OrchidGroupLedgerReconciliationService reconciliation;
  @Autowired EntityManagerFactory emf;
  @Autowired JdbcMeasurement measurement;
  @Autowired QueryShapeCapture shapes;
  @Autowired TransactionTemplate transactions;
  @MockitoSpyBean WorkOrchidGroupLedgerRehearsalInspector inspector;

  private void fixture(int count) {
    reset(inspector);
    new DomainPerformanceFixture(jdbc, seeder, partners, groups, migration)
        .ledger(1, count, true, false);
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 499, 500, 501, 5000})
  void referencesUseScalarBatchesWithBoundedParametersAndNoEntityAccumulation(int count) {
    fixture(count);
    var stats = emf.unwrap(SessionFactory.class).getStatistics();
    stats.clear();
    measurement.start();
    shapes.start();
    var report = reconciliation.reconcile();
    var sql = shapes.stop();
    var jdbcSample = measurement.stop();
    assertThat(report.ready()).isTrue();
    assertThat(report.entryCount()).isEqualTo(count + 1);
    assertThat(report.issues()).isEmpty();
    assertThat(stats.getEntityStatistics(WorkOperationCorrection.class.getName()).getLoadCount())
        .isZero();
    assertThat(stats.getEntityStatistics(OrchidGroupMutation.class.getName()).getLoadCount())
        .isZero();
    assertThat(QueryShapeCapture.maxParameters(sql)).isLessThanOrEqualTo(512);
    assertThat(jdbcSample.executions()).isLessThanOrEqualTo(20 + 2 * ((count + 499) / 500));
    assertThat(jdbcSample.rows()).isLessThanOrEqualTo(3L * count + 20);
    System.out.printf(
        "[ledger-work-references] corrections=%d executions=%d rows=%d entityLoads=%d maxParameters=%d%n",
        count,
        jdbcSample.executions(),
        jdbcSample.rows(),
        stats.getEntityLoadCount(),
        QueryShapeCapture.maxParameters(sql));
    var references = inspector.inspect().corrections();
    assertThat(references).hasSize(count);
    assertThat(references).allSatisfy(row -> assertThat(row.orchidGroupIds()).hasSize(1));
    assertThat(references).extracting(row -> row.id()).isSorted();
  }

  @Test
  void missingAndInvalidLinksKeepIssueOrderAcrossBatchBoundaryAndLegacyDateOnlyResults() {
    fixture(502);
    var before = reconciliation.reconcile();
    var ids =
        jdbc.queryForList("select id from work_operation_corrections order by id", Long.class);
    // Preserve dangling/missing references and invalid provenance, without breaking SQL
    // constraints.
    jdbc.update(
        "update work_operation_corrections set mutation_id=null,correlation_id=null where id=?",
        ids.get(499));
    var baselineMutation =
        jdbc.queryForObject(
            "select mutation_id from orchid_group_mutation_entries where entry_kind='BASELINE'",
            Long.class);
    jdbc.update(
        "update work_operation_corrections set mutation_id=? where id=?",
        baselineMutation,
        ids.get(500));
    jdbc.update(
        "update work_operation_corrections set result_details=jsonb_set(result_details,'{adjustments,0,orchidGroupId}','999999999'::jsonb) where id=?",
        ids.get(500));
    jdbc.update(
        "update work_operation_corrections set mutation_id=null,correlation_id=null,result_details='{}'::jsonb where id=?",
        ids.get(501));
    var after = reconciliation.reconcile();
    assertThat(after.ready()).isFalse();
    assertThat(after.issues())
        .extracting("code")
        .containsExactly(
            "INVALID_WORK_CORRECTION_MUTATION_LINK",
            "DANGLING_WORK_CORRECTION_GROUP",
            "INVALID_WORK_CORRECTION_MUTATION_LINK");
    assertThat(after.issues())
        .extracting("referenceId")
        .containsExactly(ids.get(499).toString(), ids.get(500).toString(), ids.get(500).toString());
    assertThat(after.baselineFingerprint()).isEqualTo(before.baselineFingerprint());
    assertThat(after.currentStateFingerprint()).isEqualTo(before.currentStateFingerprint());
  }

  @Test
  void legacyDateOnlyCorrectionsNeedNoMutationLookupAndRemainValidAcrossBatches() {
    fixture(501);
    jdbc.update(
        """
        update work_operation_corrections set mutation_id=null,correlation_id=null,
        result_details='{"beforeWorkDate":"2026-08-20","afterWorkDate":"2026-08-21"}'::jsonb
        """);
    var references = inspector.inspect().corrections();
    assertThat(references)
        .hasSize(501)
        .allSatisfy(
            row -> {
              assertThat(row.mutationId()).isNull();
              assertThat(row.changesGroups()).isFalse();
              assertThat(row.orchidGroupIds()).isEmpty();
            });
    measurement.start();
    var report = reconciliation.reconcile();
    var sample = measurement.stop();
    assertThat(report.ready()).isTrue();
    assertThat(report.issues()).isEmpty();
    assertThat(sample.executions()).isLessThanOrEqualTo(22);
  }

  @Test
  void correctionAndMutationProjectionsShareTheRootRepeatableSnapshot() throws Exception {
    fixture(501);
    var before = reconciliation.reconcile();
    doAnswer(
            call -> {
              var captured = call.callRealMethod();
              try (var executor = Executors.newSingleThreadExecutor()) {
                executor
                    .submit(
                        () ->
                            transactions.execute(
                                tx -> {
                                  jdbc.update(
                                      "update work_operation_corrections set correlation_id=gen_random_uuid()");
                                  jdbc.update(
                                      "update orchid_group_mutations set source_type='BROKEN' where source_type='WORK_CORRECTION'");
                                  return null;
                                }))
                    .get(10, TimeUnit.SECONDS);
              }
              return captured;
            })
        .when(inspector)
        .inspect();
    var during = reconciliation.reconcile();
    assertThat(during.ready()).isTrue();
    assertThat(during.baselineFingerprint()).isEqualTo(before.baselineFingerprint());
    assertThat(during.currentStateFingerprint()).isEqualTo(before.currentStateFingerprint());
    reset(inspector);
    var after = reconciliation.reconcile();
    assertThat(after.issues())
        .hasSize(501)
        .extracting("code")
        .containsOnly("INVALID_WORK_CORRECTION_MUTATION_LINK");
  }
}
