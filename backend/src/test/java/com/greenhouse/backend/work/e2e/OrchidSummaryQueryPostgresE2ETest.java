package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.greenhouse.backend.farm.domain.inbound.InboundRecord;
import com.greenhouse.backend.farm.domain.inbound.InboundStatus;
import com.greenhouse.backend.farm.domain.inbound.InboundType;
import com.greenhouse.backend.farm.orchid.application.DerivedOrchidGroupService;
import com.greenhouse.backend.farm.orchid.domain.OrchidGroup;
import com.greenhouse.backend.farm.orchid.domain.PotSizeCode;
import com.greenhouse.backend.farm.orchid.web.dto.OrchidGroupResponse;
import com.greenhouse.backend.farm.support.FarmTestFixtures;
import com.greenhouse.backend.farm.variety.application.VarietyService;
import com.greenhouse.backend.farm.variety.domain.Variety;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("work-e2e")
class OrchidSummaryQueryPostgresE2ETest extends WorkE2ETestBase {
  @Autowired VarietyService varieties;
  @Autowired DerivedOrchidGroupService derived;
  @Autowired EntityManager em;
  @Autowired EntityManagerFactory emf;
  @Autowired TransactionTemplate transactions;
  @Autowired JdbcTemplate jdbc;
  @MockitoBean Clock clock;

