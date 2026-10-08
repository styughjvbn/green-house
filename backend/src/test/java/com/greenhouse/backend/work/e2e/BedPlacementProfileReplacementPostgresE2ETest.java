package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.greenhouse.backend.farm.structure.application.BedPlacementProfileService;
import com.greenhouse.backend.farm.structure.domain.BedZoneCapacity;
import com.greenhouse.backend.farm.structure.domain.PlacementCapacityMode;
import com.greenhouse.backend.farm.structure.web.dto.BedZoneCapacityRequest;
import com.greenhouse.backend.farm.structure.web.dto.BedZonePlacementProfileRequest;
import com.greenhouse.backend.farm.support.FarmTestFixtures;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("work-e2e")
class BedPlacementProfileReplacementPostgresE2ETest extends WorkE2ETestBase {
  @Autowired BedPlacementProfileService profiles;
  @Autowired EntityManager em;
  @Autowired TransactionTemplate transactions;
  @Autowired JdbcTemplate jdbc;

  @Test
  void sameRuleKeysCanBeReplacedAtomicallyIncludingAuditFailureAndRetry() {
    long zoneId =
        transactions.execute(
            tx -> {
              var fixtures = new FarmTestFixtures(em);
              var zone = fixtures.layout(39000).left();
              fixtures.orchidGroup(zone, "PROFILE_REPLACEMENT", 100);
              zone.replaceCapacities(
                  IntStream.range(0, 3)
                      .mapToObj(
                          i ->
                              new BedZoneCapacity(
                                  "TRAY_20",
                                  "3치",
                                  PlacementCapacityMode.values()[i],
                                  BigDecimal.valueOf(6),
                                  i + 3,
                                  true,
                                  "기존"))
                      .toList());
              em.flush();
              return zone.getId();
            });
    var inventory =
        jdbc.queryForList("select * from orchid_groups where bed_zone_id = ? order by id", zoneId);
    var originalIds = profiles.getProfile(zoneId).capacities().stream().map(c -> c.id()).toList();
    profiles.updateProfile(zoneId, request(10));
    var first = profiles.getProfile(zoneId);
    assertThat(first.capacities()).extracting(c -> c.capacityValue()).containsExactly(13, 14, 15);
    assertThat(first.capacities())
        .allSatisfy(c -> assertThat(c.id()).isPositive().isNotIn(originalIds));
    assertThat(auditCount(zoneId)).isEqualTo(1);
    // Replacing identical values still skips the audit while retaining the existing replacement
    // semantics.
    profiles.updateProfile(zoneId, request(10));
    assertThat(auditCount(zoneId)).isEqualTo(1);
    var beforeFailure = profiles.getProfile(zoneId);
    var rulesBefore =
        jdbc.queryForList(
            "select * from bed_zone_capacities where bed_zone_id = ? order by id", zoneId);
    jdbc.execute(
        "alter table audit_events add constraint test_profile_audit_failure check (entity_type <> 'BED_ZONE' or entity_id <> "
            + zoneId
            + ") not valid");
    try {
      assertThatThrownBy(() -> profiles.updateProfile(zoneId, request(20)))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("test_profile_audit_failure");
      assertThat(profiles.getProfile(zoneId)).isEqualTo(beforeFailure);
      assertThat(
              jdbc.queryForList(
                  "select * from bed_zone_capacities where bed_zone_id = ? order by id", zoneId))
          .isEqualTo(rulesBefore);
      assertThat(auditCount(zoneId)).isEqualTo(1);
    } finally {
      jdbc.execute("alter table audit_events drop constraint test_profile_audit_failure");
    }
    profiles.updateProfile(zoneId, request(20));
    assertThat(profiles.getProfile(zoneId).capacities())
        .extracting(c -> c.capacityValue())
        .containsExactly(23, 24, 25);
    assertThat(auditCount(zoneId)).isEqualTo(2);
    assertThat(
            jdbc.queryForList(
                "select * from orchid_groups where bed_zone_id = ? order by id", zoneId))
        .isEqualTo(inventory);
  }

  private BedZonePlacementProfileRequest request(int offset) {
    return new BedZonePlacementProfileRequest(
        IntStream.range(0, 3)
            .mapToObj(
                i ->
                    new BedZoneCapacityRequest(
                        "TRAY_20",
                        "3치",
                        PlacementCapacityMode.values()[i],
                        i + 3 + offset,
                        BigDecimal.valueOf(6),
                        true,
                        "기존"))
            .toList());
  }

  private long auditCount(long zoneId) {
    return jdbc.queryForObject(
        "select count(*) from audit_events where entity_type = 'BED_ZONE' and entity_id = ?",
        Long.class,
        zoneId);
  }
}
