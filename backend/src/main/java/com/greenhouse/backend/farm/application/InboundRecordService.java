package com.greenhouse.backend.farm.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.domain.BedZone;
import com.greenhouse.backend.farm.domain.InboundRecord;
import com.greenhouse.backend.farm.domain.InboundStatus;
import com.greenhouse.backend.farm.domain.InboundType;
import com.greenhouse.backend.farm.domain.OrchidGroup;
import com.greenhouse.backend.farm.domain.Variety;
import com.greenhouse.backend.farm.dto.InboundNewVarietyRequest;
import com.greenhouse.backend.farm.dto.InboundRecordCancelRequest;
import com.greenhouse.backend.farm.dto.InboundRecordCreateRequest;
import com.greenhouse.backend.farm.dto.InboundRecordPageResponse;
import com.greenhouse.backend.farm.dto.InboundRecordPottingRequest;
import com.greenhouse.backend.farm.dto.InboundRecordResponse;
import com.greenhouse.backend.farm.dto.InboundRecordUpdateRequest;
import com.greenhouse.backend.farm.repository.BedZoneRepository;
import com.greenhouse.backend.farm.repository.InboundRecordRepository;
import com.greenhouse.backend.farm.repository.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.VarietyRepository;
import com.greenhouse.backend.work.application.SystemWorkCleanupService;
import com.greenhouse.backend.work.application.SystemWorkRecorder;

