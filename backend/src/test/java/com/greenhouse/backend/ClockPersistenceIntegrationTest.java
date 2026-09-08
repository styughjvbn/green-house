package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.greenhouse.backend.auction.application.AuctionTrackingService;
import com.greenhouse.backend.auction.application.RecordAuctionResultCommand;
import com.greenhouse.backend.auction.domain.AuctionAttemptStatus;
import com.greenhouse.backend.auction.domain.AuctionShipment;
import com.greenhouse.backend.auction.domain.AuctionShipmentLot;
import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.farm.application.collection.OrchidGroupCollectionService;
import com.greenhouse.backend.farm.application.structure.FarmQueryService;
import com.greenhouse.backend.farm.domain.collection.OrchidGroupCollectionMember;
import com.greenhouse.backend.farm.dto.collection.OrchidGroupCollectionCreateRequest;
import com.greenhouse.backend.farm.dto.collection.OrchidGroupCollectionMemberAddRequest;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupResponse;
import com.greenhouse.backend.farm.support.FarmTestFixtures;
import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.partner.domain.PartnerType;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ClockPersistenceIntegrationTest extends AbstractBackendIntegrationTest {

	@MockitoBean(name = "farmClock")
	Clock clock;

	@Autowired
	EntityManager entityManager;

	@Autowired
	FarmQueryService queries;

	@Autowired
	AuctionTrackingService auctions;

	@Autowired
	OrchidGroupCollectionService collections;

	@BeforeEach
	void fixedClock() {
		when(clock.instant()).thenReturn(Instant.parse("2040-12-31T15:00:00Z"));
	}

	@Test
	void persistsUtcCreationAndUpdatesAndUsesFarmDateForAge() {
		var fixture = new FarmTestFixtures(entityManager);
		var layout = fixture.layout(908);
		var group = fixture.orchidGroup(layout.left(), "CLOCK", 10);
		entityManager.flush();
		assertThat(group.getCreatedAt()).isEqualTo(LocalDateTime.of(2040, 12, 31, 15, 0));
		assertThat(group.getUpdatedAt()).isEqualTo(group.getCreatedAt());
		when(clock.instant()).thenReturn(Instant.parse("2041-12-31T14:59:59Z"));
		assertThat(queries.getOrchidGroups(layout.house().getId(), null, null, null, null).getFirst().ageYear())
			.isEqualTo(1);
		when(clock.instant()).thenReturn(Instant.parse("2041-12-31T15:00:00Z"));
		assertThat(queries.getOrchidGroups(layout.house().getId(), null, null, null, null).getFirst().ageYear())
			.isEqualTo(2);
		layout.bed().updatePositionUnits(java.math.BigDecimal.valueOf(65), "칸");
		entityManager.flush();
		assertThat(layout.bed().getUpdatedAt()).isEqualTo(TimeConfig.utcNow(clock));
		assertThat(layout.bed().getCreatedAt()).isEqualTo(LocalDateTime.of(2040, 12, 31, 15, 0));
		assertThat(OrchidGroupResponse.calculateAgeYear(2, LocalDate.of(2044, 1, 1), TimeConfig.farmToday(clock)))
			.isEqualTo(2);
		assertThat(OrchidGroupResponse.calculateAgeYear(null, null, TimeConfig.farmToday(clock))).isNull();
	}

	@Test
	void recordsAuctionAndMembershipEventsUsingTheSameInjectedTime() {
		var partner = new BusinessPartner("Clock auction", PartnerType.AUCTION_HOUSE, null, null, null, null);
		entityManager.persist(partner);
		var shipment = new AuctionShipment(LocalDate.of(2040, 12, 31), partner.getId(), PartnerType.AUCTION_HOUSE);
		var lot = new AuctionShipmentLot("난", "품종", "A", null, 10);
		shipment.addLot(lot);
		entityManager.persist(shipment);
		auctions.addResult(lot.getId(), new RecordAuctionResultCommand(LocalDate.of(2041, 1, 1), null,
				AuctionAttemptStatus.FAILED, null, null, null));
		assertThat(lot.getStatusHistory()).singleElement()
			.satisfies(history -> assertThat(history.getChangedAt()).isEqualTo(TimeConfig.utcNow(clock)));
		var fixture = new FarmTestFixtures(entityManager);
		var layout = fixture.layout(909);
		var group = fixture.orchidGroup(layout.left(), "CLOCK-MEMBER", 10);
		var collection = collections.create(new OrchidGroupCollectionCreateRequest("시각 확인", null, null, "작성자"));
		collections.addMembers(collection.id(),
				new OrchidGroupCollectionMemberAddRequest(Set.of(group.getId()), "작성자"));
		var member = entityManager
			.createQuery("from OrchidGroupCollectionMember where collectionId = :id", OrchidGroupCollectionMember.class)
			.setParameter("id", collection.id())
			.getSingleResult();
		assertThat(member.getJoinedAt()).isEqualTo(TimeConfig.utcNow(clock));
		when(clock.instant()).thenReturn(Instant.parse("2041-01-01T01:00:00Z"));
		collections.removeMember(collection.id(), group.getId());
		assertThat(member.getRemovedAt()).isEqualTo(TimeConfig.utcNow(clock));
		member.remove(LocalDateTime.of(2042, 1, 1, 0, 0));
		assertThat(member.getRemovedAt()).isEqualTo(TimeConfig.utcNow(clock));
	}

}
