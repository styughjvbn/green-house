package com.greenhouse.backend.farm.application;

import com.greenhouse.backend.common.exception.CapacityConflictException;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.domain.BedZone;
import com.greenhouse.backend.farm.domain.BedZoneSegment;
import com.greenhouse.backend.farm.domain.BedZoneSegmentCapacity;
import com.greenhouse.backend.farm.domain.OrchidGroup;
import com.greenhouse.backend.farm.domain.OrchidGroupSegmentPlacement;
import com.greenhouse.backend.farm.domain.PlacementCapacityMode;
import com.greenhouse.backend.farm.domain.PlacementRecommendationStatus;
import com.greenhouse.backend.farm.dto.OrchidGroupMovePlacementRequest;
import com.greenhouse.backend.farm.dto.PlacementRecommendationAllocationResponse;
import com.greenhouse.backend.farm.dto.PlacementRecommendationCandidateResponse;
import com.greenhouse.backend.farm.dto.PlacementRecommendationResponse;
import com.greenhouse.backend.farm.dto.PlacementRequirementResponse;
import com.greenhouse.backend.farm.repository.BedZoneRepository;
import com.greenhouse.backend.farm.repository.BedZoneSegmentRepository;
import com.greenhouse.backend.farm.repository.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.OrchidGroupSegmentPlacementRepository;

