package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.farm.mutation.verification.OrchidGroupLedgerReconciliationCli;
import com.greenhouse.backend.farm.mutation.verification.OrchidGroupLedgerStartupVerificationCli;
import com.greenhouse.backend.support.OrchidGroupLedgerTestFixture;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Tag("work-e2e")
class OrchidGroupLedgerCliPostgresE2ETest extends WorkE2ETestBase {
  @Autowired private WorkTestDataSeeder seeder;
  @Autowired private OrchidGroupLedgerTestFixture fixture;
  @Autowired private JdbcTemplate jdbc;
  @TempDir Path directory;

  @Test
  void relocatedClisVerifyRestoredActiveLedgerWithoutChangingFacts() throws Exception {
    seeder.reset();
    seeder.seedContractScenario();
    UUID key = UUID.randomUUID();
    fixture.seedBaseline(key, LocalDate.of(2026, 8, 20), "2.0.0");
    assertThat(fixture.activate(key).ready()).isTrue();
    var original = facts(jdbc);
    String restoredDatabase = "greenhouse_cli_restore";
    String restoredUrl =
        POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + restoredDatabase);
    try {
      executeInPostgres(
          "pg_dump",
          "-U",
          POSTGRES.getUsername(),
          "-Fc",
          "-f",
          "/tmp/ledger.dump",
          POSTGRES.getDatabaseName());
      executeInPostgres("createdb", "-U", POSTGRES.getUsername(), restoredDatabase);
      executeInPostgres(
          "pg_restore",
          "-U",
          POSTGRES.getUsername(),
          "--exit-on-error",
          "--no-owner",
          "--no-privileges",
          "-d",
          restoredDatabase,
          "/tmp/ledger.dump");
      var restored =
          new JdbcTemplate(
              new DriverManagerDataSource(
                  restoredUrl, POSTGRES.getUsername(), POSTGRES.getPassword()));
      assertThat(facts(restored)).isEqualTo(original);

      var report =
          objectMapper.readTree(run(OrchidGroupLedgerReconciliationCli.class, restoredUrl));
      assertThat(report.path("stage").asText()).isEqualTo("ACTIVE");
      assertThat(report.path("ready").asBoolean()).isTrue();
      assertThat(report.path("issues").isArray()).isTrue();
      assertThat(report.path("issues").size()).isZero();
      assertThat(run(OrchidGroupLedgerStartupVerificationCli.class, restoredUrl))
          .contains("OrchidGroup ledger startup verification passed.");
      assertThat(facts(restored)).isEqualTo(original);
      assertThat(facts(jdbc)).isEqualTo(original);
    } finally {
      executeInPostgres(
          "dropdb", "-U", POSTGRES.getUsername(), "--if-exists", "--force", restoredDatabase);
    }
  }

  private void executeInPostgres(String... command) throws Exception {
    var result = POSTGRES.execInContainer(command);
    assertThat(result.getExitCode())
        .as("PostgreSQL utility failed: %s", result.getStderr())
        .isZero();
  }

  private String run(Class<?> entryPoint, String databaseUrl) throws Exception {
    String classpath = System.getProperty("greenhouse.cli.runtime-classpath");
    assertThat(classpath).isNotBlank();
    Path output = directory.resolve(entryPoint.getSimpleName() + ".log");
    var builder =
        new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xmx512m",
                "-cp",
                classpath,
                entryPoint.getName(),
                "--spring.main.banner-mode=off",
                "--logging.level.root=ERROR",
                "--logging.level.com.greenhouse.backend=ERROR",
                "--logging.level.org.hibernate.SQL=OFF",
                "--logging.level.org.hibernate.orm.jdbc.bind=OFF",
                "--app.auth.enabled=false",
                "--debug=false")
            .redirectErrorStream(true)
            .redirectOutput(output.toFile());
    builder.environment().put("DATABASE_URL", databaseUrl);
    builder.environment().put("DATABASE_USERNAME", POSTGRES.getUsername());
    builder.environment().put("DATABASE_PASSWORD", POSTGRES.getPassword());
    var process = builder.start();
    boolean completed = process.waitFor(90, TimeUnit.SECONDS);
    if (!completed) process.destroyForcibly();
    String log = Files.readString(output);
    assertThat(completed).as("CLI timed out: %s", log).isTrue();
    assertThat(process.exitValue()).as("CLI failed: %s", log).isZero();
    return log;
  }

  private Map<String, List<String>> facts(JdbcTemplate database) {
    var result = new LinkedHashMap<String, List<String>>();
    var tables =
        database.queryForList(
            "SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename",
            String.class);
    for (String table : tables) {
      String identifier = table.replace("\"", "\"\"");
      result.put(
          table,
          database.queryForList(
              "SELECT to_jsonb(row)::text FROM public.\""
                  + identifier
                  + "\" row ORDER BY to_jsonb(row)::text",
              String.class));
    }
    result.put(
        "sequences",
        database.queryForList(
            "SELECT to_jsonb(row)::text FROM pg_sequences row WHERE schemaname = 'public' "
                + "ORDER BY sequencename",
            String.class));
    return result;
  }
}
