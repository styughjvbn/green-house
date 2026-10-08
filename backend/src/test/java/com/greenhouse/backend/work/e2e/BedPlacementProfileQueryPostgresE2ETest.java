package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.structure.application.BedPlacementProfileService;
import com.greenhouse.backend.farm.structure.domain.BedZoneCapacity;
import com.greenhouse.backend.farm.structure.domain.PlacementCapacityMode;
import com.greenhouse.backend.farm.structure.web.dto.BedZoneCapacityRequest;
import com.greenhouse.backend.farm.structure.web.dto.BedZonePlacementProfileRequest;
import com.greenhouse.backend.farm.structure.web.dto.BedZonePlacementProfileResponse;
import com.greenhouse.backend.farm.support.FarmTestFixtures;
import com.greenhouse.backend.farm.variety.domain.Variety;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("work-e2e")
class BedPlacementProfileQueryPostgresE2ETest extends WorkE2ETestBase {
  @Autowired BedPlacementProfileService profiles;
  @Autowired EntityManager em;
  @Autowired EntityManagerFactory emf;
  @Autowired TransactionTemplate transactions;
  @Autowired JdbcTemplate jdbc;

  @ParameterizedTest
  @CsvSource({"1,1", "1,3", "1,5", "10,1", "10,3", "10,5", "50,1", "50,3", "50,5"})
  void readsAndUpdatesOnlyProfileEntities(int groupCount, int capacityCount) throws Exception {
    var fixture =
        transactions.execute(
            tx -> seed(30000 + groupCount * 10 + capacityCount, groupCount, capacityCount));
    var inventoryBefore = inventory(fixture.zoneId());
    var stats = emf.unwrap(SessionFactory.class).getStatistics();
    stats.clear();
    var initial = profiles.getProfile(fixture.zoneId());
    var readMetrics = metrics(stats);
    // Include commit and cascade flush in the write measurement; there is no surrounding test tx.
    stats.clear();
    var updated = profiles.updateProfile(fixture.zoneId(), request(capacityCount, 10));
    var writeMetrics = metrics(stats);
    var path =
        Path.of(
            "build/work-query-count/placement-profile-"
                + groupCount
                + "-"
                + capacityCount
                + ".json");
    Files.createDirectories(path.getParent());
    objectMapper
        .writerWithDefaultPrettyPrinter()
        .writeValue(path.toFile(), Map.of("read", readMetrics, "write", writeMetrics));
    assertProfile(initial, fixture, capacityCount, 0);
    assertProfile(updated, fixture, capacityCount, 10);
    assertThat(initial.capacities()).allSatisfy(capacity -> assertThat(capacity.id()).isPositive());
    assertAudit(fixture, capacityCount);
    assertThat(inventory(fixture.zoneId())).isEqualTo(inventoryBefore);
    assertThat(readMetrics.get("preparedStatements")).isLessThanOrEqualTo(1);
    assertThat(writeMetrics.get("preparedStatements")).isLessThanOrEqualTo(2L * capacityCount + 6);
    for (var measurement : List.of(readMetrics, writeMetrics)) {
      assertThat(measurement.get("groupLoads"))
          .as("profile must not load inventory groups")
          .isZero();
      assertThat(measurement.get("varietyLoads")).isZero();
      assertThat(measurement.get("capacityLoads")).isLessThanOrEqualTo(capacityCount);
      assertThat(measurement.get("entityLoads")).isLessThanOrEqualTo(capacityCount + 3L);
    }
  }

  private Fixture seed(int houseNumber, int groupCount, int capacityCount) {
    var fixtures = new FarmTestFixtures(em);
    var layout = fixtures.layout(houseNumber);
    for (int i = 0; i < groupCount; i++)
      fixtures.orchidGroup(layout.left(), "PROFILE_" + houseNumber + "_" + i, 100);
    var capacities = new ArrayList<BedZoneCapacity>();
    for (int i = 0; i < capacityCount; i++)
      capacities.add(
          new BedZoneCapacity(
              "TRAY_20",
              "3치",
              PlacementCapacityMode.values()[i],
              BigDecimal.valueOf(6),
              i + 3,
              true,
              "규칙 " + i));
    Collections.reverse(capacities);
    layout.left().replaceCapacities(capacities);
    em.flush();
    return new Fixture(layout.left().getId(), houseNumber);
  }

