package com.greenhouse.backend.farm.application.inbound;

import com.greenhouse.backend.farm.application.structure.OrchidPlacementPolicy;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.domain.structure.BedZone;
import com.greenhouse.backend.farm.domain.inbound.InboundRecord;
import com.greenhouse.backend.farm.domain.inbound.InboundStatus;
import com.greenhouse.backend.farm.domain.inbound.InboundType;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.variety.Variety;
import com.greenhouse.backend.farm.dto.inbound.InboundNewVarietyRequest;
import com.greenhouse.backend.farm.dto.inbound.InboundRecordCancelRequest;
import com.greenhouse.backend.farm.dto.inbound.InboundRecordCreateRequest;
import com.greenhouse.backend.farm.dto.inbound.InboundRecordResponse;
import com.greenhouse.backend.farm.dto.inbound.InboundRecordUpdateRequest;
import com.greenhouse.backend.farm.repository.structure.BedZoneRepository;
import com.greenhouse.backend.farm.repository.inbound.InboundRecordRepository;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.variety.VarietyRepository;
import com.greenhouse.backend.work.application.operation.InboundWorkOperationRecorder;
import com.greenhouse.backend.work.application.operation.InboundWorkOperationLifecycleService;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class InboundRecordService {

	private static final String DEFAULT_ORCHID_STATUS = "정상";

	private final InboundRecordRepository inboundRecordRepository;
	private final VarietyRepository varietyRepository;
	private final BedZoneRepository bedZoneRepository;
	private final OrchidGroupRepository orchidGroupRepository;
	private final InboundWorkOperationRecorder inboundWorkOperationRecorder;
	private final InboundWorkOperationLifecycleService inboundWorkOperationLifecycleService;
	private final OrchidPlacementPolicy orchidPlacementPolicy;
	private final InboundRecordFinder inboundRecordFinder;
	private final InboundWorkOperationRequestFactory workOperationRequestFactory;

	public InboundRecordResponse create(InboundRecordCreateRequest request) {
		validateCreate(request, resolveCreateStatus(request));
		Variety variety = resolveVariety(request.varietyId(), request.newVariety());
		BedZone bedZone = requiresPlacement(request.inboundType()) ? findBedZone(request.bedZoneId()) : null;
		InboundStatus status = resolveCreateStatus(request);
		InboundRecord inboundRecord = new InboundRecord(
				request.inboundDate(),
				request.inboundType(),
				variety,
				status,
				request.bottleCount(),
				request.estimatedQuantity(),
				request.actualQuantity(),
				normalize(request.tempLocation()),
				request.pottingDueDate(),
				normalize(request.potSize()),
				request.ageYear(),
				normalize(request.growthStage()),
				normalize(request.placementType()),
				request.trayCount(),
				bedZone,
				normalize(request.worker()),
				normalize(request.memo()));
		InboundRecord saved = inboundRecordRepository.save(inboundRecord);

		if (requiresPlacement(request.inboundType())) {
			OrchidGroup orchidGroup = createPlacedOrchidGroup(variety, request, bedZone);
			orchidGroup.assignVariety(variety);
			orchidGroup.assignInboundRecord(saved);
			orchidGroupRepository.save(orchidGroup);
			saved.place(
					bedZone,
					orchidGroup,
					request.inboundDate(),
					resolveQuantity(request.actualQuantity(), request.estimatedQuantity()));
		} else {
			saved.markPottingPending(status);
		}
		inboundWorkOperationRecorder.record(workOperationRequestFactory.create(saved));
		return InboundRecordResponse.from(inboundRecordFinder.find(saved.getId()));
	}

	public InboundRecordResponse update(Long inboundRecordId, InboundRecordUpdateRequest request) {
		InboundRecord inboundRecord = inboundRecordFinder.find(inboundRecordId);
		if (inboundRecord.getStatus() == InboundStatus.CANCELED) {
			throw new IllegalArgumentException("취소된 입고 기록은 수정할 수 없습니다.");
		}
		inboundRecord.updateMetadata(
				request.inboundDate(),
				request.bottleCount(),
				request.estimatedQuantity(),
				request.actualQuantity(),
				normalize(request.tempLocation()),
				request.pottingDueDate(),
				normalize(request.potSize()),
				request.ageYear(),
				normalize(request.growthStage()),
				normalize(request.placementType()),
				request.trayCount(),
				normalize(request.worker()),
				normalize(request.memo()));
		return InboundRecordResponse.from(inboundRecord);
	}

	public InboundRecordResponse cancel(Long inboundRecordId, InboundRecordCancelRequest request) {
		InboundRecord inboundRecord = inboundRecordFinder.find(inboundRecordId);
		if (inboundRecord.getCreatedOrchidGroup() != null) {
			throw new IllegalArgumentException("난 묶음이 생성된 입고 기록은 취소할 수 없습니다.");
		}
		inboundWorkOperationLifecycleService.cancelForInboundRecord(inboundRecordId);
		inboundRecord.cancel(normalize(request.memo()));
		return InboundRecordResponse.from(inboundRecord);
	}

	public void delete(Long inboundRecordId) {
		InboundRecord inboundRecord = inboundRecordFinder.find(inboundRecordId);
		if (inboundRecord.getStatus() != InboundStatus.CANCELED) {
			throw new IllegalArgumentException("취소된 입고 기록만 삭제할 수 있습니다.");
		}
		if (inboundRecord.getCreatedOrchidGroup() != null) {
			throw new IllegalArgumentException("난 묶음이 생성된 입고 기록은 삭제할 수 없습니다.");
		}
		inboundRecordRepository.delete(inboundRecord);
	}

	private void validateCreate(InboundRecordCreateRequest request, InboundStatus status) {
		if (request.varietyId() == null && request.newVariety() == null) {
			throw new IllegalArgumentException("품종을 선택하거나 새 품종을 입력해야 합니다.");
		}
		if (status == InboundStatus.POTTING_IN_PROGRESS) {
			throw new IllegalArgumentException("작업중 상태는 포트 작업 계획 생성 시 자동으로 설정됩니다.");
		}
		if (request.inboundType() == InboundType.FLASK_SEEDLING) {
			if (request.estimatedQuantity() == null) {
				throw new IllegalArgumentException("유리병 모종은 예상 수량이 필요합니다.");
			}
			if (status == InboundStatus.POTTING_PENDING && request.pottingDueDate() == null) {
				throw new IllegalArgumentException("포트 작업 대기 상태는 예정일이 필요합니다.");
			}
			return;
		}
		if (request.actualQuantity() == null || request.bedZoneId() == null) {
			throw new IllegalArgumentException("즉시 배치 입고는 실제 수량과 배치 구역이 필요합니다.");
		}
	}

	private InboundStatus resolveCreateStatus(InboundRecordCreateRequest request) {
		if (request.status() != null) {
			return request.status();
		}
		if (request.inboundType() == InboundType.FLASK_SEEDLING) {
			return request.pottingDueDate() == null ? InboundStatus.TEMP_STORED : InboundStatus.POTTING_PENDING;
		}
		return InboundStatus.PLACED;
	}

	private boolean requiresPlacement(InboundType inboundType) {
		return inboundType != InboundType.FLASK_SEEDLING;
	}

	private BedZone findBedZone(Long bedZoneId) {
		if (bedZoneId == null) {
			throw new IllegalArgumentException("배치 구역이 필요합니다.");
		}
		return bedZoneRepository.findWithDetailsById(bedZoneId)
				.orElseThrow(() -> new NotFoundException("논리 구역을 찾을 수 없습니다."));
	}

	private Variety resolveVariety(Long varietyId, InboundNewVarietyRequest newVariety) {
		if (varietyId != null) {
			return varietyRepository.findById(varietyId)
					.orElseThrow(() -> new NotFoundException("품종을 찾을 수 없습니다."));
		}
		if (newVariety == null) {
			throw new IllegalArgumentException("품종을 선택하거나 새 품종을 입력해야 합니다.");
		}
		String genus = normalizeRequired(newVariety.genus());
		String name = normalizeRequired(newVariety.name());
		return varietyRepository.findByGenusAndName(genus, name)
				.orElseGet(() -> varietyRepository.save(new Variety(
						nextVarietyCode(),
						genus,
						name,
						null,
						normalize(newVariety.defaultPotSize()),
						true,
						true,
						null,
						normalize(newVariety.memo()))));
	}

	private OrchidGroup createPlacedOrchidGroup(Variety variety, InboundRecordCreateRequest request, BedZone bedZone) {
		OrchidPlacementPolicy.PlacementRange placementRange = resolvePlacementRange(
				bedZone,
				request.startPosition(),
				request.endPosition());
		OrchidGroup orchidGroup = new OrchidGroup(
				bedZone,
				variety.getGenus(),
				variety.getName(),
				resolveQuantity(request.actualQuantity(), request.estimatedQuantity()),
				normalize(request.potSize()),
				request.ageYear(),
				DEFAULT_ORCHID_STATUS,
				orchidGroupRepository.findMaxSortOrderByBedZoneId(bedZone.getId()) + 1,
				placementRange.startPosition(),
				placementRange.endPosition());
		orchidGroup.updateDetails(
				variety.getGenus(),
				variety.getName(),
				resolveQuantity(request.actualQuantity(), request.estimatedQuantity()),
				normalize(request.potSize()),
				request.ageYear(),
				DEFAULT_ORCHID_STATUS,
				normalize(request.placementType()),
				request.trayCount(),
				false,
				placementRange.startPosition(),
				placementRange.endPosition(),
				normalize(request.memo()));
		return orchidGroup;
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

	private int resolveQuantity(Integer actualQuantity, Integer estimatedQuantity) {
		Integer resolved = actualQuantity != null ? actualQuantity : estimatedQuantity;
		if (resolved == null || resolved < 1) {
			throw new IllegalArgumentException("수량은 1 이상이어야 합니다.");
		}
		return resolved;
	}

	private String nextVarietyCode() {
		long next = varietyRepository.findTopByOrderByIdDesc()
				.map(Variety::getId)
				.orElse(0L) + 1;
		return "VAR-%04d".formatted(next);
	}

	private String normalize(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private String normalizeRequired(String value) {
		String normalized = normalize(value);
		if (normalized == null) {
			throw new IllegalArgumentException("필수 문자열 값은 비워둘 수 없습니다.");
		}
		return normalized;
	}

}
