package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerReconciliationReport;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerReconciliationService;
import com.greenhouse.backend.farm.orchid.repository.OrchidGroupRepository;
import com.greenhouse.backend.sales.partner.repository.BusinessPartnerRepository;
import com.greenhouse.backend.support.BenchmarkRuntimeMeasurement;
import com.greenhouse.backend.support.JdbcMeasurement;
import com.greenhouse.backend.support.JdbcTransactionMeasurement;
import com.greenhouse.backend.support.OrchidGroupLedgerTestFixture;
import com.sun.management.OperatingSystemMXBean;
import jakarta.persistence.EntityManagerFactory;
import java.lang.management.ManagementFactory;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.DockerClientFactory;

@Tag("domain-benchmark")
@Timeout(value = 60, unit = TimeUnit.MINUTES)
@TestPropertySource(
    properties = {"logging.level.root=WARN", "logging.level.org.hibernate.stat=OFF"})
@Import({JdbcMeasurement.Configuration.class, JdbcTransactionMeasurement.Configuration.class})
class DomainPerformanceBenchmarkTest extends WorkE2ETestBase {
  @Autowired WorkTestDataSeeder seeder;
  @Autowired JdbcTemplate jdbc;
  @Autowired DataSource dataSource;
  @Autowired BusinessPartnerRepository partners;
  @Autowired OrchidGroupRepository groups;
  @Autowired OrchidGroupLedgerTestFixture ledgerFixture;
  @Autowired OrchidGroupLedgerReconciliationService reconciliation;
  @Autowired EntityManagerFactory emf;
  @Autowired JdbcMeasurement jdbcMeasurement;
  @Autowired JdbcTransactionMeasurement transactionMeasurement;

  private final Map<String, Object> report = new LinkedHashMap<>();
  private final List<Map<String, Object>> scenarios = new ArrayList<>();
  private final Path output =
      Path.of(System.getProperty("domainBenchmark.outputDir", "build/domain-benchmark/direct"));

  @Test
  void measuresLedgerWithFreshFixturesAndExportsResults() throws Exception {
    String profile = choice("profile", "standard", List.of("smoke", "standard", "large"));
    String selection = choice("scenario", "all", List.of("all", "ledger"));
    int warmup = integer("warmup", 1, 0, 20);
    int samples = integer("samples", 3, 1, 30);
    report.put("schemaVersion", 1);
    report.put("status", "RUNNING");
    report.put("profile", profile);
    report.put("scenarioSelection", selection);
    report.put("warmupCount", warmup);
    report.put("sampleCount", samples);
    report.put("environment", environment());
    report.put("scenarios", scenarios);
    writeReport();
    try {
      // All destructive fixture operations must target this test's newly created container.
      try (var connection = dataSource.getConnection()) {
        if (!connection.getMetaData().getURL().equals(POSTGRES.getJdbcUrl()))
          throw new IllegalStateException("Domain benchmark requires its Testcontainers database");
      }
      var fixture = new DomainPerformanceFixture(jdbc, seeder, partners, ledgerFixture);
      for (Case scenario : cases(profile)) {
        if (!selection.equals("all") && !selection.equals(scenario.family())) continue;
        var result = new LinkedHashMap<String, Object>();
        var measurements = new ArrayList<Map<String, Object>>();
        result.put("fixture", scenario);
        result.put("status", "RUNNING");
        result.put("measurements", measurements);
        scenarios.add(result);
        for (int index = 0; index < warmup + samples; index++) {
          System.out.printf(
              "[domain-benchmark] %s: %s %d/%d%n",
              scenario.name(),
              index < warmup ? "warmup" : "sample",
              index < warmup ? index + 1 : index - warmup + 1,
              index < warmup ? warmup : samples);
          fixture.ledger(
              scenario.groups(),
              scenario.revisions(),
              scenario.workReferences(),
              scenario.errors());
          if (index < warmup) verifyLedger(scenario, reconciliation.reconcile());
          else
            measure(
                measurements,
                "reconcile",
                index - warmup,
                reconciliation::reconcile,
                state -> verifyLedger(scenario, state));
        }
        result.put("status", "PASSED");
        writeReport();
      }
      report.put("status", "PASSED");
    } catch (Exception | AssertionError failure) {
      report.put("status", "FAILED");
      report.put("failureType", failure.getClass().getName());
      throw failure;
    } finally {
      writeReport();
    }
  }

