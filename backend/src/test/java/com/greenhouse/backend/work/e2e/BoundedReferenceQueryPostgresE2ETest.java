package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.sales.application.partner.BusinessPartnerReader;
import com.greenhouse.backend.sales.domain.partner.PartnerTextMatch;
import com.greenhouse.backend.sales.domain.partner.PartnerTextSearch;
import com.greenhouse.backend.work.application.operation.WorkOperationMetricsReader;
import com.greenhouse.backend.work.repository.WorkEffectOrchidGroupRepository;
import com.greenhouse.backend.work.repository.WorkOperationRepository;
import com.greenhouse.backend.work.repository.WorkOperationTargetRepository;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("work-e2e")
@Import(QueryShapeCapture.Configuration.class)
class BoundedReferenceQueryPostgresE2ETest extends WorkE2ETestBase {
  @Autowired WorkTestDataSeeder seeder;
  @Autowired JdbcTemplate jdbc;
  @Autowired BusinessPartnerReader partners;
  @Autowired WorkOperationMetricsReader metrics;
  @Autowired WorkOperationRepository operations;
  @Autowired WorkOperationTargetRepository targets;
  @Autowired WorkEffectOrchidGroupRepository links;
  @Autowired TransactionTemplate transactions;
  @Autowired EntityManagerFactory emf;
  @Autowired QueryShapeCapture capture;

  @BeforeEach
  void reset() {
    seeder.resetKeepingSequences();
    jdbc.execute("truncate table business_partners continue identity cascade");
  }

