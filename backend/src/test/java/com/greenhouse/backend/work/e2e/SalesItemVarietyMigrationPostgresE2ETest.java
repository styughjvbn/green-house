package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.*;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Tag("work-e2e")
@Testcontainers
class SalesItemVarietyMigrationPostgresE2ETest {
  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

  @Test
  void migrationUsesCapturedIdentityThenUniqueNamesAndNeverRewritesSnapshots() {
    var source =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    var jdbc = new JdbcTemplate(source);
    jdbc.execute("CREATE TABLE varieties(id bigint PRIMARY KEY, name text, genus text)");
    jdbc.execute(
        "CREATE TABLE sales_slip_items(id bigint PRIMARY KEY, item_name text, genus text, amount integer)");
    jdbc.execute("CREATE TABLE orchid_groups(id bigint PRIMARY KEY, variety_id bigint)");
    jdbc.execute(
        "CREATE TABLE sales_slip_item_allocations(id bigint PRIMARY KEY, sales_slip_item_id bigint, orchid_group_id bigint)");
    jdbc.execute(
        "CREATE TABLE sales_orchid_group_snapshots(sales_slip_item_allocation_id bigint, snapshot_type text, variety_id bigint, capture_source text)");
    jdbc.execute(
        "INSERT INTO varieties VALUES (1,'이름 A','속'),(2,'하니 문','속'),(3,'동명','속1'),(4,'동명','속2'),(5,'현재 이름 B','현재 속')");
    jdbc.execute(
        "INSERT INTO sales_slip_items VALUES (1,'이름 A','속',100),(2,'하니문','속',200),(3,'동명',NULL,300),(4,'과거 이름 A','과거 속',400),(5,'알 수 없음',NULL,500),(6,'이름 A','다른 속',600),(7,'하니문','속',700),(8,'하니문','속',800),(9,'하니문','속',900)");
    jdbc.execute("INSERT INTO orchid_groups VALUES (10,1),(11,2)");
    jdbc.execute(
        "INSERT INTO sales_slip_item_allocations VALUES (10,4,10),(11,7,10),(12,7,11),(13,8,11),(14,9,11)");
    jdbc.execute(
        "INSERT INTO sales_orchid_group_snapshots VALUES (10,'CREATION',5,'LIVE'),(14,'CREATION',2,'MIGRATED_CURRENT_STATE')");
    var before =
        jdbc.queryForList("SELECT id,item_name,genus,amount FROM sales_slip_items ORDER BY id");
    Flyway.configure()
        .dataSource(source)
        .baselineOnMigrate(true)
        .baselineVersion("44")
        .target("45")
        .load()
        .migrate();
    assertThat(
            jdbc.queryForList("SELECT id,item_name,genus,amount FROM sales_slip_items ORDER BY id"))
        .isEqualTo(before);
    assertThat(
            jdbc.queryForObject("SELECT variety_id FROM sales_slip_items WHERE id=1", Long.class))
        .isEqualTo(1L);
    assertThat(
            jdbc.queryForObject("SELECT variety_id FROM sales_slip_items WHERE id=2", Long.class))
        .isEqualTo(2L);
    assertThat(
            jdbc.queryForObject("SELECT variety_id FROM sales_slip_items WHERE id=4", Long.class))
        .isEqualTo(5L);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM sales_slip_items WHERE variety_id IS NULL", Integer.class))
        .isEqualTo(6);
    assertThatThrownBy(() -> jdbc.update("UPDATE sales_slip_items SET variety_id=999 WHERE id=1"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> jdbc.update("DELETE FROM varieties WHERE id=1"))
        .isInstanceOf(DataIntegrityViolationException.class);
    jdbc.update("UPDATE varieties SET name='변경된 이름',genus='변경된 속' WHERE id=1");
    assertThat(
            jdbc.queryForObject("SELECT item_name FROM sales_slip_items WHERE id=1", String.class))
        .isEqualTo("이름 A");
  }
}