  private <T> void measure(
      List<Map<String, Object>> measurements,
      String phase,
      int index,
      Supplier<T> operation,
      Function<T, Map<String, Object>> verifier)
      throws Exception {
    var stats = emf.unwrap(SessionFactory.class).getStatistics();
    stats.clear();
    jdbcMeasurement.start();
    transactionMeasurement.start();
    var runtime = new BenchmarkRuntimeMeasurement();
    var sample = new LinkedHashMap<String, Object>();
    sample.put("phase", phase);
    sample.put("sampleIndex", index);
    sample.put("status", "RUNNING");
    measurements.add(sample);
    long start = System.nanoTime();
    T value;
    JdbcTransactionMeasurement.Sample transactions;
    try {
      value = operation.get();
    } catch (RuntimeException | Error failure) {
      sample.put("status", "FAILED");
      sample.put("failureType", failure.getClass().getName());
      throw failure;
    } finally {
      sample.put("elapsedNanos", System.nanoTime() - start);
      sample.put("runtime", runtime.stop());
      sample.put("jdbc", jdbcMeasurement.stop());
      transactions = transactionMeasurement.stop();
      sample.put("transactions", transactions);
      sample.put("hibernatePreparedStatements", stats.getPrepareStatementCount());
      sample.put("hibernateEntityLoads", stats.getEntityLoadCount());
      sample.put("hibernateFlushes", stats.getFlushCount());
      writeReport();
    }
    try {
      assertThat(transactions.openAtEnd()).as("unfinished JDBC transaction windows").isZero();
      assertThat(transactions.closedWithoutObservedCompletion()).isZero();
      sample.put("outcome", verifier.apply(value));
      sample.put("status", "PASSED");
    } catch (RuntimeException | AssertionError failure) {
      sample.put("status", "FAILED");
      sample.put("failureType", failure.getClass().getName());
      throw failure;
    } finally {
      writeReport();
    }
  }

  private Map<String, Object> verifyLedger(
      Case scenario, OrchidGroupLedgerReconciliationReport state) {
    assertThat(state.orchidGroupCount()).isEqualTo(scenario.groups());
    assertThat(state.entryCount()).isEqualTo((long) scenario.groups() * (scenario.revisions() + 1));
    assertThat(state.ready()).isEqualTo(!scenario.errors());
    assertThat(state.issues()).hasSize(scenario.errors() ? scenario.groups() : 0);
    if (scenario.errors())
      assertThat(state.issues()).extracting("code").containsOnly("SNAPSHOT_CHAIN_MISMATCH");
    assertThat(state.baselineFingerprint()).hasSize(64);
    assertThat(state.currentStateFingerprint()).hasSize(64);
    return Map.of(
        "groups",
        state.orchidGroupCount(),
        "entries",
        state.entryCount(),
        "mutations",
        state.mutationCount(),
        "ready",
        state.ready(),
        "issues",
        state.issues().size(),
        "stage",
        state.stage().name(),
        "workCorrections",
        scenario.workReferences() ? (long) scenario.groups() * scenario.revisions() : 0);
  }

