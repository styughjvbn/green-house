package com.greenhouse.backend.farm.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.domain.FarmStatusTargetType;
import com.greenhouse.backend.farm.domain.FarmZoomLevel;
import com.greenhouse.backend.farm.dto.BedZoneResponse;
import com.greenhouse.backend.farm.dto.DashboardSummaryResponse;
import com.greenhouse.backend.farm.dto.FarmStatusMapResponse;
import com.greenhouse.backend.farm.dto.FarmStatusOrchidGroupItemResponse;
import com.greenhouse.backend.farm.dto.FarmStatusOrchidGroupListResponse;
import com.greenhouse.backend.farm.dto.FarmStatusZoomResponse;
import com.greenhouse.backend.farm.dto.HouseStatusSummaryResponse;
import com.greenhouse.backend.farm.dto.PhysicalBedResponse;
import com.greenhouse.backend.farm.repository.BedZoneRepository;
import com.greenhouse.backend.farm.repository.HouseRepository;
import com.greenhouse.backend.farm.repository.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.PhysicalBedRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FarmStatusService {

	private final HouseRepository houseRepository;
	private final PhysicalBedRepository physicalBedRepository;
	private final BedZoneRepository bedZoneRepository;
	private final OrchidGroupRepository orchidGroupRepository;

	public FarmStatusService(
		HouseRepository houseRepository,
		PhysicalBedRepository physicalBedRepository,
		BedZoneRepository bedZoneRepository,
		OrchidGroupRepository orchidGroupRepository
	) {
		this.houseRepository = houseRepository;
		this.physicalBedRepository = physicalBedRepository;
		this.bedZoneRepository = bedZoneRepository;
		this.orchidGroupRepository = orchidGroupRepository;
	}

	public DashboardSummaryResponse getDashboardSummary() {
		return new DashboardSummaryResponse(
			houseRepository.count(),
			physicalBedRepository.count(),
			bedZoneRepository.count(),
			orchidGroupRepository.count(),
			orchidGroupRepository.countWarningStatus(),
			0,
			null
		);
	}

	public FarmStatusMapResponse getMap() {
		var houses = houseRepository.findAll().stream()
			.sorted((a, b) -> a.getNumber().compareTo(b.getNumber()))
			.map(house -> new HouseStatusSummaryResponse(
				house.getId(),
				house.getNumber(),
				house.getName(),
				orchidGroupRepository.countByHouseId(house.getId()),
				orchidGroupRepository.countWarningStatusByHouseId(house.getId()),
				0,
				null
			))
			.toList();
		return new FarmStatusMapResponse(houses);
	}

	public FarmStatusOrchidGroupListResponse getOrchidGroups(FarmStatusTargetType targetType, Long targetId) {
		String targetName = resolveTargetName(targetType, targetId);
		var items = searchOrchidGroupsByTarget(targetType, targetId).stream()
			.map(FarmStatusOrchidGroupItemResponse::from)
			.toList();
		return new FarmStatusOrchidGroupListResponse(targetType, targetId, targetName, items);
	}

	public FarmStatusZoomResponse getZoom(FarmZoomLevel level, Long houseId, Long physicalBedId) {
		return switch (level) {
			case HOUSE, PHYSICAL_BED -> {
				if (houseId == null) {
					throw new IllegalArgumentException("houseId is required.");
				}
				var house = houseRepository.findById(houseId)
					.orElseThrow(() -> new NotFoundException("동을 찾을 수 없습니다."));
				var beds = physicalBedRepository.findByHouseIdOrderByDisplayOrderAsc(houseId).stream()
					.map(PhysicalBedResponse::from)
					.toList();
				yield new FarmStatusZoomResponse(level, house.getId(), house.getNumber(), beds, List.of());
			}
			case BED_ZONE -> {
				if (physicalBedId == null) {
					throw new IllegalArgumentException("physicalBedId is required.");
				}
				var physicalBed = physicalBedRepository.findById(physicalBedId)
					.orElseThrow(() -> new NotFoundException("물리 배드를 찾을 수 없습니다."));
				var zones = bedZoneRepository.findByPhysicalBedIdOrderBySortOrderAsc(physicalBedId).stream()
					.map(BedZoneResponse::from)
					.toList();
				yield new FarmStatusZoomResponse(
					level,
					physicalBed.getHouse().getId(),
					physicalBed.getHouse().getNumber(),
					List.of(),
					zones
				);
			}
		};
	}

	private String resolveTargetName(FarmStatusTargetType targetType, Long targetId) {
		return switch (targetType) {
			case HOUSE -> houseRepository.findById(targetId)
				.map(house -> house.getNumber() + "동")
				.orElseThrow(() -> new NotFoundException("동을 찾을 수 없습니다."));
			case PHYSICAL_BED -> physicalBedRepository.findById(targetId)
				.map(physicalBed -> physicalBed.getHouse().getNumber() + "동 " + physicalBed.getNumber() + "배드")
				.orElseThrow(() -> new NotFoundException("물리 배드를 찾을 수 없습니다."));
			case BED_ZONE -> bedZoneRepository.findById(targetId)
				.map(bedZone -> bedZone.getPhysicalBed().getHouse().getNumber() + "동 "
					+ bedZone.getPhysicalBed().getNumber() + "배드 " + bedZone.getName())
				.orElseThrow(() -> new NotFoundException("논리 구역을 찾을 수 없습니다."));
		};
	}

	private List<com.greenhouse.backend.farm.domain.OrchidGroup> searchOrchidGroupsByTarget(
		FarmStatusTargetType targetType,
		Long targetId
	) {
		return switch (targetType) {
			case HOUSE -> orchidGroupRepository.search(targetId, null, null, null);
			case PHYSICAL_BED -> orchidGroupRepository.search(null, targetId, null, null);
			case BED_ZONE -> orchidGroupRepository.search(null, null, targetId, null);
		};
	}
}
