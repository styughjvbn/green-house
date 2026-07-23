package com.greenhouse.backend.farm.application.inbound;

import com.greenhouse.backend.farm.application.structure.OrchidPlacementPolicy;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.domain.structure.BedZone;
import com.greenhouse.backend.farm.domain.inbound.InboundStatus;
import com.greenhouse.backend.farm.domain.inbound.InboundType;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.dto.inbound.InboundRecordPottingRequest;
import com.greenhouse.backend.farm.dto.inbound.InboundRecordResponse;
import com.greenhouse.backend.farm.repository.structure.BedZoneRepository;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class InboundPottingService {

	private static final String DEFAULT_ORCHID_STATUS = "정상";

	private final InboundRecordFinder inboundRecordFinder;
	private final BedZoneRepository bedZoneRepository;
	private final OrchidGroupRepository orchidGroupRepository;
	private final OrchidPlacementPolicy orchidPlacementPolicy;

	public InboundPottingResult potting(Long inboundRecordId, InboundRecordPottingRequest request) {
		var inboundRecord = inboundRecordFinder.find(inboundRecordId);
		if (inboundRecord.getInboundType() != InboundType.FLASK_SEEDLING) {
			throw new IllegalArgumentException("유리병 모종 입고만 포트 작업을 등록할 수 있습니다.");
		}
		if (inboundRecord.getStatus() == InboundStatus.CANCELED) {
			throw new IllegalArgumentException("취소된 입고 기록은 포트 작업을 등록할 수 없습니다.");
		}
		if (inboundRecord.getCreatedOrchidGroup() != null) {
			throw new IllegalArgumentException("이미 난 묶음이 생성된 입고 기록입니다.");
		}

		var createdGroups = request.results().stream().map(row -> {
			BedZone bedZone = findBedZone(row.bedZoneId());
			OrchidPlacementPolicy.PlacementRange placementRange = resolvePlacementRange(
					bedZone, row.startPosition(), row.endPosition());
			OrchidGroup orchidGroup = new OrchidGroup(
					bedZone,
					inboundRecord.getVariety().getGenus(),
					inboundRecord.getVariety().getName(),
					row.quantity(),
					firstNonBlank(row.potSize(), inboundRecord.getPotSize()),
					row.ageYear(),
					DEFAULT_ORCHID_STATUS,
					orchidGroupRepository.findMaxSortOrderByBedZoneId(bedZone.getId()) + 1,
					placementRange.startPosition(),
					placementRange.endPosition());
			orchidGroup.updateDetails(
					inboundRecord.getVariety().getGenus(),
					inboundRecord.getVariety().getName(),
					row.quantity(),
					firstNonBlank(row.potSize(), inboundRecord.getPotSize()),
					row.ageYear(),
					DEFAULT_ORCHID_STATUS,
					normalize(row.placementType()),
					row.trayCount(),
					Boolean.TRUE.equals(row.splitPlacementAllowed()),
					placementRange.startPosition(),
					placementRange.endPosition(),
					normalize(row.memo()));
			orchidGroup.assignVariety(inboundRecord.getVariety());
			orchidGroup.assignInboundRecord(inboundRecord);
			return orchidGroupRepository.saveAndFlush(orchidGroup);
		}).toList();

		OrchidGroup representative = createdGroups.getFirst();
		int actualQuantity = createdGroups.stream().mapToInt(OrchidGroup::getQuantity).sum();
		inboundRecord.updateMetadata(
				inboundRecord.getInboundDate(),
				inboundRecord.getBottleCount(),
				inboundRecord.getEstimatedQuantity(),
				actualQuantity,
				inboundRecord.getTempLocation(),
				inboundRecord.getPottingDueDate(),
				representative.getPotSize(),
				representative.getAgeYear(),
				normalize(request.growthStage()),
				representative.getPlacementType(),
				representative.getTrayCount(),
				normalize(request.worker()),
				appendMemo(inboundRecord.getMemo(), request.memo()));
		inboundRecord.place(representative.getBedZone(), representative, request.pottingDate(), actualQuantity);
		return new InboundPottingResult(
				InboundRecordResponse.from(inboundRecordFinder.find(inboundRecord.getId())),
				createdGroups.stream().map(OrchidGroup::getId).toList(),
				actualQuantity);
	}

	private BedZone findBedZone(Long bedZoneId) {
		if (bedZoneId == null) {
			throw new IllegalArgumentException("배치 구역이 필요합니다.");
		}
		return bedZoneRepository.findWithDetailsById(bedZoneId)
				.orElseThrow(() -> new NotFoundException("논리 구역을 찾을 수 없습니다."));
	}

	private OrchidPlacementPolicy.PlacementRange resolvePlacementRange(
			BedZone bedZone,
			BigDecimal requestedStartPosition,
			BigDecimal requestedEndPosition) {
		if (requestedStartPosition == null && requestedEndPosition == null) {
			return orchidPlacementPolicy.findFirstAvailableSingleSlot(bedZone);
		}
		BigDecimal startPosition = orchidPlacementPolicy.normalizeNumber(requestedStartPosition);
		BigDecimal endPosition = orchidPlacementPolicy.normalizeNumber(requestedEndPosition);
		orchidPlacementPolicy.validatePlacement(bedZone, startPosition, endPosition, null);
		return new OrchidPlacementPolicy.PlacementRange(startPosition, endPosition);
	}

	private String firstNonBlank(String first, String second) {
		String normalizedFirst = normalize(first);
		return normalizedFirst != null ? normalizedFirst : normalize(second);
	}

	private String appendMemo(String base, String extra) {
		String normalizedBase = normalize(base);
		String normalizedExtra = normalize(extra);
		if (normalizedExtra == null) {
			return normalizedBase;
		}
		if (normalizedBase == null) {
			return normalizedExtra;
		}
		return normalizedBase + "\n" + normalizedExtra;
	}

	private String normalize(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
