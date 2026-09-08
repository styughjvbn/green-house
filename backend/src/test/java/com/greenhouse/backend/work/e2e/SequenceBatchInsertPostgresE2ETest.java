package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.partner.domain.PartnerType;
import com.greenhouse.backend.partner.repository.BusinessPartnerRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.stream.IntStream;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Tag("work-e2e")
class SequenceBatchInsertPostgresE2ETest extends WorkE2ETestBase {

	@Autowired
	BusinessPartnerRepository businessPartnerRepository;

	@Autowired
	EntityManager entityManager;

	@Autowired
	EntityManagerFactory entityManagerFactory;

	@Test
	@Transactional
	void insertsEntitiesWithPooledSequenceAndJdbcBatchOnPostgres() {
		List<BusinessPartner> partners = IntStream.range(0, 120)
			.mapToObj(index -> new BusinessPartner("PostgreSQL 배치 거래처 " + index, PartnerType.WHOLESALE, null, null,
					null, null))
			.toList();
		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();

		businessPartnerRepository.saveAll(partners);
		entityManager.flush();

		assertThat(partners).extracting(BusinessPartner::getId).doesNotContainNull().doesNotHaveDuplicates();
		assertThat(statistics.getPrepareStatementCount()).isLessThan(10L);
	}

}
