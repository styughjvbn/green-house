package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Tag("work-e2e")
@IsolatedPostgresContext
class VarietySaleFlagMigrationPostgresE2ETest extends WorkE2ETestBase {
  @Test
  void removesUnusedFlagWhilePreservingVarietiesAndOrchidGroupLinks() {
    String database = "variety_flag_" + UUID.randomUUID().toString().replace("-", "");
    var admin =
        new JdbcTemplate(
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    admin.execute("CREATE DATABASE " + database);
    var source =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + database),
            POSTGRES.getUsername(),
            POSTGRES.getPassword());
    try {
      Flyway.configure().dataSource(source).target("42").load().migrate();
      var jdbc = new JdbcTemplate(source);
      jdbc.execute(
          """
          INSERT INTO varieties (id, code, genus, name, sale_enabled, is_active, created_at, updated_at)
          VALUES (9001, 'FLAG-TRUE', '속', '판매사용', true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                 (9002, 'FLAG-FALSE', '속', '판매미사용', false, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
          INSERT INTO orchid_groups (id, bed_zone_id, variety_id, genus, variety_name, quantity,
              reserved_quantity, sort_order, status, pot_size_code, version, state_revision, created_at, updated_at)
          VALUES (9001, (SELECT min(id) FROM bed_zones), 9001, '속', '판매사용', 10,
              0, 1, '정상', 'POT_3', 0, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
          """);
      var varietiesBefore =
          jdbc.queryForList(
              "SELECT (to_jsonb(v) - 'sale_enabled')::text FROM varieties v ORDER BY id",
              String.class);
      var groupsBefore =
          jdbc.queryForList(
              "SELECT to_jsonb(g)::text FROM orchid_groups g ORDER BY id", String.class);

      var upgrade = Flyway.configure().dataSource(source).target("43").load();
      assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  """
          SELECT count(*) FROM information_schema.columns
          WHERE table_schema = 'public' AND table_name = 'varieties' AND column_name = 'sale_enabled'
          """,
                  Integer.class))
          .isZero();
      assertThat(
              jdbc.queryForList(
                  "SELECT to_jsonb(v)::text FROM varieties v ORDER BY id", String.class))
          .isEqualTo(varietiesBefore);
      assertThat(
              jdbc.queryForList(
                  "SELECT to_jsonb(g)::text FROM orchid_groups g ORDER BY id", String.class))
          .isEqualTo(groupsBefore);
      assertThat(upgrade.migrate().migrationsExecuted).isZero();
    } finally {
      admin.execute("DROP DATABASE " + database + " WITH (FORCE)");
    }
  }
}
