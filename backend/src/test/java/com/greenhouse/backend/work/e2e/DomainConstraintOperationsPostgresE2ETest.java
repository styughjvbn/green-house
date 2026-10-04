package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Tag("work-e2e")
@Testcontainers
class DomainConstraintOperationsPostgresE2ETest {
  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

  private static final List<String> SCRIPTS =
      List.of(
          "domain-constraint-catalog.sql",
          "audit-domain-constraints.sql",
          "validate-domain-constraint.sql");
  private static final List<Target> TARGETS =
      List.of(
          new Target("orchid_groups", "ck_orchid_groups_quantity_nonnegative", "quantity = -1"),
          new Target(
              "orchid_groups", "ck_orchid_groups_reserved_quantity", "reserved_quantity = 101"),
          new Target("orchid_groups", "ck_orchid_groups_state_revision", "state_revision = -1"),
          new Target("sales_slips", "ck_sales_slips_sales_status", "sales_status = '출하 완료'"),
          new Target("sales_slips", "ck_sales_slips_total_amount", "total_amount = -1"),
          new Target("sales_slip_items", "ck_sales_slip_items_amount", "amount = 1999"));
  private final ObjectMapper mapper = new ObjectMapper();
  private JdbcTemplate admin;
  private JdbcTemplate jdbc;
  private DriverManagerDataSource dataSource;
  private String database;
  private String readerRole;

  @BeforeAll
  static void copyActualScripts() throws Exception {
    for (String script : SCRIPTS) {
      POSTGRES.copyFileToContainer(
          Transferable.of(Files.readAllBytes(Path.of("../scripts/data-audit", script))),
          "/domain-constraints/" + script);
    }
  }

