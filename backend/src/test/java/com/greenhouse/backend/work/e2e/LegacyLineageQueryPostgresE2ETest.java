package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.greenhouse.backend.farm.application.transformation.OrchidGroupLineageService;
import com.greenhouse.backend.farm.domain.inbound.InboundRecord;
import com.greenhouse.backend.farm.domain.inbound.InboundStatus;
import com.greenhouse.backend.farm.domain.inbound.InboundType;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.transformation.OrchidGroupLineage;
import com.greenhouse.backend.farm.domain.transformation.OrchidGroupLineageRelationType;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupResponse;
import com.greenhouse.backend.farm.support.FarmTestFixtures;
import com.greenhouse.backend.work.api.operation.WorkSourceScopeType;
import com.greenhouse.backend.work.api.operation.WorkTypeTemplate;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.operation.WorkType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("work-e2e")
@Import(QueryShapeCapture.Configuration.class)
class LegacyLineageQueryPostgresE2ETest extends WorkE2ETestBase {
  @Autowired OrchidGroupLineageService lineage;
  @Autowired EntityManager em;
  @Autowired EntityManagerFactory emf;
  @Autowired TransactionTemplate transactions;
  @MockitoBean Clock clock;
  @Autowired QueryShapeCapture capture;

  @BeforeEach
  void fixedBusinessDate() {
    when(clock.instant()).thenReturn(Instant.parse("2026-10-04T00:00:00Z"));
    when(clock.getZone()).thenReturn(ZoneOffset.UTC);
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 10, 50, 501})
  void directLineageLoadsIndependentReferencesInBulk(int count) throws Exception {
    var fixture = transactions.execute(tx -> seed(count));
    var stats = emf.unwrap(SessionFactory.class).getStatistics();
    stats.clear();
    capture.start();
    // A fresh service transaction must assemble the whole response, including age and location.
    var response = lineage.getLineage(fixture.center().id());
    long statements = stats.getPrepareStatementCount();
    assertThat(QueryShapeCapture.maxParameters(capture.stop())).isLessThanOrEqualTo(500);
    var path = Path.of("build/work-query-count/legacy-lineage-" + count + ".json");
    Files.createDirectories(path.getParent());
    objectMapper
        .writerWithDefaultPrettyPrinter()
        .writeValue(
            path.toFile(),
            Map.of(
                "preparedStatements",
                statements,
                "entityLoads",
                stats.getEntityLoadCount(),
                "collectionFetches",
                stats.getCollectionFetchCount()));
    assertThat(response.transformations()).isEmpty();
    assertThat(response.sources())
        .extracting(item -> item.id())
        .containsExactlyElementsOf(fixture.sourceIds());
    assertThat(response.results())
        .extracting(item -> item.id())
        .containsExactlyElementsOf(fixture.resultIds());
    for (int i = 0; i < count; i++) {
      var source = response.sources().get(i);
      var result = response.results().get(i);
      assertThat(source.workOperationId()).isEqualTo(fixture.operationId());
      assertThat(result.workOperationId()).isEqualTo(fixture.operationId());
      assertThat(source.relationType()).isEqualTo(OrchidGroupLineageRelationType.REPOTTED_TO);
      assertThat(result.relationType()).isEqualTo(OrchidGroupLineageRelationType.SPLIT_TO);
      assertThat(source.sourceQuantity()).isEqualTo(i + 1);
      assertThat(source.resultQuantity()).isEqualTo(i + 2);
      assertThat(result.sourceQuantity()).isEqualTo(i + 2);
      assertThat(result.resultQuantity()).isEqualTo(i + 1);
      assertGroup(source.sourceOrchidGroup(), fixture.sources().get(i));
      assertGroup(source.resultOrchidGroup(), fixture.center());
      assertGroup(result.sourceOrchidGroup(), fixture.center());
      assertGroup(result.resultOrchidGroup(), fixture.results().get(i));
      assertThat(source.createdAt()).isNotNull();
      assertThat(result.createdAt()).isNotNull();
    }
    assertThat(statements)
        .as("complete direct lineage, incoming/outgoing links=%s", count)
        .isLessThanOrEqualTo(8);
  }

  private Fixture seed(int count) {
    var fixtures = new FarmTestFixtures(em);
    var type =
        new WorkType(
            "LEGACY_QUERY_" + count, "계보 회귀", WorkTypeTemplate.REPOT, false, false, true, 1);
    em.persist(type);
    var operation =
        new WorkOperation(
            type,
            "기존 직접 계보",
            LocalDate.of(2026, 10, 4),
            null,
            WorkSourceScopeType.NONE,
            null,
            Map.of(),
            Map.of(),
            "기존 담당",
            null,
            LocalDateTime.of(2026, 10, 4, 0, 0));
    em.persist(operation);
    int base = 20000 + count * 200;
    var center = group(fixtures, base);
    var sources = new ArrayList<Group>();
    var results = new ArrayList<Group>();
    var sourceIds = new ArrayList<Long>();
    var resultIds = new ArrayList<Long>();
    for (int i = 0; i < count; i++) {
      var source = group(fixtures, base + 1 + i * 2);
      var result = group(fixtures, base + 2 + i * 2);
      var incoming =
          new OrchidGroupLineage(
              source,
              center,
              OrchidGroupLineageRelationType.REPOTTED_TO,
              operation.getId(),
              i + 1,
              i + 2);
      var outgoing =
          new OrchidGroupLineage(
              center,
              result,
              OrchidGroupLineageRelationType.SPLIT_TO,
              operation.getId(),
              i + 2,
              i + 1);
      em.persist(incoming);
      em.persist(outgoing);
      sources.add(reference(source));
      results.add(reference(result));
      sourceIds.add(incoming.getId());
      resultIds.add(outgoing.getId());
    }
    em.flush();
    return new Fixture(
        reference(center), operation.getId(), sources, results, sourceIds, resultIds);
  }

  private OrchidGroup group(FarmTestFixtures fixtures, int number) {
    var group = fixtures.orchidGroup(fixtures.layout(number).left(), "LEGACY_QUERY_" + number, 100);
    var inbound =
        new InboundRecord(
            LocalDate.of(2020, 10, 4),
            InboundType.PRODUCT_POT,
            group.getVariety(),
            InboundStatus.PLACED,
            100,
            null,
            null,
            "기존 담당",
            null);
    em.persist(inbound);
    group.assignInboundRecord(inbound);
    return group;
  }

  private Group reference(OrchidGroup group) {
    var zone = group.getBedZone();
    var house = zone.getPhysicalBed().getHouse();
    return new Group(
        group.getId(),
        zone.getId(),
        house.getId(),
        house.getNumber(),
        group.getVariety().getId(),
        group.getVariety().getName());
  }

  private void assertGroup(OrchidGroupResponse actual, Group expected) {
    assertThat(actual.id()).isEqualTo(expected.id());
    assertThat(actual.bedZoneId()).isEqualTo(expected.zoneId());
    assertThat(actual.houseId()).isEqualTo(expected.houseId());
    assertThat(actual.houseNumber()).isEqualTo(expected.houseNumber());
    assertThat(actual.physicalBedNumber()).isEqualTo(1);
    assertThat(actual.bedZoneName()).isEqualTo("좌");
    assertThat(actual.varietyId()).isEqualTo(expected.varietyId());
    assertThat(actual.varietyName()).isEqualTo(expected.varietyName());
    assertThat(actual.ageYear()).isEqualTo(7);
    assertThat(actual.quantity()).isEqualTo(100);
  }

  private record Group(
      Long id, Long zoneId, Long houseId, int houseNumber, Long varietyId, String varietyName) {}

  private record Fixture(
      Group center,
      Long operationId,
      List<Group> sources,
      List<Group> results,
      List<Long> sourceIds,
      List<Long> resultIds) {}
}
