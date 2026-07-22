package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManagerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("work-benchmark")
class WorkOperationBenchmarkTest extends WorkE2ETestBase {

	private static final int OPERATION_COUNT = 100;
	private static final int TARGETS_PER_OPERATION = 20;
	private static final int WARMUP_COUNT = 3;
	private static final int SAMPLE_COUNT = 20;
	private static final long LIST_MAX_QUERY_COUNT = 4;
	private static final long DETAIL_MAX_QUERY_COUNT = 4;
	private static final long HISTORY_MAX_QUERY_COUNT = 5;
	private static final boolean ENFORCE_QUERY_LIMITS =
			Boolean.getBoolean("workBenchmark.enforceQueryLimits");

	@Autowired
	private WorkTestDataSeeder seeder;
	@Autowired
	private EntityManagerFactory entityManagerFactory;
	private WorkTestDataSeeder.BenchmarkScenario scenario;

	@BeforeEach
	void setUp() {
		seeder.reset();
		scenario = seeder.seedBenchmark(OPERATION_COUNT, TARGETS_PER_OPERATION);
	}

	@Test
	void measuresMajorWorkQueryApis() throws Exception {
		List<Map<String, Object>> measurements = List.of(
				measure(
						"work-operation-list-100",
						"/api/work-operations?view=ALL",
						LIST_MAX_QUERY_COUNT,
						this::assertListResponse),
				measure(
						"work-operation-detail-20-targets",
						"/api/work-operations/%d".formatted(scenario.firstOperationId()),
						DETAIL_MAX_QUERY_COUNT,
						this::assertDetailResponse),
				measure(
						"orchid-group-work-history",
						"/api/orchid-groups/%d/work-history".formatted(scenario.firstOrchidGroupId()),
						HISTORY_MAX_QUERY_COUNT,
						this::assertHistoryResponse));

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("operationCount", scenario.operationCount());
		result.put("targetCount", scenario.targetCount());
		result.put("targetsPerOperation", TARGETS_PER_OPERATION);
		result.put("warmupCount", WARMUP_COUNT);
		result.put("sampleCount", SAMPLE_COUNT);
		result.put("queryLimitsEnforced", ENFORCE_QUERY_LIMITS);
		result.put("scenarios", measurements);

		Path report = Path.of("build", "work-benchmark", "results.json");
		Files.createDirectories(report.getParent());
		objectMapper.writerWithDefaultPrettyPrinter().writeValue(report.toFile(), result);
	}

	private Map<String, Object> measure(
			String name,
			String path,
			long queryCountLimit,
			Consumer<ApiResult> responseAssertion) throws Exception {
		for (int index = 0; index < WARMUP_COUNT; index++) {
			responseAssertion.accept(get(path));
		}

		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();
		responseAssertion.accept(get(path));
		long queryCount = statistics.getPrepareStatementCount();

		List<Double> responseTimesMs = new ArrayList<>(SAMPLE_COUNT);
		for (int index = 0; index < SAMPLE_COUNT; index++) {
			long startedAt = System.nanoTime();
			responseAssertion.accept(get(path));
			responseTimesMs.add((System.nanoTime() - startedAt) / 1_000_000.0);
		}
		responseTimesMs.sort(Double::compareTo);

		if (ENFORCE_QUERY_LIMITS) {
			assertThat(queryCount)
					.as("%s 쿼리 수가 일괄 조회 상한을 지켜야 합니다", name)
					.isLessThanOrEqualTo(queryCountLimit);
		}

		Map<String, Object> measurement = new LinkedHashMap<>();
		measurement.put("name", name);
		measurement.put("endpoint", "GET " + path);
		measurement.put("queryCount", queryCount);
		measurement.put("queryCountLimit", queryCountLimit);
		measurement.put("medianMs", percentile(responseTimesMs, 0.50));
		measurement.put("p95Ms", percentile(responseTimesMs, 0.95));
		measurement.put("samplesMs", responseTimesMs);
		return measurement;
	}

	private void assertListResponse(ApiResult response) {
		assertThat(response.status()).isEqualTo(200);
		assertThat(response.data()).hasSize(OPERATION_COUNT);
		assertThat(response.data().get(0).path("targets")).hasSize(TARGETS_PER_OPERATION);
	}

	private void assertDetailResponse(ApiResult response) {
		assertThat(response.status()).isEqualTo(200);
		assertThat(response.data().path("id").asLong()).isEqualTo(scenario.firstOperationId());
		assertThat(response.data().path("targets")).hasSize(TARGETS_PER_OPERATION);
	}

	private void assertHistoryResponse(ApiResult response) {
		assertThat(response.status()).isEqualTo(200);
		assertThat(response.data()).hasSize(1);
		assertThat(response.data().get(0).path("workOperationId").asLong())
				.isEqualTo(scenario.firstOperationId());
	}

	private double percentile(List<Double> sortedValues, double percentile) {
		int index = Math.max(0, (int) Math.ceil(sortedValues.size() * percentile) - 1);
		return sortedValues.get(index);
	}
}
