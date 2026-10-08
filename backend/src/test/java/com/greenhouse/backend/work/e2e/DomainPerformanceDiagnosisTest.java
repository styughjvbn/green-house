package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerReconciliationReport;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerReconciliationService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationFingerprint;
import com.greenhouse.backend.sales.application.auction.AuctionDataReader;
import com.greenhouse.backend.sales.partner.repository.BusinessPartnerRepository;
import com.greenhouse.backend.support.BenchmarkRuntimeMeasurement;
import com.greenhouse.backend.support.JdbcMeasurement;
import com.greenhouse.backend.support.JdbcTransactionMeasurement;
import com.greenhouse.backend.support.OrchidGroupLedgerTestFixture;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Manual investigation: profiling changes timing and is separate from regression/benchmark. */
@Tag("domain-diagnosis")
@Timeout(value = 15, unit = TimeUnit.MINUTES)
@TestPropertySource(
    properties = {"logging.level.root=WARN", "logging.level.org.hibernate.stat=OFF"})
@Import({
  JdbcMeasurement.Configuration.class,
  JdbcTransactionMeasurement.Configuration.class,
  QueryShapeCapture.Configuration.class
})
@ActiveProfiles("e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DomainPerformanceDiagnosisTest {
  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:18-alpine")
          .withCommand(
              "postgres",
              "-c",
              "shared_preload_libraries=pg_stat_statements",
              "-c",
              "pg_stat_statements.track_planning=on",
              "-c",
              "pg_stat_statements.track=all",
              "-c",
              "track_io_timing=on");

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  @Autowired WorkTestDataSeeder seeder;
  @Autowired JdbcTemplate jdbc;
  @Autowired DataSource source;
  @Autowired BusinessPartnerRepository partners;
  @Autowired OrchidGroupLedgerTestFixture ledgerFixture;
  @Autowired OrchidGroupLedgerReconciliationService reconciliation;
  @Autowired JdbcMeasurement counters;
  @Autowired AuctionDataReader auctionReader;
  @Autowired QueryShapeCapture shapes;
  @MockitoSpyBean OrchidGroupMutationFingerprint fingerprint;
  private final List<Map<String, Object>> samples = new ArrayList<>();
  private final Map<String, Object> report = new LinkedHashMap<>();
  private final Path directory =
      Path.of(System.getProperty("diagnosis.outputDir", "build/domain-diagnosis/direct"));
  private long fingerprintNanos;

  @Test
  void isolatesSqlAndLedgerAllocationCosts() throws Exception {
    Files.createDirectories(directory);
    try (var connection = source.getConnection()) {
      assertThat(connection.getMetaData().getURL()).isEqualTo(POSTGRES.getJdbcUrl());
    }
    jdbc.execute("create extension if not exists pg_stat_statements");
    report.put("schemaVersion", 1);
    report.put("revision", System.getProperty("diagnosis.revision"));
    report.put("status", "RUNNING");
    report.put("postgresVersion", jdbc.queryForObject("show server_version", String.class));
    report.put("maxHeapBytes", Runtime.getRuntime().maxMemory());
    report.put("javaVersion", System.getProperty("java.version"));
    report.put("availableProcessors", Runtime.getRuntime().availableProcessors());
    String scope = System.getProperty("diagnosis.scope", "all");
    assertThat(scope).isIn("all", "ledger", "work");
    report.put("scope", scope);
    report.put("samples", samples);
    report.put("measuredSqlLogging", "WARN");
    var logging = LoggingSystem.get(getClass().getClassLoader());
    for (String logger :
        List.of(
            "ROOT",
            "org.springframework",
            "org.hibernate",
            "org.hibernate.SQL",
            "org.springframework.jdbc.core.JdbcTemplate",
            "com.zaxxer.hikari")) {
      logging.setLogLevel(logger, LogLevel.WARN);
    }
    var fixture = new DomainPerformanceFixture(jdbc, seeder, partners, ledgerFixture);
    try {
      setting("jit", "on");
      setting("plan_cache_mode", "auto");
      boolean work = scope.equals("work");
      fixture.ledger(work ? 500 : 5000, work ? 10 : 1, work, false);
      reset(fingerprint);
      doAnswer(
              call -> {
                long start = System.nanoTime();
                try {
                  return call.callRealMethod();
                } finally {
                  fingerprintNanos += System.nanoTime() - start;
                }
              })
          .when(fingerprint)
          .calculate(any());
      // Same rows, alternating successful and deliberately corrupted chains. No forced GC.
      for (int index = 0; index < 12; index++) {
        boolean errors = !work && (index % 4 == 1 || index % 4 == 2);
        if (!work)
          jdbc.execute(
              errors
                  ? "update orchid_group_mutation_entries set before_state=jsonb_set(after_state,'{memo}','\"측정 오류\"'::jsonb) where entry_kind='CHANGE'"
                  : "update orchid_group_mutation_entries set before_state=after_state where entry_kind='CHANGE'");
        if (index < 4) {
          assertThat(reconciliation.reconcile().ready()).isEqualTo(!errors);
          continue;
        }
        var sample =
            begin(work ? "ledger-work" : errors ? "ledger-errors" : "ledger-healthy", index - 4);
        var jfr = directory.resolve("ledger-" + (index - 4) + ".jfr");
        try (var recording = new Recording()) {
          recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(2));
          recording.start();
          fingerprintNanos = 0;
          counters.start();
          var runtime = new BenchmarkRuntimeMeasurement();
          long start = System.nanoTime();
          OrchidGroupLedgerReconciliationReport state;
          try {
            state = reconciliation.reconcile();
          } finally {
            sample.put("elapsedNanos", System.nanoTime() - start);
            sample.put("runtime", runtime.stop());
            sample.put("jdbc", counters.stop());
          }
          sample.put("fingerprintNanos", fingerprintNanos);
          recording.stop();
          recording.dump(jfr);
          assertThat(state.ready()).isEqualTo(!errors);
          assertThat(state.issues()).hasSize(errors ? 5000 : 0);
          sample.put("issues", state.issues().size());
        }
        sample.put("sql", sql());
        sample.put("executionSampleFrames", frames(jfr));
        write();
      }
      report.put("status", "PASSED");
    } catch (Exception | AssertionError failure) {
      report.put("status", "FAILED");
      report.put("failureType", failure.getClass().getName());
      throw failure;
    } finally {
      write();
    }
  }

  private Map<String, Object> begin(String name, int index) {
    System.out.printf("[domain-diagnosis] %s %d%n", name, index);
    jdbc.execute("select pg_stat_statements_reset()");
    var sample = new LinkedHashMap<String, Object>();
    sample.put("name", name);
    sample.put("index", index);
    sample.put("jit", jdbc.queryForObject("show jit", String.class));
    sample.put("planCacheMode", jdbc.queryForObject("show plan_cache_mode", String.class));
    samples.add(sample);
    return sample;
  }

  private List<Map<String, Object>> sql() {
    return jdbc.queryForList(
        """
        select query,calls,plans,total_plan_time,total_exec_time,mean_exec_time,rows,
          shared_blks_hit,shared_blks_read,jit_generation_time,jit_inlining_time,
          jit_optimization_time,jit_emission_time
        from pg_stat_statements where query not like '%pg_stat_statements%'
        order by total_exec_time desc limit 60
        """);
  }

  private void setting(String name, String value) throws Exception {
    String database = '"' + POSTGRES.getDatabaseName().replace("\"", "\"\"") + '"';
    jdbc.execute("alter database " + database + " set " + name + "=" + value);
    source.unwrap(HikariDataSource.class).getHikariPoolMXBean().softEvictConnections();
    assertThat(jdbc.queryForObject("show " + name, String.class)).isEqualTo(value);
  }

  private Map<String, Long> frames(Path file) throws Exception {
    Map<String, Long> counts = new LinkedHashMap<>();
    try (var reader = new RecordingFile(file)) {
      while (reader.hasMoreEvents()) {
        var event = reader.readEvent();
        if (event.getStackTrace() == null
            || !event.getEventType().getName().equals("jdk.ExecutionSample")) continue;
        for (var frame : event.getStackTrace().getFrames()) {
          String type = frame.getMethod().getType().getName();
          if (type.startsWith("com.greenhouse.backend") && !type.contains("Diagnosis")) {
            counts.merge(type + "." + frame.getMethod().getName(), 1L, Long::sum);
            break;
          }
        }
      }
    }
    return counts;
  }

  private void write() throws Exception {
    Files.writeString(
        directory.resolve("diagnosis.json"),
        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report) + "\n");
  }
}