import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class InboundRecordService {

	private static final String DEFAULT_ORCHID_STATUS = "정상";
	private static final String INBOUND_WORK_TYPE_CODE = "INBOUND";
	private static final String POTTING_WORK_TYPE_CODE = "POTTING";
	private static final String INBOUND_RECORD_TARGET_TYPE = "INBOUND_RECORD";

	private final InboundRecordRepository inboundRecordRepository;
	private final VarietyRepository varietyRepository;
	private final BedZoneRepository bedZoneRepository;
	private final OrchidGroupRepository orchidGroupRepository;
	private final SystemWorkRecorder systemWorkRecorder;
	private final SystemWorkCleanupService systemWorkCleanupService;

	@Transactional(readOnly = true)
	public InboundRecordPageResponse getInboundRecords(
			LocalDate from,
			LocalDate to,
			InboundType inboundType,
			InboundStatus status,
			String varietyKeyword,
			int page,
			int size) {
		validatePageRequest(page, size);
		return InboundRecordPageResponse.from(inboundRecordRepository.search(
				from,
				to,
				inboundType,
				status,
				normalize(varietyKeyword) == null ? "" : normalize(varietyKeyword),
				PageRequest.of(page, size, Sort.by(
						Sort.Order.desc("inboundDate"),
						Sort.Order.desc("id"))))
				.map(InboundRecordResponse::from));
	}

	@Transactional(readOnly = true)
	public InboundRecordResponse getInboundRecord(Long inboundRecordId) {
		return InboundRecordResponse.from(findInboundRecord(inboundRecordId));
	}

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
		recordWork(
				INBOUND_WORK_TYPE_CODE,
				saved.getInboundDate(),
				INBOUND_RECORD_TARGET_TYPE,
				saved.getId(),
				saved.getVariety().getName(),
				resolveWorkQuantity(saved.getInboundType(), saved.getBottleCount(), saved.getActualQuantity(),
						saved.getEstimatedQuantity()),
				saved.getWorker(),
				saved.getMemo());
		return InboundRecordResponse.from(findInboundRecord(saved.getId()));
	}

	public InboundRecordResponse update(Long inboundRecordId, InboundRecordUpdateRequest request) {
		InboundRecord inboundRecord = findInboundRecord(inboundRecordId);
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

	public InboundRecordResponse potting(Long inboundRecordId, InboundRecordPottingRequest request) {
		InboundRecord inboundRecord = findInboundRecord(inboundRecordId);
		if (inboundRecord.getInboundType() != InboundType.FLASK_SEEDLING) {
			throw new IllegalArgumentException("유리병 모종 입고만 포트 작업을 등록할 수 있습니다.");
		}
		if (inboundRecord.getStatus() == InboundStatus.CANCELED) {
			throw new IllegalArgumentException("취소된 입고 기록은 포트 작업을 등록할 수 없습니다.");
		}
		if (inboundRecord.getCreatedOrchidGroup() != null) {
			throw new IllegalArgumentException("이미 난 묶음이 생성된 입고 기록입니다.");
		}
		BedZone bedZone = findBedZone(request.bedZoneId());
		OrchidGroup orchidGroup = new OrchidGroup(
				bedZone,
				inboundRecord.getVariety().getGenus(),
				inboundRecord.getVariety().getName(),
				request.actualQuantity(),
				firstNonBlank(request.potSize(), inboundRecord.getPotSize()),
				request.ageYear(),
				DEFAULT_ORCHID_STATUS,
				orchidGroupRepository.findMaxSortOrderByBedZoneId(bedZone.getId()) + 1);
		orchidGroup.updateDetails(
				inboundRecord.getVariety().getGenus(),
				inboundRecord.getVariety().getName(),
				request.actualQuantity(),
				firstNonBlank(request.potSize(), inboundRecord.getPotSize()),
				request.ageYear(),
				DEFAULT_ORCHID_STATUS,
				normalize(request.placementType()),
				request.trayCount(),
				false,
				normalize(request.memo()));
		orchidGroup.assignVariety(inboundRecord.getVariety());
		orchidGroup.assignInboundRecord(inboundRecord);
		orchidGroupRepository.save(orchidGroup);
		inboundRecord.updateMetadata(
				inboundRecord.getInboundDate(),
				inboundRecord.getBottleCount(),
				inboundRecord.getEstimatedQuantity(),
				request.actualQuantity(),
				inboundRecord.getTempLocation(),
				inboundRecord.getPottingDueDate(),
				firstNonBlank(request.potSize(), inboundRecord.getPotSize()),
				request.ageYear(),
				normalize(request.growthStage()),
				normalize(request.placementType()),
				request.trayCount(),
				normalize(request.worker()),
				appendMemo(inboundRecord.getMemo(), request.memo()));
		inboundRecord.place(bedZone, orchidGroup, request.pottingDate(), request.actualQuantity());
		recordWork(
				POTTING_WORK_TYPE_CODE,
				request.pottingDate(),
				"ORCHID_GROUP",
				orchidGroup.getId(),
				inboundRecord.getVariety().getName(),
				String.valueOf(request.actualQuantity()),
				normalize(request.worker()),
				normalize(request.memo()));
		return InboundRecordResponse.from(findInboundRecord(inboundRecord.getId()));
	}

	public InboundRecordResponse cancel(Long inboundRecordId, InboundRecordCancelRequest request) {
		InboundRecord inboundRecord = findInboundRecord(inboundRecordId);
		if (inboundRecord.getCreatedOrchidGroup() != null) {
			throw new IllegalArgumentException("난 묶음이 생성된 입고 기록은 취소할 수 없습니다.");
		}
		inboundRecord.cancel(normalize(request.memo()));
		return InboundRecordResponse.from(inboundRecord);
	}

	public void delete(Long inboundRecordId) {
		InboundRecord inboundRecord = findInboundRecord(inboundRecordId);
		if (inboundRecord.getStatus() != InboundStatus.CANCELED) {
			throw new IllegalArgumentException("취소된 입고 기록만 삭제할 수 있습니다.");
		}
		if (inboundRecord.getCreatedOrchidGroup() != null) {
			throw new IllegalArgumentException("난 묶음이 생성된 입고 기록은 삭제할 수 없습니다.");
		}
		systemWorkCleanupService.deleteAutoInboundCreateRecords(inboundRecordId);
		inboundRecordRepository.delete(inboundRecord);
	}

	public LocalDate getLatestInboundDate(Long varietyId) {
		return inboundRecordRepository.findLatestInboundDateByVarietyId(varietyId);
	}

	private InboundRecord findInboundRecord(Long inboundRecordId) {
		return inboundRecordRepository.findWithDetailsById(inboundRecordId)
				.orElseThrow(() -> new NotFoundException("입고 기록을 찾을 수 없습니다."));
	}

	private void validateCreate(InboundRecordCreateRequest request, InboundStatus status) {
		if (request.varietyId() == null && request.newVariety() == null) {
			throw new IllegalArgumentException("품종을 선택하거나 새 품종을 입력해야 합니다.");
		}
		if (request.inboundType() == InboundType.FLASK_SEEDLING) {
			if (request.bottleCount() == null || request.estimatedQuantity() == null) {
				throw new IllegalArgumentException("유리병 모종은 병 수와 예상 수량이 필요합니다.");
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
		OrchidGroup orchidGroup = new OrchidGroup(
				bedZone,
				variety.getGenus(),
				variety.getName(),
				resolveQuantity(request.actualQuantity(), request.estimatedQuantity()),
				normalize(request.potSize()),
				request.ageYear(),
				DEFAULT_ORCHID_STATUS,
				orchidGroupRepository.findMaxSortOrderByBedZoneId(bedZone.getId()) + 1);
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
				normalize(request.memo()));
		return orchidGroup;
	}

	private void recordWork(
			String workTypeCode,
			LocalDate workDate,
			String targetType,
			Long targetId,
			String materialName,
			String quantity,
			String worker,
			String memo) {
		systemWorkRecorder.record(
				workTypeCode,
				workDate,
				targetType,
				targetId,
				normalize(materialName),
				normalize(quantity),
				normalize(worker),
				normalize(memo));
	}

	private int resolveQuantity(Integer actualQuantity, Integer estimatedQuantity) {
		Integer resolved = actualQuantity != null ? actualQuantity : estimatedQuantity;
		if (resolved == null || resolved < 1) {
			throw new IllegalArgumentException("수량은 1 이상이어야 합니다.");
		}
		return resolved;
	}

	private String resolveWorkQuantity(InboundType inboundType, Integer bottleCount, Integer actualQuantity,
			Integer estimatedQuantity) {
		if (inboundType == InboundType.FLASK_SEEDLING && bottleCount != null) {
			return bottleCount + "병";
		}
		Integer resolved = actualQuantity != null ? actualQuantity : estimatedQuantity;
		return resolved == null ? null : String.valueOf(resolved);
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

	private String firstNonBlank(String first, String second) {
		String normalizedFirst = normalize(first);
		return normalizedFirst != null ? normalizedFirst : normalize(second);
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

	private void validatePageRequest(int page, int size) {
		if (page < 0) {
			throw new IllegalArgumentException("페이지 번호는 0 이상이어야 합니다.");
		}
		if (size < 1 || size > 100) {
			throw new IllegalArgumentException("페이지 크기는 1~100이어야 합니다.");
		}
	}
}
