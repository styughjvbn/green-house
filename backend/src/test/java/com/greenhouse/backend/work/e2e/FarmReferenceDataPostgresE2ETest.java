package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.greenhouse.backend.farm.application.material.MaterialService;
import com.greenhouse.backend.farm.application.variety.VarietyService;
import com.greenhouse.backend.farm.domain.material.Material;
import com.greenhouse.backend.farm.domain.variety.Variety;
import com.greenhouse.backend.farm.dto.material.MaterialCreateRequest;
import com.greenhouse.backend.farm.dto.variety.VarietyCreateRequest;
import com.greenhouse.backend.farm.repository.material.MaterialRepository;
import com.greenhouse.backend.farm.repository.variety.VarietyRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@Tag("work-e2e")
class FarmReferenceDataPostgresE2ETest extends WorkE2ETestBase {

	@PersistenceContext
	EntityManager entityManager;

	@Autowired
	WorkTestDataSeeder seeder;

	@Autowired
	MaterialService materialService;

	@Autowired
	VarietyService varietyService;

	@Autowired
	JdbcTemplate jdbc;

	@MockitoSpyBean
	MaterialRepository materials;

	@MockitoSpyBean
	VarietyRepository varieties;

	@BeforeEach
	void reset() {
		seeder.reset();
	}

	@Test
	void parallelMaterialCreationDoesNotReuseACode() throws Exception {
		var barrier = new CyclicBarrier(2);
		doAnswer(invocation -> {
			barrier.await(10, TimeUnit.SECONDS);
			Object entity = invocation.getArgument(0);
			entityManager.persist(entity);
			return entity;
		}).when(materials).save(any(Material.class));
		assertThat(parallel(index -> materialService
			.create(new MaterialCreateRequest("자재", "동시 자재 " + index, null, null, null, null, null))
			.code())).doesNotHaveDuplicates().allMatch(code -> code.matches("MAT-[0-9]{4,}"));
	}

	@Test
	void parallelVarietyCreationDoesNotReuseACode() throws Exception {
		var barrier = new CyclicBarrier(2);
		doAnswer(invocation -> {
			barrier.await(10, TimeUnit.SECONDS);
			Object entity = invocation.getArgument(0);
			entityManager.persist(entity);
			return entity;
		}).when(varieties).save(any(Variety.class));
		assertThat(parallel(index -> varietyService
			.create(new VarietyCreateRequest("팔레놉시스", "동시 품종 " + index, null, null, null, true, null, null))
			.code())).doesNotHaveDuplicates().allMatch(code -> code.matches("VAR-[0-9]{4,}"));
	}

	private List<String> parallel(IntFunction<String> action) throws Exception {
		try (var executor = Executors.newFixedThreadPool(2)) {
			var results = executor.invokeAll(List.<Callable<String>>of(() -> action.apply(1), () -> action.apply(2)));
			return List.of(results.get(0).get(), results.get(1).get());
		}
	}

	@Test
	void migrationPreservesImportedCodesAndDoesNotReuseRolledBackNumbers() throws Exception {
		jdbc.execute("CREATE DATABASE reference_upgrade");
		String url = POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/reference_upgrade");
		var dataSource = new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword());
		Flyway.configure().dataSource(dataSource).target("23").load().migrate();
		var jdbc = new JdbcTemplate(dataSource);
		jdbc.update(
				"""
						INSERT INTO varieties (code, genus, name, sale_enabled, is_active, created_at, updated_at)
						VALUES ('VAR-008000', '속', '기존 코드', TRUE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
						       ('IMPORTED', '속', '수입 코드', TRUE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
						       ('VAR-999999999999999999999999', '속', '임의 긴 코드', TRUE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
						""");
		jdbc.update("""
				INSERT INTO materials (code, category, name, is_active, created_at, updated_at)
				VALUES ('MAT-009000', '분류', '기존 자재', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
				""");
		var upgrade = Flyway.configure().dataSource(dataSource).load();
		assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
		assertThat(jdbc.queryForList("SELECT code FROM varieties WHERE genus = '속'", String.class))
			.containsExactlyInAnyOrder("VAR-008000", "IMPORTED", "VAR-999999999999999999999999");
		assertThat(jdbc.queryForObject("SELECT nextval('variety_codes_seq')", Long.class)).isEqualTo(8001);
		assertThat(jdbc.queryForObject("SELECT nextval('material_codes_seq')", Long.class)).isEqualTo(9001);
		try (var connection = dataSource.getConnection()) {
			connection.setAutoCommit(false);
			try (var statement = connection.createStatement();
					var result = statement.executeQuery("SELECT nextval('variety_codes_seq')")) {
				assertThat(result.next()).isTrue();
				assertThat(result.getLong(1)).isEqualTo(8002);
			}
			connection.rollback();
		}
		jdbc.update("DELETE FROM varieties WHERE code = 'VAR-008000'");
		assertThat(upgrade.migrate().migrationsExecuted).isZero();
		assertThat(jdbc.queryForObject("SELECT nextval('variety_codes_seq')", Long.class)).isEqualTo(8003);
	}

	@Test
	void inboundReusesNormalizedVarietyAndKeepsWorkSnapshot() throws Exception {
		String request = """
				{"inboundDate":"2026-08-20","inboundType":"FLASK_SEEDLING","estimatedQuantity":20,
				 "newVariety":{"genus":" 속 ","name":" 품종 ","defaultPotSize":"3치","memo":"최초"},
				 "worker":"담당자","tempLocation":"임시 위치"}
				""";
		var first = post("/api/inbound-records", request);
		var second = post("/api/inbound-records", request.replace("최초", "재입고"));
		assertThat(first.status()).as(first.body().toString()).isEqualTo(201);
		assertThat(second.status()).as(second.body().toString()).isEqualTo(201);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM varieties", Long.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT memo FROM varieties", String.class)).isEqualTo("최초");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM work_operations", Long.class)).isEqualTo(2);
		assertThat(jdbc.queryForList("SELECT quantity_snapshot FROM work_operation_targets", Integer.class))
			.containsExactlyInAnyOrder(20, 20);
	}

	@Test
	void failedInboundPlacementRollsBackNewVarietyRecordAndWork() throws Exception {
		Long zoneId = jdbc.queryForObject("SELECT min(id) FROM bed_zones", Long.class);
		var result = post("/api/inbound-records", """
				{"inboundDate":"2026-08-20","inboundType":"PRODUCT_POT","actualQuantity":20,
				 "newVariety":{"genus":"속","name":"실패 품종"},"bedZoneId":%d,
				 "potSize":"3치","startPosition":2,"endPosition":1}
				""".formatted(zoneId));
		assertThat(result.status()).as(result.body().toString()).isEqualTo(400);
		for (String table : List.of("varieties", "inbound_records", "orchid_groups", "work_operations")) {
			assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class)).as(table).isZero();
		}
	}

}
