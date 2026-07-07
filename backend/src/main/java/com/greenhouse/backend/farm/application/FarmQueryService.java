package com.greenhouse.backend.farm.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.dto.BedZoneResponse;
import com.greenhouse.backend.farm.dto.HouseResponse;
import com.greenhouse.backend.farm.dto.OrchidGroupResponse;
import com.greenhouse.backend.farm.dto.PhysicalBedResponse;
import com.greenhouse.backend.farm.repository.BedZoneRepository;
import com.greenhouse.backend.farm.repository.HouseRepository;
import com.greenhouse.backend.farm.repository.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.PhysicalBedRepository;

import lombok.RequiredArgsConstructor;

import java.util.List;
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
		return houseRepository.findAll().stream()
				.sorted((a, b) -> a.getNumber().compareTo(b.getNumber()))
				.map(HouseResponse::from)
				.toList();
	}

	public HouseResponse getHouse(Long houseId) {
		return houseRepository.findWithPhysicalBedsById(houseId)
				.map(HouseResponse::from)
				.orElseThrow(() -> new NotFoundException("동을 찾을 수 없습니다."));
	}

	public List<PhysicalBedResponse> getPhysicalBeds(Long houseId) {
		return physicalBedRepository.findByHouseIdOrderByDisplayOrderAsc(houseId).stream()
				.map(PhysicalBedResponse::from)
				.toList();
	}

	public PhysicalBedResponse getPhysicalBed(Long physicalBedId) {
		return physicalBedRepository.findWithHouseAndBedZonesById(physicalBedId)
				.map(PhysicalBedResponse::from)
				.orElseThrow(() -> new NotFoundException("물리 배드를 찾을 수 없습니다."));
	}

	public List<BedZoneResponse> getBedZones(Long houseId, Long physicalBedId) {
		if (physicalBedId != null) {
			return bedZoneRepository.findByPhysicalBedIdOrderBySortOrderAsc(physicalBedId).stream()
					.map(BedZoneResponse::from)
					.toList();
		}
		if (houseId != null) {
			return bedZoneRepository.findByHouseId(houseId).stream()
					.map(BedZoneResponse::from)
					.toList();
		}
		return bedZoneRepository.findAll().stream()
				.map(BedZoneResponse::from)
				.toList();
	}

	public BedZoneResponse getBedZone(Long bedZoneId) {
		return bedZoneRepository.findWithDetailsById(bedZoneId)
				.map(BedZoneResponse::from)
				.orElseThrow(() -> new NotFoundException("논리 구역을 찾을 수 없습니다."));
	}

	public List<OrchidGroupResponse> getOrchidGroups(Long houseId, Long physicalBedId, Long bedZoneId, String status) {
		return orchidGroupRepository.search(houseId, physicalBedId, bedZoneId, status).stream()
				.map(OrchidGroupResponse::from)
				.toList();
	}
}
