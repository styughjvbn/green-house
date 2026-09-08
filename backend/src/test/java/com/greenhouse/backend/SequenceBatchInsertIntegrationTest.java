package com.greenhouse.backend;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class SequenceBatchInsertIntegrationTest {

	@Autowired
	BusinessPartnerRepository businessPartnerRepository;

	@Autowired
	EntityManager entityManager;

	@Autowired
	EntityManagerFactory entityManagerFactory;

	@Test
	void insertsEntitiesWithPooledSequenceAndJdbcBatch() {
		List<BusinessPartner> partners = IntStream.range(0, 120)
			.mapToObj(index -> new BusinessPartner("배치 거래처 " + index, PartnerType.WHOLESALE, null, null, null, null))
			.toList();
		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();

		businessPartnerRepository.saveAll(partners);
		entityManager.flush();

		assertThat(partners).extracting(BusinessPartner::getId).doesNotContainNull().doesNotHaveDuplicates();
		assertThat(statistics.getPrepareStatementCount()).isLessThan(10L);
	}

}