  @BeforeEach
  void fixedDate() {
    when(clock.instant()).thenReturn(Instant.parse("2026-10-04T00:00:00Z"));
    when(clock.getZone()).thenReturn(ZoneOffset.UTC);
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 500, 5001})
  void summariesKeepQuantitiesDatesAndAgeWithoutLoadingInventoryEntities(int count)
      throws Exception {
    var fixture = transactions.execute(tx -> seed(count));
    var stats = emf.unwrap(SessionFactory.class).getStatistics();
    stats.clear();
    var page = varieties.getVarieties("SUMMARY_" + count + "_", null, null, null, 0, 10);
    var varietyMetrics = metrics();
    stats.clear();
    var groups = derived.getGroups(fixture.firstVariety(), 7, PotSizeCode.POT_3, null, null, null);
    var derivedMetrics = metrics();
    stats.clear();
    var members = derived.getMembers(fixture.firstVariety() + ":7:POT_3", null, null, null);
    var memberMetrics = metrics();
    var report = Path.of("build/work-query-count/orchid-summary-" + count + ".json");
    Files.createDirectories(report.getParent());
    objectMapper
        .writerWithDefaultPrettyPrinter()
        .writeValue(
            report.toFile(),
            Map.of("variety", varietyMetrics, "derived", derivedMetrics, "members", memberMetrics));
    assertThat(page.totalElements()).isEqualTo(3);
    long normal = IntStream.rangeClosed(1, count).filter(i -> i % 4 == 0 || i % 4 == 3).count();
    for (var variety : page.content()) {
      if (variety.id().equals(fixture.firstVariety())) {
        assertThat(variety.connectedGroupCount()).isEqualTo(count);
        assertThat(variety.totalQuantity()).isEqualTo(100L * count);
        assertThat(variety.saleableQuantity()).isEqualTo(90L * normal);
        assertThat(variety.recentWorkDate()).isEqualTo(LocalDate.of(2026, 7, 20));
        assertThat(variety.recentInboundDate()).isEqualTo(LocalDate.of(2020, 10, 4));
      } else if (variety.id().equals(fixture.secondVariety())) {
        assertThat(variety.connectedGroupCount()).isEqualTo(1);
        assertThat(variety.totalQuantity()).isEqualTo(70);
        assertThat(variety.saleableQuantity()).isEqualTo(65);
        assertThat(variety.recentWorkDate())
            .as("latest work date belongs to this variety")
            .isEqualTo(LocalDate.of(2026, 7, 1));
      } else {
        assertThat(variety.connectedGroupCount()).isZero();
        assertThat(variety.recentWorkDate()).isNull();
      }
    }
    int active = count - (count + 2) / 4;
    assertThat(groups)
        .singleElement()
        .satisfies(
            group -> {
              assertThat(group.groupKey()).isEqualTo(fixture.firstVariety() + ":7:POT_3");
              assertThat(group.orchidGroupCount()).isEqualTo(active);
              assertThat(group.totalQuantity()).isEqualTo(active * 100);
              assertThat(group.locationCount()).isEqualTo(count == 1 ? 1 : 2);
            });
    assertThat(members).hasSize(active).contains(fixture.firstMember());
    assertThat(derived.getGroups(fixture.firstVariety(), 6, PotSizeCode.POT_3, null, null, null))
        .isEmpty();
    assertThat(varietyMetrics.get("groupLoads")).isZero();
    assertThat(varietyMetrics.get("entityLoads")).isLessThanOrEqualTo(3);
    assertThat(varietyMetrics.get("queries")).isLessThanOrEqualTo(5 + (count + 1L + 499) / 500);
    for (var metrics : List.of(derivedMetrics, memberMetrics)) {
      assertThat(metrics.get("entityLoads")).isZero();
      assertThat(metrics.get("queries")).isLessThanOrEqualTo(1);
    }
  }

  private Fixture seed(int count) {
    var layout = new FarmTestFixtures(em).layout(40000 + count);
    var first = variety("SUMMARY_" + count + "_A");
    var second = variety("SUMMARY_" + count + "_B");
    variety("SUMMARY_" + count + "_EMPTY");
    var inbound =
        new InboundRecord(
            LocalDate.of(2020, 10, 4),
            InboundType.PRODUCT_POT,
            first,
            InboundStatus.PLACED,
            count * 100,
            null,
            null,
            "기존",
            null);
    em.persist(inbound);
    em.flush();
    var ids =
        jdbc.queryForList(
            """
        insert into orchid_groups (created_at, updated_at, age_year, genus, variety_name,
          placement_type, pot_size, pot_size_code, quantity, reserved_quantity, sort_order,
          status, bed_zone_id, variety_id, inbound_record_id, split_placement_allowed,
          start_position, end_position, memo)
        select timestamp '2026-01-01 18:00', timestamp '2026-01-01', 1, '과거 속', '과거 품종',
          'POT', '3"', 'POT_3', 100, 10, i,
          case i % 4 when 1 then '주의' when 2 then '종료' else '정상' end,
          case i % 2 when 0 then ? else ? end, ?, ?, false, 0, 1, '기존 메모'
        from generate_series(1, ?) i returning id
        """,
            Long.class,
            layout.right().getId(),
            layout.left().getId(),
            first.getId(),
            inbound.getId(),
            count);
    long secondGroup =
        jdbc.queryForObject(
            """
        insert into orchid_groups (created_at, updated_at, age_year, genus, variety_name,
          placement_type, pot_size, pot_size_code, quantity, reserved_quantity, sort_order,
          status, bed_zone_id, variety_id, split_placement_allowed, start_position, end_position)
        values (timestamp '2026-01-01 18:00', timestamp '2026-01-01', 1, '난', '두번째', 'POT',
          '3"', 'POT_3', 70, 5, 0, '정상', ?, ?, false, 0, 1) returning id
        """,
            Long.class,
            layout.left().getId(),
            second.getId());
    for (long groupId : new long[] {ids.getFirst(), secondGroup}) {
      long operation =
          jdbc.queryForObject(
              """
          insert into work_operations (work_type_id, title, status, planned_start_date,
            source_scope_type, target_snapshot_at, version, created_at, updated_at)
          values ((select id from work_types where code = 'PESTICIDE'), '요약 완료 이력',
            'COMPLETED', ?::date, 'MANUAL_SELECTION', timestamp '2026-07-01', 0,
            timestamp '2026-07-01', timestamp '2026-07-01') returning id
          """,
              Long.class,
              groupId == secondGroup ? "2026-07-01" : "2026-07-20");
      jdbc.update(
          """
          insert into work_operation_targets (work_operation_id, orchid_group_id,
            target_reference_type, inclusion_source, included_at, variety_name_snapshot,
            quantity_snapshot, location_snapshot, created_at)
          values (?, ?, 'ORCHID_GROUP', 'MANUAL_SELECTION', timestamp '2026-07-01', '이력',
            50, '{}'::jsonb, timestamp '2026-07-01')
          """,
          operation,
          groupId);
    }
    var original =
        OrchidGroupResponse.from(
            em.find(OrchidGroup.class, ids.getFirst()), LocalDate.of(2026, 10, 4));
    return new Fixture(first.getId(), second.getId(), original);
  }

  @ParameterizedTest
  @CsvSource(
      value = {
        "1,2021-02-28,2020-02-29,2020-02-29T00:00:00,2,2",
        "2,2021-03-01,2020-02-29,2020-02-29T00:00:00,2,3",
        "3,2026-10-04,2027-10-04,2020-01-01T00:00:00,2,2",
        "4,2026-10-04,NULL,2020-10-03T18:00:00,2,8",
        "5,2026-10-04,NULL,2020-10-04T18:00:00,2,7",
        "6,2026-10-04,2020-10-04,2020-01-01T00:00:00,NULL,NULL"
      },
      nullValues = "NULL")
  void streamedRowsKeepLeapFutureUnknownAndFarmDateAge(
      int index,
      LocalDate date,
      LocalDate inboundDate,
      LocalDateTime createdAt,
      Integer baseAge,
      Integer expectedAge) {
    when(clock.instant()).thenReturn(date.atStartOfDay().toInstant(ZoneOffset.UTC));
    var original =
        transactions.execute(
            tx -> {
              var layout = new FarmTestFixtures(em).layout(48000 + index);
              var variety = variety("AGE_CASE_" + index);
              var group =
                  new OrchidGroup(
                      layout.left(),
                      "과거 속",
                      "과거 이름",
                      10,
                      "3치",
                      baseAge,
                      "정상",
                      1,
                      BigDecimal.ZERO,
                      BigDecimal.ONE);
              group.assignVariety(variety);
              em.persist(group);
              if (inboundDate != null) {
                var inbound =
                    new InboundRecord(
                        inboundDate,
                        InboundType.PRODUCT_POT,
                        variety,
                        InboundStatus.PLACED,
                        10,
                        null,
                        null,
                        "기존",
                        null);
                em.persist(inbound);
                group.assignInboundRecord(inbound);
              }
              em.flush();
              long id = group.getId();
              jdbc.update("update orchid_groups set created_at = ? where id = ?", createdAt, id);
              em.clear();
              return OrchidGroupResponse.from(em.find(OrchidGroup.class, id), date);
            });
    var stats = emf.unwrap(SessionFactory.class).getStatistics();
    stats.clear();
    var summaries = derived.getGroups(original.varietyId(), null, null, null, null, null);
    assertThat(summaries)
        .singleElement()
        .satisfies(
            summary -> {
              assertThat(summary.ageYear()).isEqualTo(expectedAge);
              assertThat(summary.groupKey())
                  .isEqualTo(
                      original.varietyId()
                          + ":"
                          + (expectedAge == null ? "UNSPECIFIED" : expectedAge)
                          + ":POT_3");
              assertThat(derived.getMembers(summary.groupKey(), null, null, null))
                  .containsExactly(original);
            });
    assertThat(stats.getEntityLoadCount()).isZero();
  }

  private Variety variety(String name) {
    var variety = new Variety(name, "난", name, null, "3치", true, true, null, null);
    em.persist(variety);
    return variety;
  }

  private Map<String, Long> metrics() {
    var stats = emf.unwrap(SessionFactory.class).getStatistics();
    return Map.of(
        "queries",
        stats.getPrepareStatementCount(),
        "entityLoads",
        stats.getEntityLoadCount(),
        "groupLoads",
        stats.getEntityStatistics(OrchidGroup.class.getName()).getLoadCount());
  }

  private record Fixture(Long firstVariety, Long secondVariety, OrchidGroupResponse firstMember) {}
}
