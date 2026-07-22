package com.greenhouse.backend.farm.application.status;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.domain.status.FarmStatusTargetType;
import com.greenhouse.backend.farm.domain.status.FarmZoomLevel;
import com.greenhouse.backend.farm.dto.structure.BedZoneResponse;
import com.greenhouse.backend.farm.dto.status.FarmStatusMapResponse;
import com.greenhouse.backend.farm.dto.status.FarmStatusMapOrchidGroupResponse;
import com.greenhouse.backend.farm.dto.status.FarmStatusMapPhysicalBedResponse;
import com.greenhouse.backend.farm.dto.status.FarmStatusOrchidGroupItemResponse;
import com.greenhouse.backend.farm.dto.status.FarmStatusOrchidGroupListResponse;
import com.greenhouse.backend.farm.dto.status.FarmStatusZoomResponse;
import com.greenhouse.backend.farm.dto.status.HouseStatusSummaryResponse;
import com.greenhouse.backend.farm.dto.orchid.OrchidManagementBedOrderResponse;
import com.greenhouse.backend.farm.dto.orchid.OrchidManagementSummaryResponse;
import com.greenhouse.backend.farm.dto.orchid.OrchidManagementViewportResponse;
import com.greenhouse.backend.farm.dto.structure.PhysicalBedResponse;
import com.greenhouse.backend.farm.repository.structure.BedZoneRepository;
import com.greenhouse.backend.farm.repository.structure.HouseRepository;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.structure.PhysicalBedRepository;

import lombok.RequiredArgsConstructor;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class FarmStatusService {

	private final HouseRepository houseRepository;
	private final PhysicalBedRepository physicalBedRepository;
	private final BedZoneRepository bedZoneRepository;
	private final OrchidGroupRepository orchidGroupRepository;

	public FarmStatusMapResponse getMap() {
		var mapOrchidGroups = orchidGroupRepository.search(null, "", null, null, null);
		var houses = houseRepository.findAll().stream()
				.sorted((a, b) -> a.getNumber().compareTo(b.getNumber()))
				.map(house -> new HouseStatusSummaryResponse(
						house.getId(),
						house.getNumber(),
						house.getName(),
						mapOrchidGroups.stream()
								.filter(group -> group.getBedZone().getPhysicalBed().getHouse().getId().equals(house.getId()))
								.count(),
						mapOrchidGroups.stream()
								.filter(group -> group.getBedZone().getPhysicalBed().getHouse().getId().equals(house.getId()))
								.filter(group -> List.of("주의", "이상", "병해충").contains(group.getStatus()))
								.count(),
						0,
						null,
						physicalBedRepository.findByHouseIdOrderByDisplayOrderAsc(house.getId()).stream()
								.map(FarmStatusMapPhysicalBedResponse::from)
								.toList()))
				.toList();
		return new FarmStatusMapResponse(
				houses,
				mapOrchidGroups.stream().map(FarmStatusMapOrchidGroupResponse::from).toList());
	}

	public OrchidManagementViewportResponse getOrchidManagementViewport(Long startBedId, int bedCount) {
		if (bedCount < 2 || bedCount > 4) {
			throw new IllegalArgumentException("bedCount must be between 2 and 4.");
		}

		var allBeds = physicalBedRepository.findAllInFarmOrder();
		if (allBeds.isEmpty()) {
			return new OrchidManagementViewportResponse(
					null,
					bedCount,
					List.of(),
					false,
					false,
					new OrchidManagementSummaryResponse(0, 0, 0, 0),
					List.of());
		}

		int requestedIndex = 0;
		if (startBedId != null) {
			for (int index = 0; index < allBeds.size(); index++) {
				if (allBeds.get(index).getId().equals(startBedId)) {
					requestedIndex = index;
					break;
				}
			}
		}
		int startIndex = Math.min(requestedIndex, Math.max(0, allBeds.size() - bedCount));
		var visibleBeds = allBeds.subList(startIndex, Math.min(startIndex + bedCount, allBeds.size()));

		long orchidGroupCount = 0;
		long totalQuantity = 0;
		long abnormalCount = 0;
		long bedZoneCount = 0;
		for (var bed : visibleBeds) {
			bedZoneCount += bed.getBedZones().size();
			for (var zone : bed.getBedZones()) {
				for (var orchidGroup : zone.getOrchidGroups()) {
					if (orchidGroup.getQuantity() == null || orchidGroup.getQuantity() <= 0) {
						continue;
					}
					orchidGroupCount++;
					totalQuantity += orchidGroup.getQuantity();
					if (List.of("주의", "이상", "병해충").contains(orchidGroup.getStatus())) {
						abnormalCount++;
					}
				}
			}
		}

		return new OrchidManagementViewportResponse(
				visibleBeds.getFirst().getId(),
				bedCount,
				visibleBeds.stream().map(PhysicalBedResponse::from).toList(),
				startIndex > 0,
				startIndex + bedCount < allBeds.size(),
				new OrchidManagementSummaryResponse(
						orchidGroupCount,
						totalQuantity,
						abnormalCount,
						bedZoneCount),
				allBeds.stream().map(OrchidManagementBedOrderResponse::from).toList());
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
						.orElseThrow(() -> new NotFoundException("물리 다이를 찾을 수 없습니다."));
				var zones = bedZoneRepository.findByPhysicalBedIdOrderBySortOrderAsc(physicalBedId).stream()
						.map(BedZoneResponse::from)
						.toList();
				yield new FarmStatusZoomResponse(
						level,
						physicalBed.getHouse().getId(),
						physicalBed.getHouse().getNumber(),
						List.of(),
						zones);
			}
		};
	}

	private String resolveTargetName(FarmStatusTargetType targetType, Long targetId) {
		return switch (targetType) {
			case HOUSE -> houseRepository.findById(targetId)
					.map(house -> house.getNumber() + "동")
					.orElseThrow(() -> new NotFoundException("동을 찾을 수 없습니다."));
			case PHYSICAL_BED -> physicalBedRepository.findById(targetId)
					.map(physicalBed -> physicalBed.getHouse().getNumber() + "동 " + physicalBed.getNumber() + "다이")
					.orElseThrow(() -> new NotFoundException("다이를 찾을 수 없습니다."));
			case BED_ZONE -> bedZoneRepository.findById(targetId)
					.map(bedZone -> bedZone.getPhysicalBed().getHouse().getNumber() + "동 "
							+ bedZone.getPhysicalBed().getNumber() + "다이 " + bedZone.getName())
					.orElseThrow(() -> new NotFoundException("논리 구역을 찾을 수 없습니다."));
		};
	}

	private List<com.greenhouse.backend.farm.domain.orchid.OrchidGroup> searchOrchidGroupsByTarget(
			FarmStatusTargetType targetType,
			Long targetId) {
		return switch (targetType) {
			case HOUSE -> orchidGroupRepository.search(targetId, "", null, null, null);
			case PHYSICAL_BED -> orchidGroupRepository.search(null, "", targetId, null, null);
			case BED_ZONE -> orchidGroupRepository.search(null, "", null, targetId, null);
		};
	}
}