  @ParameterizedTest
  @ValueSource(ints = {500, 501, 5001})
  void batchedTermsKeepSingleSearchSemanticsIncludingInactiveAndLiteralWildcards(int count) {
    seedPartners(count);
    var searches = new ArrayList<PartnerTextSearch>();
    searches.add(new PartnerTextSearch(PartnerTextMatch.CONTACT_CONTAINS, "contact"));
    searches.add(new PartnerTextSearch(PartnerTextMatch.CONTACT_CONTAINS, "010"));
    searches.add(new PartnerTextSearch(PartnerTextMatch.NAME_CONTAINS, "%_"));
    searches.add(new PartnerTextSearch(PartnerTextMatch.NAME_PREFIX, "market"));
    searches.add(new PartnerTextSearch(PartnerTextMatch.NAME_EXACT, "MARKET %_ 1"));
    searches.add(new PartnerTextSearch(PartnerTextMatch.NAME_CONTAINS, ""));
    for (int i = searches.size(); i < 33; i++) {
      searches.add(new PartnerTextSearch(PartnerTextMatch.NAME_PREFIX, "absent " + i));
    }
    var expected = new HashMap<PartnerTextSearch, List<Long>>();
    searches.forEach(
        term -> expected.put(term, partners.findMatchingIds(term.match(), term.value())));
    searches.add(searches.getFirst());
    var stats = emf.unwrap(SessionFactory.class).getStatistics();
    stats.clear();
    capture.start();
    var actual = partners.findMatchingIds(searches);
    var sql = capture.stop();
    assertThat(actual).isEqualTo(expected);
    assertThat(actual.get(searches.getFirst())).hasSize(count / 2);
    assertThat(actual.get(searches.get(2))).hasSize(count);
    assertThat(actual.get(searches.get(4))).containsExactly(1_000_001L);
    assertThat(stats.getPrepareStatementCount()).isEqualTo(count / 500 + 2);
    assertThat(QueryShapeCapture.maxParameters(sql)).isLessThanOrEqualTo(200);
    assertThatThrownBy(() -> actual.get(searches.getFirst()).add(1L))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(partners.findMatchingIds(List.of())).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 500, 1001})
  void fullPartnerValuesUseBoundedQueriesAndStillRejectMissingIds(int count) {
    seedPartners(count);
    var ids = jdbc.queryForList("select id from business_partners order by id", Long.class);
    var requested = new ArrayList<>(ids);
    if (!ids.isEmpty()) requested.add(ids.getFirst());
    var stats = emf.unwrap(SessionFactory.class).getStatistics();
    stats.clear();
    capture.start();
    var values = partners.getAllInfo(requested);
    var sql = capture.stop();
    assertThat(values.keySet()).containsExactlyInAnyOrderElementsOf(ids);
    assertThat(stats.getPrepareStatementCount()).isEqualTo((count + 499) / 500);
    assertThat(QueryShapeCapture.maxParameters(sql)).isLessThanOrEqualTo(500);
    if (!ids.isEmpty()) {
      var first = values.get(ids.getFirst());
      assertThat(first.name()).isEqualTo("Market %_ 1");
      assertThat(first.active()).isFalse();
      assertThat(first.ownerName()).isNull();
      assertThat(first.phone()).isNull();
    }
    requested.add(-1L);
    assertThatThrownBy(() -> partners.getAllInfo(requested)).isInstanceOf(NotFoundException.class);
  }

  @ParameterizedTest
  @ValueSource(ints = {500, 501, 5001})
  void historyArraysPreserveGlobalPageExcludedTargetsAndEffectOnlyMembership(int count) {
    var scenario = seeder.seedBenchmark(3, count);
    var ids = jdbc.queryForList("select id from orchid_groups order by id", Long.class);
    var roots = jdbc.queryForList("select id from work_operations order by id", Long.class);
    jdbc.update("update work_operations set status = 'COMPLETED'");
    jdbc.update(
        "update work_operation_targets set excluded_at = timestamp '2026-07-16', exclusion_reason = '회귀' where work_operation_id = ?",
        scenario.firstOperationId());
    jdbc.update(
        """
        insert into work_applied_effects (work_operation_id, effect_key, effect_kind,
          handler_code, applied_at, created_at, updated_at)
        values (?, 'array-history', 'RECORD_ONLY', 'PESTICIDE', timestamp '2026-07-15',
          timestamp '2026-07-15', timestamp '2026-07-15')
        """,
        scenario.firstOperationId());
    jdbc.update(
        """
        insert into work_effect_orchid_groups (work_applied_effect_id, orchid_group_id,
          relation_type, created_at)
        select id, ?, 'SOURCE', timestamp '2026-07-15' from work_applied_effects
        """,
        ids.getFirst());
    var stats = emf.unwrap(SessionFactory.class).getStatistics();
    stats.clear();
    capture.start();
    var latest = metrics.getLatestWorkDates(ids);
    var dateSql = capture.stop();
    assertThat(latest).hasSize(count * 2);
    assertThat(latest.values()).containsOnly(LocalDate.of(2026, 7, 15));
    assertThat(latest.keySet()).doesNotContainAnyElementsOf(ids.subList(0, count));
    assertThat(stats.getPrepareStatementCount()).isEqualTo((ids.size() + 499) / 500);
    assertThat(QueryShapeCapture.maxParameters(dateSql)).isLessThanOrEqualTo(502);

    capture.start();
    transactions.executeWithoutResult(
        tx -> {
          var sort = Sort.by(Sort.Direction.DESC, "plannedStartDate", "id");
          var first = operations.findHistoryPage(ids, PageRequest.of(0, 2, sort));
          var last = operations.findHistoryPage(ids, PageRequest.of(1, 2, sort));
          assertThat(first.getTotalElements()).isEqualTo(3);
          assertThat(first.getContent())
              .extracting(o -> o.getId())
              .containsExactly(roots.get(2), roots.get(1));
          assertThat(last.getTotalElements()).isEqualTo(3);
          assertThat(last.getContent())
              .extracting(o -> o.getId())
              .containsExactly(roots.getFirst());
          var historyTargets =
              targets
                  .findByOrchidGroupIdInAndExcludedAtIsNullOrderByWorkOperationPlannedStartDateDescWorkOperationIdDesc(
                      ids);
          assertThat(historyTargets).hasSize(count * 2);
          assertThat(historyTargets).allSatisfy(t -> assertThat(t.getExcludedAt()).isNull());
          var pageTargets =
              targets
                  .findByWorkOperationIdInAndOrchidGroupIdInAndExcludedAtIsNullOrderByWorkOperationIdAscIdAsc(
                      roots, ids);
          assertThat(pageTargets).hasSize(count * 2);
          assertThat(
                  links
                      .findByOrchidGroupIdInOrderByWorkAppliedEffectAppliedAtDescWorkAppliedEffectIdDesc(
                          ids))
              .singleElement()
              .satisfies(link -> assertThat(link.getOrchidGroupId()).isEqualTo(ids.getFirst()));
          assertThat(
                  links
                      .findByWorkAppliedEffectWorkOperationIdInAndOrchidGroupIdInOrderByWorkAppliedEffectWorkOperationIdAscIdAsc(
                          roots, ids))
              .hasSize(1);
          assertThat(operations.findHistoryPage(List.of(-1L), PageRequest.of(0, 2))).isEmpty();
        });
    assertThat(QueryShapeCapture.maxParameters(capture.stop())).isLessThanOrEqualTo(5);
  }

  private void seedPartners(int count) {
    jdbc.update(
        """
        insert into business_partners (id, name, partner_type, owner_name, phone, is_active, created_at, updated_at)
        select 1000000 + n, 'Market %_ ' || n, 'AUCTION_HOUSE',
          case when n % 2 = 0 then 'contact' else null end,
          case when n % 2 = 0 then '010-1234' else null end,
          n % 2 = 0, now(), now() from generate_series(1, ?) n
        """,
        count);
  }
}
