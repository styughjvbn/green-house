package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.sales.api.partner.PartnerType;
import com.greenhouse.backend.sales.partner.domain.BusinessPartner;
import com.greenhouse.backend.sales.partner.repository.BusinessPartnerRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestContextManager;
import org.springframework.test.context.TestPropertySource;

@Tag("work-e2e")
class PostgresContextReusePostgresE2ETest {
  @Test
  void sharesContextRestoresRowsAndSequencesAndSurvivesAnIsolatedContextClose() throws Exception {
    var first = new TestContextManager(FirstFixture.class);
    var second = new TestContextManager(SecondFixture.class);
    var isolated = new TestContextManager(IsolatedFixture.class);
    first.beforeTestClass();
    var shared = first.getTestContext().getApplicationContext();
    var jdbc = shared.getBean(JdbcTemplate.class);
    var database = shared.getBean(PostgresE2eConfiguration.TestDatabase.class);
    var reference = jdbc.queryForList("SELECT * FROM work_types ORDER BY id");
    var history = jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
    var partners = shared.getBean(BusinessPartnerRepository.class);
    var statistics =
        shared.getBean(EntityManagerFactory.class).unwrap(SessionFactory.class).getStatistics();
    assertThat(statistics.isStatisticsEnabled()).isTrue();
    long firstId = partners.saveAndFlush(partner("first")).getId();
    jdbc.update("UPDATE work_types SET is_active=false");
    jdbc.update("UPDATE houses SET name='previous test'");
    var sequence = jdbc.queryForMap("SELECT last_value,is_called FROM business_partners_id_seq");
    statistics.setStatisticsEnabled(false);
    first.afterTestClass();
    second.beforeTestClass();
    try {
      var reused = second.getTestContext().getApplicationContext();
      assertThat(reused).isSameAs(shared);
      assertThat(reused.getBean(PostgresE2eConfiguration.TestDatabase.class)).isSameAs(database);
      assertThat(statistics.isStatisticsEnabled()).isTrue();
      assertThat(statistics.getPrepareStatementCount()).isZero();
      assertThat(jdbc.queryForObject("SELECT count(*) FROM business_partners", Long.class))
          .isZero();
      assertThat(jdbc.queryForList("SELECT * FROM work_types ORDER BY id")).isEqualTo(reference);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM houses WHERE name='previous test'", Long.class))
          .isZero();
      assertThat(jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank"))
          .isEqualTo(history);
      assertThat(jdbc.queryForMap("SELECT last_value,is_called FROM business_partners_id_seq"))
          .isEqualTo(sequence);
      long secondId = partners.saveAndFlush(partner("second")).getId();
      assertThat(secondId).isGreaterThan(firstId);
      long rawId = jdbc.queryForObject("SELECT nextval('business_partners_id_seq')", Long.class);
      long thirdId = partners.saveAndFlush(partner("third")).getId();
      assertThat(thirdId).isNotIn(firstId, secondId, rawId);
      isolated.beforeTestClass();
      var isolatedDatabase =
          isolated
              .getTestContext()
              .getApplicationContext()
              .getBean(PostgresE2eConfiguration.TestDatabase.class);
      try {
        var other = isolated.getTestContext().getApplicationContext();
        assertThat(other).isNotSameAs(shared);
        assertThat(other.getBean(PostgresE2eConfiguration.TestDatabase.class).getJdbcUrl())
            .isNotEqualTo(database.getJdbcUrl());
        assertThat(
                other
                    .getBean(JdbcTemplate.class)
                    .queryForObject("SELECT count(*) FROM business_partners", Long.class))
            .isZero();
      } finally {
        isolated.afterTestClass();
      }
      assertThat(PostgresE2eConfiguration.POSTGRES.isRunning()).isTrue();
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM pg_database WHERE datname=?",
                  Long.class,
                  isolatedDatabase.name()))
          .isZero();
      assertThat(jdbc.queryForObject("SELECT count(*) FROM business_partners", Long.class))
          .isEqualTo(2);
    } finally {
      second.afterTestClass();
    }
  }

  private static BusinessPartner partner(String name) {
    return new BusinessPartner(name, PartnerType.RETAIL, null, null, null, null);
  }

  @TestPropertySource(properties = "test.postgres.reuse-proof=true")
  static class FirstFixture extends WorkE2ETestBase {}

  @TestPropertySource(properties = "test.postgres.reuse-proof=true")
  static class SecondFixture extends WorkE2ETestBase {}

  @IsolatedPostgresContext
  static class IsolatedFixture extends WorkE2ETestBase {}
}
