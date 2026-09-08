package com.greenhouse.backend.farm.application.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.greenhouse.backend.farm.domain.structure.House;
import com.greenhouse.backend.farm.domain.structure.PhysicalBed;
import com.greenhouse.backend.farm.repository.structure.BedZoneRepository;
import com.greenhouse.backend.farm.repository.structure.HouseRepository;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.structure.PhysicalBedRepository;
import com.greenhouse.backend.farm.repository.structure.PhysicalBedOrderRow;

import java.util.List;
import org.junit.jupiter.api.Test;

class FarmStatusServiceTest {

	private final HouseRepository houseRepository = mock(HouseRepository.class);
	private final PhysicalBedRepository physicalBedRepository = mock(PhysicalBedRepository.class);
	private final BedZoneRepository bedZoneRepository = mock(BedZoneRepository.class);
	private final OrchidGroupRepository orchidGroupRepository = mock(OrchidGroupRepository.class);
	private final FarmStatusService service = new FarmStatusService(
			java.time.Clock.fixed(java.time.Instant.parse("2026-09-08T00:00:00Z"), java.time.ZoneOffset.UTC),
			houseRepository,
			physicalBedRepository,
			bedZoneRepository,
			orchidGroupRepository);

	@Test
	void preservesRequestedBedAtTheStartOfTheLastViewport() {
		var beds = List.of(
				bed(11L, 1L, 1, 1),
				bed(12L, 1L, 1, 2),
				bed(13L, 1L, 1, 3),
				bed(21L, 2L, 2, 1),
				bed(22L, 2L, 2, 2));
		var rows = orderRows(beds);
		when(physicalBedRepository.findAllOrderRows()).thenReturn(rows);
		when(physicalBedRepository.findAllWithZonesByIdIn(List.of(22L))).thenReturn(List.of(beds.get(4)));
		when(orchidGroupRepository.findByPhysicalBedIdInOrderByLocation(List.of(22L))).thenReturn(List.of());

		var result = service.getOrchidManagementViewport(22L, 3);

		assertThat(result.startBedId()).isEqualTo(22L);
		assertThat(result.beds()).extracting("id").containsExactly(22L);
		assertThat(result.hasPrevious()).isTrue();
		assertThat(result.hasNext()).isFalse();
	}

	@Test
	void returnsBedOrderSeparatelyFromViewport() {
		var rows = List.of(
				new PhysicalBedOrderRow(11L, 1L, 1, 1),
				new PhysicalBedOrderRow(21L, 2L, 2, 1));
		when(physicalBedRepository.findAllOrderRows()).thenReturn(rows);

		var result = service.getOrchidManagementBedOrder();

		assertThat(result).extracting("id").containsExactly(11L, 21L);
	}

	@Test
	void fallsBackToFirstBedForUnknownStartId() {
		var beds = List.of(
				bed(11L, 1L, 1, 1),
				bed(12L, 1L, 1, 2),
				bed(13L, 1L, 1, 3));
		var rows = orderRows(beds);
		when(physicalBedRepository.findAllOrderRows()).thenReturn(rows);
		when(physicalBedRepository.findAllWithZonesByIdIn(List.of(11L, 12L))).thenReturn(beds.subList(0, 2));
		when(orchidGroupRepository.findByPhysicalBedIdInOrderByLocation(List.of(11L, 12L))).thenReturn(List.of());

		var result = service.getOrchidManagementViewport(999L, 2);

		assertThat(result.startBedId()).isEqualTo(11L);
		assertThat(result.beds()).extracting("id").containsExactly(11L, 12L);
	}

	@Test
	void rejectsUnsupportedBedCount() {
		assertThatThrownBy(() -> service.getOrchidManagementViewport(null, 5))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("bedCount");
	}

	@Test
	void returnsLightweightFarmMapStructureWithoutLoadingDetailedGroupLists() {
		var house = mock(House.class);
		when(house.getId()).thenReturn(1L);
		when(house.getNumber()).thenReturn(1);
		when(house.getName()).thenReturn("1동");
		var physicalBed = bed(11L, 1L, 1, 1);
		when(houseRepository.findAll()).thenReturn(List.of(house));
		when(physicalBedRepository.findAllInFarmOrder()).thenReturn(List.of(physicalBed));
		when(orchidGroupRepository.search(null, "", null, null, null))
				.thenReturn(List.of());

		var result = service.getMap();

		assertThat(result.houses()).hasSize(1);
		assertThat(result.houses().getFirst().physicalBeds())
				.extracting("id")
				.containsExactly(11L);
		assertThat(result.orchidGroups()).isEmpty();
	}

	private PhysicalBed bed(long id, long houseId, int houseNumber, int number) {
		var house = mock(House.class);
		when(house.getId()).thenReturn(houseId);
		when(house.getNumber()).thenReturn(houseNumber);

		var bed = mock(PhysicalBed.class);
		when(bed.getId()).thenReturn(id);
		when(bed.getHouse()).thenReturn(house);
		when(bed.getNumber()).thenReturn(number);
		when(bed.getDisplayOrder()).thenReturn(number);
		when(bed.getBedZones()).thenReturn(List.of());
		return bed;
	}

	private List<PhysicalBedOrderRow> orderRows(List<PhysicalBed> beds) {
		return beds.stream()
				.map(bed -> new PhysicalBedOrderRow(
						bed.getId(),
						bed.getHouse().getId(),
						bed.getHouse().getNumber(),
						bed.getNumber()))
				.toList();
	}
}
