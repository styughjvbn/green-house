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

@Tag("work-e2e")
class OrchidGroupLedgerCliPostgresE2ETest extends WorkE2ETestBase {
  @Autowired private WorkTestDataSeeder seeder;
  @Autowired private OrchidGroupLedgerTestFixture fixture;
  @Autowired private JdbcTemplate jdbc;
  @TempDir Path directory;

  @Test
  void relocatedClisVerifyExistingActiveLedgerWithoutChangingFacts() throws Exception {
    seeder.reset();
    seeder.seedContractScenario();
    UUID key = UUID.randomUUID();
    fixture.seedBaseline(key, LocalDate.of(2026, 8, 20), "2.0.0");
    assertThat(fixture.activate(key).ready()).isTrue();
    var before = facts();

    var report = objectMapper.readTree(run(OrchidGroupLedgerReconciliationCli.class));
    assertThat(report.path("stage").asText()).isEqualTo("ACTIVE");
    assertThat(report.path("ready").asBoolean()).isTrue();
    assertThat(report.path("issues").isArray()).isTrue();
    assertThat(report.path("issues").size()).isZero();
    assertThat(run(OrchidGroupLedgerStartupVerificationCli.class))
        .contains("OrchidGroup ledger startup verification passed.");
    assertThat(facts()).isEqualTo(before);
  }

  private String run(Class<?> entryPoint) throws Exception {
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
    builder.environment().put("DATABASE_URL", POSTGRES.getJdbcUrl());
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

  private Map<String, List<Map<String, Object>>> facts() {
    var result = new LinkedHashMap<String, List<Map<String, Object>>>();
    for (String table :
        List.of(
            "orchid_groups",
            "orchid_group_mutations",
            "orchid_group_mutation_entries",
            "orchid_group_mutation_relations",
            "orchid_group_ledger_coverages")) {
      result.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id"));
    }
    return result;
  }
}
