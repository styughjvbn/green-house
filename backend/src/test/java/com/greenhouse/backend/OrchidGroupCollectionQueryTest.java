package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.farm.application.collection.OrchidGroupCollectionService;
import com.greenhouse.backend.farm.domain.collection.OrchidGroupCollection;
import com.greenhouse.backend.farm.domain.collection.OrchidGroupCollectionMember;
import com.greenhouse.backend.farm.dto.collection.OrchidGroupCollectionMemberResponse;
import com.greenhouse.backend.farm.support.FarmTestFixtures;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class OrchidGroupCollectionQueryTest {

	@Autowired
	OrchidGroupCollectionService service;

	@Autowired
	EntityManager entityManager;

	@Autowired
	EntityManagerFactory entityManagerFactory;

	@ParameterizedTest
	@ValueSource(ints = { 1, 10, 50 })
	void loadsCollectionsAndMembersWithABoundedQueryCount(int collectionCount) {
		var fixtures = new FarmTestFixtures(entityManager);
		var layout = fixtures.layout(983);
		var first = fixtures.orchidGroup(layout.left(), "COLLECTION-FIRST", 20);
		var second = fixtures.orchidGroup(layout.right(), "COLLECTION-SECOND", 30);
		for (int index = 0; index < collectionCount; index++) {
			var collection = new OrchidGroupCollection("목록 " + index, null, null, "worker");
			entityManager.persist(collection);
			entityManager.persist(new OrchidGroupCollectionMember(collection.getId(), second.getId(), "worker",
					java.time.LocalDateTime.of(2026, 9, 8, 1, 2)));
			entityManager.persist(new OrchidGroupCollectionMember(collection.getId(), first.getId(), "worker",
					java.time.LocalDateTime.of(2026, 9, 8, 1, 2)));
		}
		var archived = new OrchidGroupCollection("보관", null, null, "worker");
		archived.archive();
		entityManager.persist(archived);
		var removed = new OrchidGroupCollectionMember(archived.getId(), first.getId(), "worker",
				java.time.LocalDateTime.of(2026, 9, 8, 1, 2));
		removed.remove(java.time.LocalDateTime.of(2026, 9, 8, 2, 3));
		entityManager.persist(removed);
		entityManager.flush();
		entityManager.clear();
		var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();

		var responses = service.getCollections(false);

		assertThat(responses).hasSize(collectionCount).allSatisfy(collection -> {
			assertThat(collection.orchidGroupCount()).isEqualTo(2);
			assertThat(collection.totalQuantity()).isEqualTo(50);
			assertThat(collection.members()).extracting(OrchidGroupCollectionMemberResponse::orchidGroupId)
				.containsExactly(second.getId(), first.getId());
		});
		assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(3);

		entityManager.clear();
		statistics.clear();
		assertThat(service.getCollectionsForOrchidGroup(first.getId())).hasSize(collectionCount);
		assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(5);
		assertThat(service.getCollections(true)).hasSize(collectionCount + 1)
			.filteredOn(response -> response.id().equals(archived.getId()))
			.singleElement()
			.satisfies(response -> assertThat(response.members()).isEmpty());
	}

}