  @BeforeEach
  void createDatabase() {
    admin =
        new JdbcTemplate(
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    database = "constraint_operations_" + UUID.randomUUID().toString().replace("-", "");
    admin.execute("CREATE DATABASE " + database);
    dataSource =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + database),
            POSTGRES.getUsername(),
            POSTGRES.getPassword());
    Flyway.configure().dataSource(dataSource).load().migrate();
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute(
        """
        INSERT INTO orchid_groups (id, created_at, updated_at, quantity, reserved_quantity,
            sort_order, status, variety_name, bed_zone_id)
        VALUES (900, TIMESTAMP '2026-10-04 00:00:00', TIMESTAMP '2026-10-04 00:00:00',
            100, 0, 1, '정상', '대사 fixture', (SELECT min(id) FROM bed_zones))
        """);
    jdbc.execute(
        """
        INSERT INTO business_partners (id, name, created_at, updated_at)
        VALUES (900, '대사 거래처', TIMESTAMP '2026-10-04 00:00:00', TIMESTAMP '2026-10-04 00:00:00')
        """);
    jdbc.execute(
        """
        INSERT INTO sales_slips (id, slip_number, sale_date, sales_type, partner_id,
            total_amount, paid_amount, remaining_amount, payment_status, sales_status,
            created_at, updated_at)
        VALUES (900, 'AUDIT-FIXTURE', DATE '2026-10-04', 'DIRECT', 900,
            2000, 0, 2000, '미입금', '작성중', TIMESTAMP '2026-10-04 00:00:00', TIMESTAMP '2026-10-04 00:00:00')
        """);
    jdbc.execute(
        """
        INSERT INTO sales_slip_items (id, sales_slip_id, item_name, quantity, unit_price, amount)
        VALUES (900, 900, '대사 품목', 2, 1000, 2000)
        """);
  }

  @AfterEach
  void dropDatabaseAndRole() {
    if (database != null) admin.execute("DROP DATABASE " + database + " WITH (FORCE)");
    if (readerRole != null) admin.execute("DROP ROLE " + readerRole);
  }

  static Stream<Target> targets() {
    return TARGETS.stream();
  }

  @Test
  void readOnlyRoleReportsUnvalidatedCleanRowsWithoutChangingDataOrCatalog() throws Exception {
    createReader();
    var rows = rows();
    var catalog = catalog();
    var reports = readSuccess(run("audit-domain-constraints.sql", null, readerRole));
    assertThat(reports).hasSize(7);
    assertThat(reports.getFirst().path("readOnly").asText()).isEqualTo("on");
    assertThat(reports.getFirst().path("isolation").asText()).isEqualTo("repeatable read");
    assertThat(reports.getFirst().path("user").asText()).isEqualTo(readerRole);
    assertThat(reports.getFirst().path("snapshot").asText()).isNotBlank();
    assertThat(
            jdbc.queryForList(
                """
                SELECT conname FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                WHERE n.nspname = 'public' AND c.contype = 'c' AND NOT c.convalidated
                """,
                String.class))
        .containsExactlyInAnyOrderElementsOf(
            reports.stream().skip(1).map(r -> r.path("constraint").asText()).toList());
    assertThat(reports.subList(1, reports.size()))
        .allSatisfy(
            report -> {
              assertThat(report.path("status").asText()).isEqualTo("UNVALIDATED");
              assertThat(report.path("validated").asBoolean()).isFalse();
              assertThat(report.path("violationCount").asLong()).isZero();
              assertThat(report.path("sampleIds").size()).isZero();
            });
    assertThat(rows()).isEqualTo(rows);
    assertThat(catalog()).isEqualTo(catalog);
  }

  @ParameterizedTest
  @MethodSource("targets")
  void legacyViolationIsReportedAndValidationCannotRewriteOrValidateIt(Target target)
      throws Exception {
    installLegacyViolation(target);
    var rows = rows();
    var catalog = catalog();
    var report = reportFor(target.name());
    assertThat(report.path("status").asText()).isEqualTo("VIOLATIONS");
    assertThat(report.path("violationCount").asInt()).isEqualTo(1);
    assertThat(report.path("sampleIds").get(0).asInt()).isEqualTo(900);
    var validation = run("validate-domain-constraint.sql", target.name(), null);
    assertThat(validation.getExitCode()).isNotZero();
    assertThat(validation.getStderr()).contains(target.name());
    // NOT VALID still rejects modifications to the invalid historical row.
    assertThatThrownBy(
            () -> jdbc.update("UPDATE " + target.table() + " SET memo = 'blocked' WHERE id = 900"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(rows()).isEqualTo(rows);
    assertThat(catalog()).isEqualTo(catalog);
  }

  @ParameterizedTest
  @MethodSource("targets")
  void validatesOnlyRequestedCheckAndCanBeRepeatedWithoutRewritingHistory(Target target)
      throws Exception {
    var rows = rows();
    var first = readSuccess(run("validate-domain-constraint.sql", target.name(), null));
    assertThat(first).hasSize(1);
    assertThat(first.getFirst().path("validated").asBoolean()).isTrue();
    assertThat(first.getFirst().path("constraint").asText()).isEqualTo(target.name());
    var catalog = catalog();
    assertThat(readSuccess(run("validate-domain-constraint.sql", target.name(), null)))
        .isEqualTo(first);
    assertThat(catalog()).isEqualTo(catalog);
    var reports = readSuccess(run("audit-domain-constraints.sql", null, null));
    assertThat(reports.stream().skip(1).filter(r -> r.path("validated").asBoolean()).toList())
        .hasSize(1);
    assertThat(reportFor(target.name()).path("status").asText()).isEqualTo("VALIDATED");
    assertThat(rows()).isEqualTo(rows);
    var flyway = Flyway.configure().dataSource(dataSource).load();
    flyway.validate();
    assertThat(flyway.migrate().migrationsExecuted).isZero();
  }

  @Test
  void missingConstraintIsNotReportedAsZeroViolationsAndCannotBeValidated() throws Exception {
    var target = TARGETS.getFirst();
    jdbc.execute("ALTER TABLE " + target.table() + " DROP CONSTRAINT " + target.name());
    var before = catalog();
    var report = reportFor(target.name());
    assertThat(report.path("status").asText()).isEqualTo("MISSING");
    assertThat(report.path("violationCount").isNull()).isTrue();
    var result = run("validate-domain-constraint.sql", target.name(), null);
    assertThat(result.getExitCode()).isNotZero();
    assertThat(result.getStderr()).contains("Unknown, missing, or non-CHECK");
    assertThat(catalog()).isEqualTo(before);
  }

  @Test
  void sameNameOfDifferentConstraintKindIsNotAccepted() throws Exception {
    var target = TARGETS.getFirst();
    jdbc.execute("ALTER TABLE " + target.table() + " DROP CONSTRAINT " + target.name());
    jdbc.execute(
        "ALTER TABLE "
            + target.table()
            + " ADD CONSTRAINT "
            + target.name()
            + " UNIQUE (quantity)");
    var report = reportFor(target.name());
    assertThat(report.path("status").asText()).isEqualTo("NOT_CHECK");
    assertThat(report.path("violationCount").isNull()).isTrue();
    assertThat(run("validate-domain-constraint.sql", target.name(), null).getExitCode())
        .isNotZero();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {"not_a_target", "ck_sales_slips_total_amount'; DROP TABLE sales_slips; --"})
  void missingUnknownAndSqlTextParametersCannotSelectAnArbitraryDdlTarget(String name)
      throws Exception {
    var rows = rows();
    var catalog = catalog();
    assertThat(run("validate-domain-constraint.sql", name, null).getExitCode()).isNotZero();
    assertThat(rows()).isEqualTo(rows);
    assertThat(catalog()).isEqualTo(catalog);
  }

  @Test
  void samplesAreBoundedButCountsIncludeEveryViolation() throws Exception {
    var quantity = TARGETS.getFirst();
    var reservation = TARGETS.get(1);
    String quantityDefinition = definition(quantity);
    String reservationDefinition = definition(reservation);
    jdbc.execute("ALTER TABLE orchid_groups DROP CONSTRAINT " + quantity.name());
    jdbc.execute("ALTER TABLE orchid_groups DROP CONSTRAINT " + reservation.name());
    jdbc.execute(
        """
        INSERT INTO orchid_groups (id, created_at, updated_at, quantity, reserved_quantity,
            sort_order, status, variety_name, bed_zone_id)
        SELECT n, created_at, updated_at, -1, 0, n, status, variety_name, bed_zone_id
        FROM orchid_groups CROSS JOIN generate_series(901, 1005) n WHERE id = 900
        """);
    addNotValid(quantity, quantityDefinition);
    addNotValid(reservation, reservationDefinition);
    var rows = rows();
    var report = reportFor(quantity.name());
    assertThat(report.path("violationCount").asInt()).isEqualTo(105);
    assertThat(report.path("sampleIds").size()).isEqualTo(50);
    assertThat(report.path("sampleIds").get(0).asInt()).isEqualTo(901);
    assertThat(report.path("sampleIds").get(49).asInt()).isEqualTo(950);
    assertThat(rows()).isEqualTo(rows);
  }

  @Test
  void evaluatesTheInstalledExpressionAndPreservesCheckNullSemantics() throws Exception {
    var target = TARGETS.get(2);
    jdbc.execute("ALTER TABLE orchid_groups DROP CONSTRAINT " + target.name());
    jdbc.execute(
        "ALTER TABLE orchid_groups ADD CONSTRAINT "
            + target.name()
            + " CHECK (state_revision >= 0) NOT VALID");
    var report = reportFor(target.name());
    assertThat(report.path("violationCount").asInt()).isZero();
    assertThat(report.path("expression").asText()).doesNotContain("IS NULL");
    assertThat(
            readSuccess(run("validate-domain-constraint.sql", target.name(), null))
                .getFirst()
                .path("validated")
                .asBoolean())
        .isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT state_revision FROM orchid_groups WHERE id = 900", Long.class))
        .isNull();
    assertThatThrownBy(
            () -> jdbc.update("UPDATE orchid_groups SET state_revision = -1 WHERE id = 900"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining(target.name());
  }

  @Test
  void rowSecurityCannotHideRowsAndProduceAFalseCleanReport() throws Exception {
    createReader();
    jdbc.execute("ALTER TABLE orchid_groups ENABLE ROW LEVEL SECURITY");
    jdbc.execute("CREATE POLICY hide_all ON orchid_groups FOR SELECT USING (false)");
    var result = run("audit-domain-constraints.sql", null, readerRole);
    assertThat(result.getExitCode()).isNotZero();
    assertThat(result.getStderr()).contains("row-level security");
  }

  @Test
  void validationLockWaitIsBoundedAndLeavesTheConstraintUnvalidated() throws Exception {
    var before = catalog();
    try (var connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try (var statement = connection.createStatement()) {
        statement.execute("LOCK TABLE orchid_groups IN ACCESS EXCLUSIVE MODE");
        var result = run("validate-domain-constraint.sql", TARGETS.getFirst().name(), null);
        assertThat(result.getExitCode()).isNotZero();
        assertThat(result.getStderr()).contains("lock timeout");
      } finally {
        connection.rollback();
      }
    }
    assertThat(catalog()).isEqualTo(before);
  }

  @Test
  void validationCanFinishWhileAValidRowUpdateHoldsItsWriteLock() throws Exception {
    var target = TARGETS.getFirst();
    try (var connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try (var statement = connection.createStatement()) {
        statement.executeUpdate("UPDATE orchid_groups SET quantity = 99 WHERE id = 900");
        var result = readSuccess(run("validate-domain-constraint.sql", target.name(), null));
        assertThat(result.getFirst().path("validated").asBoolean()).isTrue();
        connection.commit();
      } finally {
        connection.rollback();
      }
    }
    assertThat(
            jdbc.queryForObject("SELECT quantity FROM orchid_groups WHERE id = 900", Integer.class))
        .isEqualTo(99);
    assertThat(reportFor(target.name()).path("violationCount").asInt()).isZero();
  }

  private void createReader() {
    readerRole = "constraint_reader_" + UUID.randomUUID().toString().replace("-", "");
    jdbc.execute("CREATE ROLE " + readerRole + " LOGIN");
    jdbc.execute("GRANT USAGE ON SCHEMA public TO " + readerRole);
    jdbc.execute("GRANT SELECT ON orchid_groups, sales_slips, sales_slip_items TO " + readerRole);
  }

  private void installLegacyViolation(Target target) {
    String original = definition(target);
    String reservation = definition(TARGETS.get(1));
    jdbc.execute("ALTER TABLE " + target.table() + " DROP CONSTRAINT " + target.name());
    // A negative quantity also violates the reservation bound; preserve both legacy checks.
    if (target.equals(TARGETS.getFirst()))
      jdbc.execute("ALTER TABLE orchid_groups DROP CONSTRAINT " + TARGETS.get(1).name());
    jdbc.execute("UPDATE " + target.table() + " SET " + target.invalidUpdate() + " WHERE id = 900");
    addNotValid(target, original);
    if (target.equals(TARGETS.getFirst())) addNotValid(TARGETS.get(1), reservation);
  }

  private String definition(Target target) {
    return jdbc.queryForObject(
        "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = ?",
        String.class,
        target.name());
  }

  private void addNotValid(Target target, String definition) {
    jdbc.execute(
        "ALTER TABLE " + target.table() + " ADD CONSTRAINT " + target.name() + " " + definition);
  }

  private ExecResult run(String script, String constraint, String role) throws Exception {
    var command =
        new ArrayList<>(
            List.of(
                "psql",
                "-XAtq",
                "--username",
                role == null ? POSTGRES.getUsername() : role,
                "--dbname",
                database,
                "--file",
                "/domain-constraints/" + script));
    if (constraint != null) {
      command.add("--set");
      command.add("constraint=" + constraint);
    }
    return POSTGRES.execInContainer(command.toArray(String[]::new));
  }

  private List<JsonNode> readSuccess(ExecResult result) throws Exception {
    assertThat(result.getExitCode()).withFailMessage(result.getStderr()).isZero();
    var reports = new ArrayList<JsonNode>();
    for (String line : result.getStdout().lines().toList()) {
      if (line.startsWith("{")) reports.add(mapper.readTree(line));
    }
    return reports;
  }

  private JsonNode reportFor(String name) throws Exception {
    return readSuccess(run("audit-domain-constraints.sql", null, null)).stream()
        .filter(report -> report.path("constraint").asText().equals(name))
        .findFirst()
        .orElseThrow();
  }

  private Map<String, List<String>> rows() {
    var rows = new LinkedHashMap<String, List<String>>();
    for (String table :
        jdbc.queryForList(
            "SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename",
            String.class)) {
      rows.put(
          table,
          jdbc.queryForList(
              "SELECT to_jsonb(r)::text FROM public." + table + " r ORDER BY 1", String.class));
    }
    return rows;
  }

  private List<String> catalog() {
    return jdbc.queryForList(
        """
        SELECT jsonb_build_object('table', conrelid::regclass::text, 'name', conname,
            'validated', convalidated, 'definition', pg_get_constraintdef(oid))::text
        FROM pg_constraint WHERE conname LIKE 'ck_orchid_groups_%' OR conname LIKE 'ck_sales_%'
        ORDER BY conname
        """,
        String.class);
  }

  record Target(String table, String name, String invalidUpdate) {}
}
