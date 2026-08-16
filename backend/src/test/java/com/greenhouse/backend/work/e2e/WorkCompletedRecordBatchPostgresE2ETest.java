package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("work-e2e")
class WorkCompletedRecordBatchPostgresE2ETest extends WorkE2ETestBase {

	private static final int TARGET_COUNT = 120;

	@Autowired WorkTestDataSeeder seeder;
	@Autowired EntityManagerFactory entityManagerFactory;

	@BeforeEach
	void setUp() {
		seeder.reset();
		seeder.seedBenchmark(1, TARGET_COUNT);
	}

	@Test
	void createsFarmCompletedRecordWithoutPerTargetQueries() throws Exception {
		long workTypeId = pesticideWorkTypeId();
		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();

		ApiResult recorded = post("/api/work-operations/record", """
				{
				  "workTypeId": %d,
				  "title": "농장 전체 배치 기록",
				  "plannedStartDate": "2026-07-15",
				  "sourceScopeType": "FARM",
				  "details": {"materialName": "배치 살균제"},
				  "worker": "E2E 기록자"
				}
				""".formatted(workTypeId));

		assertThat(recorded.status()).isEqualTo(201);
		assertThat(recorded.data().path("status").asText()).isEqualTo("COMPLETED");
		assertThat(recorded.data().path("targets")).hasSize(TARGET_COUNT);
		assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(30L);
	}

	private long pesticideWorkTypeId() throws Exception {
		ApiResult workTypes = get("/api/work-types");
		for (JsonNode workType : workTypes.data()) {
			if ("PESTICIDE".equals(workType.path("code").asText())) {
				return workType.path("id").asLong();
			}
		}
		throw new AssertionError("PESTICIDE 작업 유형을 찾을 수 없습니다.");
	}
}
