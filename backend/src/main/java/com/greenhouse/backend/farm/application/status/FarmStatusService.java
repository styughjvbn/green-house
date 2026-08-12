package com.greenhouse.backend.farm.application.status;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.domain.status.FarmStatusTargetType;
import com.greenhouse.backend.farm.domain.status.FarmZoomLevel;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class FarmStatusService {
	private static final Set<String> WARNING_STATUSES = Set.of("주의", "이상", "병해충");

	private final HouseRepository houseRepository;
	private final PhysicalBedRepository physicalBedRepository;
	private final BedZoneRepository bedZoneRepository;
	private final OrchidGroupRepository orchidGroupRepository;

	public FarmStatusMapResponse getMap() {
		var mapOrchidGroups = orchidGroupRepository.search(null, "", null, null, null);
		var groupsByHouseId = mapOrchidGroups.stream().collect(Collectors.groupingBy(
				group -> group.getBedZone().getPhysicalBed().getHouse().getId()));
		var bedsByHouseId = physicalBedRepository.findAllInFarmOrder().stream()
				.collect(Collectors.groupingBy(bed -> bed.getHouse().getId()));
		var houses = houseRepository.findAll().stream()
				.sorted((a, b) -> a.getNumber().compareTo(b.getNumber()))
				.map(house -> {
					var houseGroups = groupsByHouseId.getOrDefault(house.getId(), List.of());
					return new HouseStatusSummaryResponse(
							house.getId(),
							house.getNumber(),
							house.getName(),
							houseGroups.size(),
							houseGroups.stream()
									.filter(group -> WARNING_STATUSES.contains(group.getStatus()))
									.count(),
							0,
							null,
							bedsByHouseId.getOrDefault(house.getId(), List.of()).stream()
									.map(FarmStatusMapPhysicalBedResponse::from)
									.toList());
				})
				.toList();
		return new FarmStatusMapResponse(
				houses,
				mapOrchidGroups.stream().map(FarmStatusMapOrchidGroupResponse::from).toList());
	}

	public OrchidManagementViewportResponse getOrchidManagementViewport(Long startBedId, int bedCount) {
		if (bedCount < 2 || bedCount > 4) {
			throw new IllegalArgumentException("bedCount must be between 2 and 4.");
		}

		var allBedRows = physicalBedRepository.findAllOrderRows();
		if (allBedRows.isEmpty()) {
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
			for (int index = 0; index < allBedRows.size(); index++) {
				if (allBedRows.get(index).id().equals(startBedId)) {
					requestedIndex = index;
					break;
				}
			}
		}
		int startIndex = Math.min(requestedIndex, allBedRows.size() - 1);
		var visibleBedRows = allBedRows.subList(startIndex, Math.min(startIndex + bedCount, allBedRows.size()));
		var visibleBedIds = visibleBedRows.stream().map(row -> row.id()).toList();
		var visibleBeds = physicalBedRepository.findAllWithZonesByIdIn(visibleBedIds);
		var groupsByZoneId = loadGroupsByZoneId(visibleBedIds);

		long orchidGroupCount = 0;
		long totalQuantity = 0;
		long abnormalCount = 0;
		long bedZoneCount = 0;
		for (var bed : visibleBeds) {
			bedZoneCount += bed.getBedZones().size();
			for (var zone : bed.getBedZones()) {
				for (var orchidGroup : groupsByZoneId.getOrDefault(zone.getId(), List.of())) {
					if (orchidGroup.getQuantity() == null || orchidGroup.getQuantity() <= 0) {
						continue;
					}
					orchidGroupCount++;
					totalQuantity += orchidGroup.getQuantity();
					if (WARNING_STATUSES.contains(orchidGroup.getStatus())) {
						abnormalCount++;
					}
				}
			}
		}

		return new OrchidManagementViewportResponse(
				visibleBedRows.getFirst().id(),
				bedCount,
				visibleBeds.stream().map(bed -> PhysicalBedResponse.from(bed, groupsByZoneId)).toList(),
				startIndex > 0,
				startIndex + bedCount < allBedRows.size(),
				new OrchidManagementSummaryResponse(
						orchidGroupCount,
						totalQuantity,
						abnormalCount,
						bedZoneCount),
				allBedRows.stream()
						.map(row -> new OrchidManagementBedOrderResponse(
								row.id(), row.houseId(), row.houseNumber(), row.number()))
						.toList());
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
				var beds = physicalBedRepository.findByHouseIdOrderByDisplayOrderAsc(houseId);
				var bedIds = beds.stream().map(bed -> bed.getId()).toList();
				var groupsByZoneId = loadGroupsByZoneId(bedIds);
				var responses = beds.stream()
						.map(bed -> PhysicalBedResponse.from(bed, groupsByZoneId))
						.toList();
				yield new FarmStatusZoomResponse(level, house.getId(), house.getNumber(), responses, List.of());
			}
			case BED_ZONE -> {
				if (physicalBedId == null) {
					throw new IllegalArgumentException("physicalBedId is required.");
				}
				var physicalBed = physicalBedRepository.findWithHouseAndBedZonesById(physicalBedId)
						.orElseThrow(() -> new NotFoundException("물리 다이를 찾을 수 없습니다."));
				var groupsByZoneId = loadGroupsByZoneId(List.of(physicalBedId));
				var zones = physicalBed.getBedZones().stream()
						.map(zone -> BedZoneResponse.from(
								zone,
								groupsByZoneId.getOrDefault(zone.getId(), List.of())))
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

	private Map<Long, List<OrchidGroup>> loadGroupsByZoneId(
			List<Long> physicalBedIds) {
		if (physicalBedIds.isEmpty()) {
			return Map.of();
		}
		return orchidGroupRepository.findByPhysicalBedIdInOrderByLocation(physicalBedIds).stream()
				.collect(Collectors.groupingBy(
						group -> group.getBedZone().getId(),
						java.util.LinkedHashMap::new,
						Collectors.toList()));
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

	private List<OrchidGroup> searchOrchidGroupsByTarget(
			FarmStatusTargetType targetType,
			Long targetId) {
		return switch (targetType) {
			case HOUSE -> orchidGroupRepository.search(targetId, "", null, null, null);
			case PHYSICAL_BED -> orchidGroupRepository.search(null, "", targetId, null, null);
			case BED_ZONE -> orchidGroupRepository.search(null, "", null, targetId, null);
		};
	}
}