import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PlacementRecommendationService {

	private final OrchidGroupRepository orchidGroupRepository;
	private final BedZoneRepository bedZoneRepository;
	private final BedZoneSegmentRepository segmentRepository;
	private final OrchidGroupSegmentPlacementRepository placementRepository;

	public PlacementRecommendationResponse recommend(Long orchidGroupId, Long houseId) {
		OrchidGroup group = findGroup(orchidGroupId);
		String placementType = requirePlacementType(group);
		int requiredUnits = requiredUnits(group);
		List<BedZone> zones = houseId == null ? bedZoneRepository.findAll() : bedZoneRepository.findByHouseId(houseId);
		List<PlacementRecommendationCandidateResponse> candidates = zones.stream()
				.filter(BedZone::getActive)
				.map(zone -> evaluateZone(group, zone, placementType, requiredUnits))
				.sorted(candidateComparator())
				.toList();
		return new PlacementRecommendationResponse(
				group.getId(), group.getVarietyName(),
				new PlacementRequirementResponse(placementType, group.getPotSize(), group.getQuantity(), requiredUnits,
						group.getSplitPlacementAllowed()),
				candidates);
	}

	public List<OrchidGroupSegmentPlacement> validateAndCreatePlacements(
			OrchidGroup group,
			BedZone destination,
			PlacementCapacityMode mode,
			List<OrchidGroupMovePlacementRequest> requests,
			LocalDate reorganizeDueDate,
			String memo) {
		if (mode == null)
			throw new IllegalArgumentException("배치 모드는 필수입니다.");
		if (mode == PlacementCapacityMode.TEMPORARY && reorganizeDueDate == null) {
			throw new IllegalArgumentException("임시 배치는 재정리 예정일이 필요합니다.");
		}
		if (requests == null || requests.isEmpty())
			throw new IllegalArgumentException("구간별 배치 정보가 필요합니다.");
		if (!group.getSplitPlacementAllowed() && requests.size() > 1)
			throw new IllegalArgumentException("분할 배치가 허용되지 않은 난 묶음입니다.");
		String placementType = requirePlacementType(group);
		Set<Long> segmentIds = new HashSet<>();
		List<OrchidGroupSegmentPlacement> result = new ArrayList<>();
		int totalQuantity = 0;
		int totalUnits = 0;
		for (var request : requests) {
			if (!segmentIds.add(request.segmentId()))
				throw new IllegalArgumentException("같은 구간을 중복 지정할 수 없습니다.");
			BedZoneSegment segment = segmentRepository.findByIdForUpdate(request.segmentId())
					.orElseThrow(() -> new NotFoundException("배드 구간을 찾을 수 없습니다."));
			if (!segment.getBedZone().getId().equals(destination.getId()))
				throw new IllegalArgumentException("목적 구역에 속하지 않은 구간입니다.");
			int units = usesTrayUnits(placementType) ? requireTrayCount(request.trayCount()) : request.quantity();
			int available = availableUnits(segment, group, placementType, group.getPotSize(), mode);
			if (units > available)
				throw new CapacityConflictException(segment.getName() + "의 남은 수용량이 부족합니다.");
			totalQuantity += request.quantity();
			totalUnits += units;
			result.add(new OrchidGroupSegmentPlacement(segment, request.quantity(), request.trayCount(), mode,
					reorganizeDueDate, memo));
		}
		if (totalQuantity != group.getQuantity())
			throw new IllegalArgumentException("구간별 수량 합계가 난 묶음 수량과 다릅니다.");
		if (totalUnits != requiredUnits(group))
			throw new IllegalArgumentException("구간별 점유 단위 합계가 난 묶음 점유량과 다릅니다.");
		return result;
	}

	private PlacementRecommendationCandidateResponse evaluateZone(OrchidGroup group, BedZone zone, String placementType,
			int requiredUnits) {
		if (zone.getOrchidGroups().stream()
				.anyMatch(item -> !item.getId().equals(group.getId()) && item.getSegmentPlacements().isEmpty())) {
			return unavailable(zone, "구간이 지정되지 않은 기존 난 묶음이 있습니다.");
		}
		List<BedZoneSegment> segments = segmentRepository.findByBedZoneIdOrderBySortOrderAsc(zone.getId());
		if (segments.isEmpty())
			return unavailable(zone, "배드 구간 설정이 없습니다.");
		for (PlacementCapacityMode mode : PlacementCapacityMode.values()) {
			AllocationResult allocation = allocate(segments, group, placementType, group.getPotSize(), mode,
					requiredUnits);
			if (allocation.complete()) {
				return candidate(zone, statusFor(mode), mode, allocation.allocations(), allocation.warnings());
			}
		}
		return unavailable(zone, "압축·임시 배치 기준으로도 수용량이 부족하거나 해당 규격이 허용되지 않습니다.");
	}

	private AllocationResult allocate(List<BedZoneSegment> segments, OrchidGroup group, String placementType,
			String potSize, PlacementCapacityMode mode, int requiredUnits) {
		List<PlacementRecommendationAllocationResponse> allocations = new ArrayList<>();
		List<String> warnings = new ArrayList<>();
		int remaining = requiredUnits;
		if (!group.getSplitPlacementAllowed()) {
			for (BedZoneSegment segment : segments) {
				int available = availableUnits(segment, group, placementType, potSize, mode);
				if (available >= requiredUnits) {
					allocations.add(allocation(segment, group, requiredUnits, requiredUnits, available));
					collectWarnings(segment, placementType, potSize, mode, warnings);
					return new AllocationResult(true, allocations, warnings);
				}
			}
			return new AllocationResult(false, List.of(), List.of());
		}
		for (BedZoneSegment segment : segments) {
			int available = availableUnits(segment, group, placementType, potSize, mode);
			if (available <= 0)
				continue;
			int allocated = Math.min(available, remaining);
			allocations.add(allocation(segment, group, allocated, requiredUnits, available));
			collectWarnings(segment, placementType, potSize, mode, warnings);
			remaining -= allocated;
			if (remaining == 0)
				return new AllocationResult(true, normalizeQuantities(allocations, group.getQuantity()),
						warnings.stream().distinct().toList());
		}
		return new AllocationResult(false, List.of(), List.of());
	}

	private PlacementRecommendationAllocationResponse allocation(BedZoneSegment segment, OrchidGroup group, int units,
			int totalUnits, int available) {
		int quantity = Math.max(1, (int) Math.floor((double) group.getQuantity() * units / totalUnits));
		return new PlacementRecommendationAllocationResponse(segment.getId(), segment.getName(), quantity, units,
				available - units);
	}

	private List<PlacementRecommendationAllocationResponse> normalizeQuantities(
			List<PlacementRecommendationAllocationResponse> allocations, int totalQuantity) {
		int assigned = allocations.stream().mapToInt(PlacementRecommendationAllocationResponse::quantity).sum();
		int difference = totalQuantity - assigned;
		if (difference == 0 || allocations.isEmpty())
			return allocations;
		List<PlacementRecommendationAllocationResponse> adjusted = new ArrayList<>(allocations);
		var last = adjusted.getLast();
		adjusted.set(adjusted.size() - 1, new PlacementRecommendationAllocationResponse(
				last.segmentId(), last.segmentName(), last.quantity() + difference, last.occupancyUnits(),
				last.remainingUnits()));
		return adjusted;
	}

	private int availableUnits(BedZoneSegment segment, OrchidGroup excludedGroup, String placementType, String potSize,
			PlacementCapacityMode mode) {
		BedZoneSegmentCapacity target = findCapacity(segment, placementType, potSize, mode);
		if (target == null || !target.getAllowed() || target.getCapacityValue() <= 0)
			return 0;
		double occupiedRatio = 0;
		for (OrchidGroupSegmentPlacement placement : placementRepository.findBySegmentId(segment.getId())) {
			if (placement.getOrchidGroup().getId().equals(excludedGroup.getId()))
				continue;
			OrchidGroup occupiedGroup = placement.getOrchidGroup();
			String occupiedType = normalizeType(occupiedGroup.getPlacementType());
			if (occupiedType == null)
				return 0;
			BedZoneSegmentCapacity occupiedCapacity = findCapacity(segment, occupiedType, occupiedGroup.getPotSize(),
					mode);
			if (occupiedCapacity == null || !occupiedCapacity.getAllowed() || occupiedCapacity.getCapacityValue() <= 0)
				return 0;
			int occupiedUnits = usesTrayUnits(occupiedType) ? Objects.requireNonNullElse(placement.getTrayCount(), 0)
					: placement.getQuantity();
			occupiedRatio += (double) occupiedUnits / occupiedCapacity.getCapacityValue();
		}
		return Math.max(0, (int) Math.floor(target.getCapacityValue() * Math.max(0, 1 - occupiedRatio)));
	}

	private BedZoneSegmentCapacity findCapacity(BedZoneSegment segment, String placementType, String potSize,
			PlacementCapacityMode mode) {
		return segment.getCapacities().stream()
				.filter(capacity -> capacity.getPlacementType().equals(placementType)
						&& capacity.getCapacityMode() == mode)
				.filter(capacity -> Objects.equals(capacity.getPotSize(), potSize))
				.findFirst()
				.orElseGet(() -> segment.getCapacities().stream()
						.filter(capacity -> capacity.getPlacementType().equals(placementType)
								&& capacity.getCapacityMode() == mode && capacity.getPotSize() == null)
						.findFirst().orElse(null));
	}

	private void collectWarnings(BedZoneSegment segment, String placementType, String potSize,
			PlacementCapacityMode mode, List<String> warnings) {
		if (segment.getMemo() != null)
			warnings.add(segment.getName() + ": " + segment.getMemo());
		BedZoneSegmentCapacity capacity = findCapacity(segment, placementType, potSize, mode);
		if (capacity != null && capacity.getMemo() != null)
			warnings.add(segment.getName() + ": " + capacity.getMemo());
	}

	private PlacementRecommendationCandidateResponse unavailable(BedZone zone, String warning) {
		return candidate(zone, PlacementRecommendationStatus.UNAVAILABLE, null, List.of(), List.of(warning));
	}

	private PlacementRecommendationCandidateResponse candidate(BedZone zone, PlacementRecommendationStatus status,
			PlacementCapacityMode mode, List<PlacementRecommendationAllocationResponse> allocations,
			List<String> warnings) {
		var bed = zone.getPhysicalBed();
		var house = bed.getHouse();
		return new PlacementRecommendationCandidateResponse(zone.getId(), zone.getName(), house.getId(),
				house.getNumber(), bed.getId(), bed.getNumber(), status, mode, allocations, warnings);
	}

	private Comparator<PlacementRecommendationCandidateResponse> candidateComparator() {
		return Comparator.comparing((PlacementRecommendationCandidateResponse value) -> value.status().ordinal())
				.thenComparing(
						value -> value.requiredMode() == null ? Integer.MAX_VALUE : value.requiredMode().ordinal())
				.thenComparing(PlacementRecommendationCandidateResponse::houseNumber)
				.thenComparing(PlacementRecommendationCandidateResponse::physicalBedNumber);
	}

	private PlacementRecommendationStatus statusFor(PlacementCapacityMode mode) {
		return switch (mode) {
			case SPACIOUS, STANDARD -> PlacementRecommendationStatus.RECOMMENDED;
			case EXPANDED -> PlacementRecommendationStatus.POSSIBLE;
			case COMPRESSED, TEMPORARY -> PlacementRecommendationStatus.WARNING;
		};
	}

	private OrchidGroup findGroup(Long id) {
		return orchidGroupRepository.findById(id).orElseThrow(() -> new NotFoundException("난 묶음을 찾을 수 없습니다."));
	}

	private String requirePlacementType(OrchidGroup group) {
		String value = normalizeType(group.getPlacementType());
		if (value == null)
			throw new IllegalArgumentException("추천 전에 난 묶음의 배치 규격을 지정해야 합니다.");
		return value;
	}

	private String normalizeType(String value) {
		if (value == null || value.isBlank())
			return null;
		return value.trim().toUpperCase();
	}

	private boolean usesTrayUnits(String type) {
		return type.startsWith("TRAY_") || type.startsWith("CUSTOM:");
	}

	private int requiredUnits(OrchidGroup group) {
		String type = requirePlacementType(group);
		return usesTrayUnits(type) ? requireTrayCount(group.getTrayCount()) : group.getQuantity();
	}

	private int requireTrayCount(Integer value) {
		if (value == null || value < 1)
			throw new IllegalArgumentException("판 배치는 실제 판 수가 필요합니다.");
		return value;
	}

	private record AllocationResult(boolean complete, List<PlacementRecommendationAllocationResponse> allocations,
			List<String> warnings) {
	}
}