  private BedZonePlacementProfileRequest request(int count, int offset) {
    return new BedZonePlacementProfileRequest(
        IntStream.range(0, count)
            .mapToObj(
                i ->
                    new BedZoneCapacityRequest(
                        "TRAY_20",
                        "3치",
                        PlacementCapacityMode.values()[i],
                        i + 3 + offset,
                        BigDecimal.valueOf(6),
                        true,
                        "규칙 " + i))
            .toList());
  }

  private Map<String, Long> metrics(Statistics stats) {
    return Map.of(
        "preparedStatements",
        stats.getPrepareStatementCount(),
        "entityLoads",
        stats.getEntityLoadCount(),
        "groupLoads",
        stats.getEntityStatistics(OrchidGroup.class.getName()).getLoadCount(),
        "varietyLoads",
        stats.getEntityStatistics(Variety.class.getName()).getLoadCount(),
        "capacityLoads",
        stats.getEntityStatistics(BedZoneCapacity.class.getName()).getLoadCount());
  }

  private void assertProfile(
      BedZonePlacementProfileResponse response, Fixture fixture, int count, int offset) {
    assertThat(response.bedZoneId()).isEqualTo(fixture.zoneId());
    assertThat(response.bedZoneName()).isEqualTo("좌");
    assertThat(response.houseNumber()).isEqualTo(fixture.houseNumber());
    assertThat(response.physicalBedNumber()).isEqualTo(1);
    assertThat(response.positionUnitCount()).isEqualByComparingTo("60");
    assertThat(response.positionUnitLabel()).isEqualTo("칸");
    assertThat(response.capacities()).hasSize(count);
    for (int i = 0; i < count; i++) {
      var capacity = response.capacities().get(i);
      assertThat(capacity.capacityMode()).isEqualTo(PlacementCapacityMode.values()[i]);
      assertThat(capacity.placementType()).isEqualTo("TRAY_20");
      assertThat(capacity.potSize()).isEqualTo("3\"");
      assertThat(capacity.unitSpan()).isEqualByComparingTo("6");
      assertThat(capacity.capacityValue()).isEqualTo(i + 3 + offset);
      assertThat(capacity.allowed()).isTrue();
      assertThat(capacity.memo()).isEqualTo("규칙 " + i);
    }
  }

  private void assertAudit(Fixture fixture, int count) throws Exception {
    var rows =
        jdbc.queryForList(
            "select * from audit_events where entity_type = 'BED_ZONE' and entity_id = ?",
            fixture.zoneId());
    assertThat(rows).hasSize(1);
    var event = rows.getFirst();
    assertThat(event.get("source")).isEqualTo("FARM_STRUCTURE_MANAGEMENT");
    assertThat(event.get("action")).isEqualTo("UPDATED");
    assertThat(event.get("zone_id")).isEqualTo(fixture.zoneId());
    assertThat(event.get("actor_id")).isNull();
    assertThat(event.get("house_id"))
        .isEqualTo(
            jdbc.queryForObject(
                "select id from houses where number = ?", Long.class, fixture.houseNumber()));
    assertThat((String[]) ((Array) event.get("changed_fields")).getArray())
        .containsExactly("capacities");
    for (String column : List.of("before_data", "after_data")) {
      JsonNode data = objectMapper.readTree(event.get(column).toString());
      assertThat(data.path("zoneId").asLong()).isEqualTo(fixture.zoneId());
      assertThat(data.path("zoneSide").asText()).isEqualTo("LEFT");
      assertThat(data.path("capacities")).hasSize(count);
      for (int i = 0; i < count; i++) {
        var rule = data.path("capacities").get(i);
        assertThat(rule.path("capacityMode").asText())
            .isEqualTo(PlacementCapacityMode.values()[i].name());
        assertThat(rule.path("placementType").asText()).isEqualTo("TRAY_20");
        assertThat(rule.path("potSize").asText()).isEqualTo("3\"");
        assertThat(rule.path("capacityValue").asInt())
            .isEqualTo(i + 3 + (column.equals("after_data") ? 10 : 0));
        assertThat(rule.path("unitSpan").decimalValue()).isEqualByComparingTo("6");
        assertThat(rule.path("allowed").asBoolean()).isTrue();
        assertThat(rule.path("memo").asText()).isEqualTo("규칙 " + i);
      }
    }
  }

  private List<Map<String, Object>> inventory(Long zoneId) {
    return jdbc.queryForList(
        "select * from orchid_groups where bed_zone_id = ? order by id", zoneId);
  }

  private record Fixture(Long zoneId, int houseNumber) {}
}
