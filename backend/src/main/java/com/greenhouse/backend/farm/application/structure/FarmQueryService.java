package com.greenhouse.backend.farm.application.structure;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.structure.BedZone;
import com.greenhouse.backend.farm.domain.structure.PhysicalBed;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupResponse;
import com.greenhouse.backend.farm.dto.structure.BedZoneResponse;
import com.greenhouse.backend.farm.dto.structure.HouseResponse;
import com.greenhouse.backend.farm.dto.structure.PhysicalBedResponse;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.structure.BedZoneRepository;
import com.greenhouse.backend.farm.repository.structure.HouseRepository;
import com.greenhouse.backend.farm.repository.structure.PhysicalBedRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class FarmQueryService {

	private final HouseRepository houseRepository;
	private final PhysicalBedRepository physicalBedRepository;
	private final BedZoneRepository bedZoneRepository;
	private final OrchidGroupRepository orchidGroupRepository;

	public List<HouseResponse> getHouses() {
		var houses = houseRepository.findAll();
		var beds = physicalBedRepository.findAllInFarmOrder();
		var groups = groupsByZone(beds.stream().map(PhysicalBed::getId).toList());
		var bedsByHouse = beds.stream().collect(Collectors.groupingBy(bed -> bed.getHouse().getId()));
		return houses.stream().sorted(java.util.Comparator.comparing(house -> house.getNumber()))
				.map(house -> HouseResponse.from(house, bedsByHouse.getOrDefault(house.getId(), List.of()).stream()
						.map(bed -> PhysicalBedResponse.from(bed, groups)).toList())).toList();
	}

	public HouseResponse getHouse(Long houseId) {
		var house = houseRepository.findById(houseId).orElseThrow(() -> new NotFoundException("동을 찾을 수 없습니다."));
		return HouseResponse.from(house, getPhysicalBeds(houseId));
	}

	public List<PhysicalBedResponse> getPhysicalBeds(Long houseId) {
		var beds = physicalBedRepository.findByHouseIdOrderByDisplayOrderAsc(houseId);
		var groups = groupsByZone(beds.stream().map(PhysicalBed::getId).toList());
		return beds.stream().map(bed -> PhysicalBedResponse.from(bed, groups)).toList();
	}

	public PhysicalBedResponse getPhysicalBed(Long physicalBedId) {
		var bed = physicalBedRepository.findWithHouseAndBedZonesById(physicalBedId)
				.orElseThrow(() -> new NotFoundException("다이를 찾을 수 없습니다."));
		return PhysicalBedResponse.from(bed, groupsByZone(List.of(physicalBedId)));
	}

	public List<BedZoneResponse> getBedZones(Long houseId, Long physicalBedId) {
		List<BedZone> zones;
		if (physicalBedId != null) {
			zones = bedZoneRepository.findByPhysicalBedIdOrderBySortOrderAsc(physicalBedId);
		} else if (houseId != null) {
			zones = bedZoneRepository.findByHouseId(houseId);
		} else {
			zones = bedZoneRepository.findAllWithLocation();
		}
		var groups = groupsByZone(zones.stream().map(zone -> zone.getPhysicalBed().getId()).distinct().toList());
		return zones.stream().map(zone -> BedZoneResponse.from(zone, groups.getOrDefault(zone.getId(), List.of()))).toList();
	}

	public BedZoneResponse getBedZone(Long bedZoneId) {
		var zone = bedZoneRepository.findWithLocationById(bedZoneId)
				.orElseThrow(() -> new NotFoundException("논리 구역을 찾을 수 없습니다."));
		return BedZoneResponse.from(zone, groupsByZone(List.of(zone.getPhysicalBed().getId()))
				.getOrDefault(zone.getId(), List.of()));
	}

	private Map<Long, List<OrchidGroup>> groupsByZone(List<Long> bedIds) {
		if (bedIds.isEmpty()) {
			return Map.of();
		}
		return orchidGroupRepository.findByPhysicalBedIdInOrderByLocation(bedIds).stream()
				.collect(Collectors.groupingBy(group -> group.getBedZone().getId(), LinkedHashMap::new, Collectors.toList()));
	}

	public List<OrchidGroupResponse> getOrchidGroups(
			Long houseId,
			String keyword,
			Long physicalBedId,
			Long bedZoneId,
			String status) {
		return orchidGroupRepository
				.search(houseId, keyword == null ? "" : keyword.trim(), physicalBedId, bedZoneId, status).stream()
				.map(OrchidGroupResponse::from)
				.toList();
	}
}
