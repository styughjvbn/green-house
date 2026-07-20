package com.greenhouse.backend.farm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.greenhouse.backend.farm.domain.House;
import com.greenhouse.backend.farm.domain.PhysicalBed;
import com.greenhouse.backend.farm.repository.BedZoneRepository;
import com.greenhouse.backend.farm.repository.HouseRepository;
import com.greenhouse.backend.farm.repository.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.PhysicalBedRepository;

import java.util.List;
import org.junit.jupiter.api.Test;

class FarmStatusServiceTest {

	private final HouseRepository houseRepository = mock(HouseRepository.class);
	private final PhysicalBedRepository physicalBedRepository = mock(PhysicalBedRepository.class);
	private final BedZoneRepository bedZoneRepository = mock(BedZoneRepository.class);
	private final OrchidGroupRepository orchidGroupRepository = mock(OrchidGroupRepository.class);
	private final FarmStatusService service = new FarmStatusService(
			houseRepository,
			physicalBedRepository,
			bedZoneRepository,
			orchidGroupRepository);

	@Test
	void returnsContinuousBedsAndTrimsLastViewport() {
		var beds = List.of(
				bed(11L, 1L, 1, 1),
				bed(12L, 1L, 1, 2),
				bed(13L, 1L, 1, 3),
				bed(21L, 2L, 2, 1),
				bed(22L, 2L, 2, 2));
		when(physicalBedRepository.findAllInFarmOrder()).thenReturn(beds);

		var result = service.getOrchidManagementViewport(22L, 3);

		assertThat(result.startBedId()).isEqualTo(13L);
		assertThat(result.beds()).extracting("id").containsExactly(13L, 21L, 22L);
		assertThat(result.hasPrevious()).isTrue();
		assertThat(result.hasNext()).isFalse();
		assertThat(result.bedOrder()).extracting("id").containsExactly(11L, 12L, 13L, 21L, 22L);
	}

	@Test
	void fallsBackToFirstBedForUnknownStartId() {
		var beds = List.of(
				bed(11L, 1L, 1, 1),
				bed(12L, 1L, 1, 2),
				bed(13L, 1L, 1, 3));
		when(physicalBedRepository.findAllInFarmOrder()).thenReturn(beds);

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
		when(physicalBedRepository.findByHouseIdOrderByDisplayOrderAsc(1L))
				.thenReturn(List.of(physicalBed));
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
}
