package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.farm.application.status.FarmStatusService;
import com.greenhouse.backend.farm.application.structure.FarmQueryService;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.structure.BedZone;
import com.greenhouse.backend.farm.domain.structure.BedZoneSide;
import com.greenhouse.backend.farm.domain.structure.House;
import com.greenhouse.backend.farm.domain.structure.PhysicalBed;
import com.greenhouse.backend.farm.domain.variety.Variety;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupResponse;
import com.greenhouse.backend.farm.dto.status.FarmStatusMapOrchidGroupResponse;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.structure.HouseRepository;
import com.greenhouse.backend.farm.repository.variety.VarietyRepository;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("work-e2e")
class FarmQueryPostgresE2ETest extends WorkE2ETestBase {
	@Autowired FarmQueryService query;
	@Autowired FarmStatusService status;
	@Autowired HouseRepository houses;
	@Autowired OrchidGroupRepository groups;
	@Autowired VarietyRepository varieties;
	@Autowired TransactionTemplate transactions;
	@Autowired EntityManagerFactory entityManagerFactory;
	@Autowired JdbcTemplate jdbc;

	@ParameterizedTest
	@ValueSource(ints = {1, 10, 50})
	void structureQueriesStayBoundedAndMapDoesNotLoadGroupEntities(int bedCount) {
		// Keep database sequences aligned with Hibernate's pooled IDs across parameter cases.
		jdbc.execute("TRUNCATE TABLE orchid_groups, varieties CONTINUE IDENTITY CASCADE");
		Long houseId = transactions.execute(tx -> {
			var house = new House(10000 + bedCount, "조회 회귀");
			for (int index = 0; index < bedCount; index++) {
				var bed = new PhysicalBed(index + 1, index + 1);
				bed.addBedZone(new BedZone("좌", BedZoneSide.LEFT, 1));
				bed.addBedZone(new BedZone("우", BedZoneSide.RIGHT, 2));
				house.addPhysicalBed(bed);
			}
			houses.save(house);
			for (var bed : house.getPhysicalBeds()) {
				var variety = varieties.save(new Variety("QUERY-" + bed.getId(), "속", "현재 " + bed.getNumber(),
						null, "3치", true, true, null, null));
				var group = new OrchidGroup(bed.getBedZones().getFirst(), "과거 속", "과거 품종", 10, "3치", 2,
						bed.getNumber() % 2 == 0 ? "문제" : "정상", 1, BigDecimal.ZERO, BigDecimal.ONE);
				group.assignVariety(variety);
				groups.save(group);
				groups.save(new OrchidGroup(bed.getBedZones().getLast(), "기타 속", "직접 입력", 5, "3치", 1,
						"정상", 1, BigDecimal.ZERO, BigDecimal.ONE));
				groups.save(new OrchidGroup(bed.getBedZones().getLast(), "기타 속", "소진", 1, "3치", 1,
						"정상", 2, BigDecimal.ONE, BigDecimal.TWO));
			}
			return house.getId();
		});
		jdbc.update("UPDATE orchid_groups SET created_at = TIMESTAMP '2020-01-01 00:00:00'");
		jdbc.update("UPDATE orchid_groups SET quantity = 0 WHERE variety_name = '소진'");
		jdbc.update("UPDATE varieties SET name = name || ' 변경', color = '#AABBCC'");
		var expectedMap = transactions.execute(tx -> groups.search(null, "", null, null, null).stream().map(group -> {
			var detail = OrchidGroupResponse.from(group);
			return new FarmStatusMapOrchidGroupResponse(detail.id(), detail.houseId(), group.getBedZone().getPhysicalBed().getId(),
					detail.bedZoneId(), detail.startPosition(), detail.endPosition(), detail.varietyId(), detail.varietyColor(),
					detail.varietyName(), detail.quantity(), detail.status(), detail.ageYear(), detail.potSize(), detail.sortOrder());
		}).toList());
		var stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		stats.clear();
		var map = status.getMap();
		assertThat(stats.getPrepareStatementCount()).isEqualTo(3);
		assertThat(stats.getEntityStatistics(OrchidGroup.class.getName()).getLoadCount()).isZero();
		assertThat(stats.getEntityStatistics(Variety.class.getName()).getLoadCount()).isZero();
		assertThat(map.orchidGroups()).containsExactlyInAnyOrderElementsOf(expectedMap);
		stats.clear();
		var structure = query.getHouses();
		assertThat(stats.getPrepareStatementCount()).isEqualTo(3);
		var house = structure.stream().filter(row -> row.id().equals(houseId)).findFirst().orElseThrow();
		assertThat(house.physicalBeds()).hasSize(bedCount);
		assertThat(house.physicalBeds()).extracting(bed -> bed.number()).isSorted();
		stats.clear();
		var zones = query.getBedZones(houseId, null);
		assertThat(stats.getPrepareStatementCount()).isEqualTo(2);
		assertThat(zones).hasSize(bedCount * 2).allSatisfy(zone -> assertThat(zone.orchidGroups()).hasSize(1));
		stats.clear();
		assertThat(query.getPhysicalBeds(houseId)).isEqualTo(house.physicalBeds());
		assertThat(stats.getPrepareStatementCount()).isEqualTo(2);
		stats.clear();
		assertThat(query.getHouse(houseId)).isEqualTo(house);
		assertThat(stats.getPrepareStatementCount()).isEqualTo(3);
		stats.clear();
		var firstBed = house.physicalBeds().getFirst();
		assertThat(query.getPhysicalBed(firstBed.id())).isEqualTo(firstBed);
		assertThat(stats.getPrepareStatementCount()).isEqualTo(2);
		stats.clear();
		assertThat(query.getBedZone(firstBed.bedZones().getLast().id())).isEqualTo(firstBed.bedZones().getLast());
		assertThat(stats.getPrepareStatementCount()).isEqualTo(2);
	}
}
