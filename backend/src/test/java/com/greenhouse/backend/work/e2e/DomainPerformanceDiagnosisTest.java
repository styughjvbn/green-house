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
import com.greenhouse.backend.sales.application.auction.settlement.AuctionSettlementRebuildService;
import com.greenhouse.backend.sales.repository.partner.BusinessPartnerRepository;
import com.greenhouse.backend.support.BenchmarkRuntimeMeasurement;
import com.greenhouse.backend.support.JdbcMeasurement;
import com.greenhouse.backend.support.JdbcTransactionMeasurement;
import com.greenhouse.backend.support.OrchidGroupLedgerTestFixture;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
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
  @Autowired AuctionSettlementRebuildService rebuild;
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
    assertThat(scope).isIn("all", "ledger", "work", "stats", "plans");
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
      for (String mode :
          scope.equals("ledger") || scope.equals("work")
              ? List.<String>of()
              : scope.equals("stats") || scope.equals("plans")
                  ? List.of("stale-house-statistics", "stale-house-refreshed")
                  : List.of(
                      "stale-house-statistics",
                      "stale-house-refreshed",
                      "standard-prefix",
                      "all-analyzed",
                      "force-custom")) {
        setting("plan_cache_mode", mode.equals("force-custom") ? "force_custom_plan" : "auto");
        setting("jit", "on");
        if (mode.equals("standard-prefix")) {
          for (int[] previous :
              List.of(
                  new int[] {1, 100},
                  new int[] {1, 1000},
                  new int[] {1, 10000},
                  new int[] {50, 20})) {
            for (int pass = 0; pass < 4; pass++) {
              fixture.settlement(previous[0], previous[1]);
              rebuild.rebuildExistingResults();
              rebuild.rebuildExistingResults();
            }
          }
        }
        for (int index = 0; index < 2; index++) {
          if (mode.startsWith("stale-house")) {
            fixture.settlement(1, 10000);
            analyzeSettlementTables();
          }
          fixture.settlement(501, 20);
          if (mode.equals("all-analyzed") || mode.equals("stale-house-refreshed"))
            analyzeSettlementTables();
          var preflight = preflightPlans();
          var sample = begin("settlement-" + mode, index);
          sample.put("preflightPlans", preflight);
          if (scope.equals("plans")) {
            write();
            break;
          }
          counters.start();
          var runtime = new BenchmarkRuntimeMeasurement();
          long start = System.nanoTime();
          int changed;
          try {
            changed = rebuild.rebuildExistingResults();
          } finally {
            sample.put("elapsedNanos", System.nanoTime() - start);
            sample.put("runtime", runtime.stop());
            sample.put("jdbc", counters.stop());
          }
          assertThat(changed).isEqualTo(501);
          assertThat(
                  jdbc.queryForObject("select count(*) from auction_settlement_lines", Long.class))
              .isEqualTo(10020);
          sample.put("sql", sql());
          sample.put("cachedPlans", cachedPlans());
          write();
        }
      }
      if (scope.equals("stats") || scope.equals("plans")) {
        report.put("status", "PASSED");
        return;
      }
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

  private void analyzeSettlementTables() {
    for (String table :
        List.of(
            "auction_shipments",
            "auction_shipment_lots",
            "auction_attempts",
            "auction_result_lines",
            "auction_settlements",
            "auction_settlement_lines",
            "business_partners",
            "partner_settlement_settings")) jdbc.execute("analyze " + table);
  }

  private Map<String, Object> preflightPlans() throws Exception {
    long house =
        jdbc.queryForObject(
            "select max(id) from business_partners where partner_type='AUCTION_HOUSE'", Long.class);
    long maximum = jdbc.queryForObject("select max(id) from auction_result_lines", Long.class);
    shapes.start();
    assertThat(auctionReader.getSoldResultLinesUpTo(house, LocalDate.of(2045, 1, 1), maximum))
        .hasSize(20);
    var captured = shapes.stop();
    String sql =
        captured.stream()
            .filter(
                row -> row.contains("from auction_result_lines") && row.contains("auction_date"))
            .findFirst()
            .orElseThrow();
    assertThat(QueryShapeCapture.maxParameters(List.of(sql))).isEqualTo(3);
    var parts = sql.split("\\?", -1);
    var prepared = new StringBuilder(parts[0]);
    for (int i = 1; i < parts.length; i++) prepared.append('$').append(i).append(parts[i]);
    var result = new LinkedHashMap<String, Object>();
    result.put("sourceSql", sql);
    result.put(
        "relationStats",
        jdbc.queryForList(
            "select relname,reltuples::bigint,relpages from pg_class where relname in ('auction_shipments','auction_shipment_lots','auction_attempts','auction_result_lines') order by relname"));
    try (var connection = source.getConnection();
        var statement = connection.createStatement()) {
      String original;
      try (var rows = statement.executeQuery("show plan_cache_mode")) {
        rows.next();
        original = rows.getString(1);
      }
      statement.execute("prepare diagnosis_source(date,bigint,bigint) as " + prepared);
      try {
        for (String planMode : List.of("force_custom_plan", "force_generic_plan")) {
          statement.execute("set plan_cache_mode=" + planMode);
          try (var rows =
              statement.executeQuery(
                  "explain (analyze,buffers,format json) execute diagnosis_source('2045-01-01',"
                      + house
                      + ","
                      + maximum
                      + ")")) {
            rows.next();
            result.put(planMode, objectMapper.readTree(rows.getString(1)));
          }
        }
      } finally {
        statement.execute("deallocate diagnosis_source");
        statement.execute("set plan_cache_mode=" + original);
      }
    }
    return result;
  }

  private List<Map<String, Object>> cachedPlans() throws Exception {
    var result = new ArrayList<Map<String, Object>>();
    try (var connection = source.getConnection();
        var statement = connection.createStatement()) {
      var rows = new ArrayList<Map<String, Object>>();
      try (var cursor =
          statement.executeQuery(
              "select name,statement,parameter_types::text,generic_plans,custom_plans from pg_prepared_statements where statement like '%from auction_result_lines%' or statement like '%from auction_settlement_lines%'")) {
        while (cursor.next()) {
          var row = new LinkedHashMap<String, Object>();
          row.put("name", cursor.getString(1));
          row.put("sql", cursor.getString(2));
          row.put("parameterTypes", cursor.getString(3));
          row.put("genericPlans", cursor.getLong(4));
          row.put("customPlans", cursor.getLong(5));
          rows.add(row);
        }
      }
      long house =
          jdbc.queryForObject(
              "select max(id) from business_partners where partner_type='AUCTION_HOUSE'",
              Long.class);
      long maximum = jdbc.queryForObject("select max(id) from auction_result_lines", Long.class);
      for (var row : rows) {
        String types = (String) row.get("parameterTypes");
        String sql = (String) row.get("sql");
        String args = null;
        if (types.equals("{date,bigint,bigint}") && sql.contains("auction_date")) {
          args = "'2045-01-01'," + house + "," + maximum;
        } else if (sql.contains("from auction_settlement_lines")
            && sql.contains("auction_result_line_id in")
            && types.matches("\\{bigint(?:,bigint)*\\}")) {
          int size = types.substring(1, types.length() - 1).split(",").length;
          if (size <= 500)
            args =
                LongStream.range(97000001, 97000001 + size)
                    .mapToObj(Long::toString)
                    .collect(Collectors.joining(","));
        }
        if (args != null) {
          String name = '"' + ((String) row.get("name")).replace("\"", "\"\"") + '"';
          String explain =
              "explain (analyze,buffers,format json) execute " + name + "(" + args + ")";
          try (var plan = statement.executeQuery(explain)) {
            plan.next();
            row.put("observedPlan", objectMapper.readTree(plan.getString(1)));
          }
          if (sql.contains("from auction_settlement_lines")) {
            String original;
            try (var value = statement.executeQuery("show plan_cache_mode")) {
              value.next();
              original = value.getString(1);
            }
            try {
              statement.execute("set plan_cache_mode=force_custom_plan");
              try (var plan = statement.executeQuery(explain)) {
                plan.next();
                row.put("forcedCustomPlan", objectMapper.readTree(plan.getString(1)));
              }
            } finally {
              statement.execute("set plan_cache_mode=" + original);
            }
          }
        }
        result.add(row);
      }
    }
    return result;
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