  private Map<String, Object> environment() {
    var os = ManagementFactory.getOperatingSystemMXBean();
    var result = new LinkedHashMap<String, Object>();
    result.put("javaVersion", System.getProperty("java.version"));
    result.put("javaVendor", System.getProperty("java.vendor"));
    result.put("osName", os.getName());
    result.put("osVersion", os.getVersion());
    result.put("architecture", os.getArch());
    result.put("availableProcessors", os.getAvailableProcessors());
    result.put("maxHeapBytes", Runtime.getRuntime().maxMemory());
    result.put(
        "physicalMemoryBytes",
        os instanceof OperatingSystemMXBean system ? system.getTotalMemorySize() : null);
    result.put("postgresImage", POSTGRES.getDockerImageName());
    result.put("postgresVersion", jdbc.queryForObject("show server_version", String.class));
    result.put(
        "postgresSettings",
        jdbc.queryForList(
            """
        select name, setting, unit from pg_settings
        where name in ('shared_buffers','work_mem','effective_cache_size',
                      'max_connections','max_parallel_workers_per_gather') order by name
        """));
    var engine = DockerClientFactory.instance().client().infoCmd().exec();
    var docker = new LinkedHashMap<String, Object>();
    docker.put("serverVersion", engine.getServerVersion());
    docker.put("cpus", engine.getNCPU());
    docker.put("memoryBytes", engine.getMemTotal());
    docker.put("architecture", engine.getArchitecture());
    docker.put("operatingSystem", engine.getOperatingSystem());
    var limits = POSTGRES.getContainerInfo().getHostConfig();
    docker.put("postgresMemoryLimitBytes", limits.getMemory());
    docker.put("postgresNanoCpuLimit", limits.getNanoCPUs());
    result.put("docker", docker);
    result.put(
        "garbageCollectors",
        ManagementFactory.getGarbageCollectorMXBeans().stream()
            .map(bean -> bean.getName())
            .toList());
    return result;
  }

  private void writeReport() throws Exception {
    Files.createDirectories(output);
    var temporary = output.resolve("benchmark-report.tmp");
    objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), report);
    try {
      Files.move(
          temporary,
          output.resolve("benchmark-report.json"),
          StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException unsupported) {
      Files.move(
          temporary, output.resolve("benchmark-report.json"), StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static String choice(String property, String fallback, List<String> choices) {
    String value = System.getProperty("domainBenchmark." + property, fallback);
    if (!choices.contains(value))
      throw new IllegalArgumentException("Invalid benchmark " + property);
    return value;
  }

  private static int integer(String property, int fallback, int minimum, int maximum) {
    int value =
        Integer.parseInt(
            System.getProperty("domainBenchmark." + property, Integer.toString(fallback)));
    if (value < minimum || value > maximum)
      throw new IllegalArgumentException("Invalid benchmark " + property);
    return value;
  }

  private record Case(
      String name,
      String family,
      int keys,
      int resultsPerKey,
      int groups,
      int revisions,
      boolean workReferences,
      boolean errors) {
    static Case ledger(int groups, int revisions, boolean references, boolean errors) {
      return new Case(
          "ledger-"
              + groups
              + "x"
              + revisions
              + (references ? "-work" : "")
              + (errors ? "-errors" : ""),
          "ledger",
          0,
          0,
          groups,
          revisions,
          references,
          errors);
    }
  }

  private static List<Case> cases(String profile) {
    return switch (profile) {
      case "smoke" ->
          List.of(
              Case.ledger(3, 2, false, false),
              Case.ledger(3, 2, true, false),
              Case.ledger(3, 2, false, true));
      case "standard" ->
          List.of(
              Case.ledger(500, 1, false, false),
              Case.ledger(5000, 10, false, false),
              Case.ledger(1, 50001, false, false),
              Case.ledger(500, 10, true, false),
              Case.ledger(5000, 1, false, true));
      case "large" ->
          List.of(
              Case.ledger(20000, 20, false, false),
              Case.ledger(1, 500001, false, false),
              Case.ledger(5000, 20, true, false),
              Case.ledger(20000, 1, false, true));
      default -> throw new IllegalArgumentException("Unknown benchmark profile");
    };
  }
}
