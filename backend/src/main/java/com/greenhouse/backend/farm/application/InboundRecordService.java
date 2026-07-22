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
import com.greenhouse.backend.work.application.operation.InboundWorkOperationRecorder;
import com.greenhouse.backend.work.application.operation.InboundWorkOperationLifecycleService;
import com.greenhouse.backend.work.dto.operation.InboundWorkOperationCreateRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
		Map<String, Object> workDetails = inboundWorkDetails(saved);
		inboundWorkOperationRecorder.record(new InboundWorkOperationCreateRequest(
				saved.getId(),
				saved.getInboundDate(),
				saved.getVariety().getId(),
				saved.getVariety().getName(),
				resolveQuantity(saved.getActualQuantity(), saved.getEstimatedQuantity()),
				saved.getPotSize(),
				inboundLocationSnapshot(saved),
				saved.getCreatedOrchidGroup() == null ? null : saved.getCreatedOrchidGroup().getId(),
				saved.getWorker(),
				inboundWorkMemo(saved),
				workDetails));
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

	public InboundPottingResult pottingForOperation(
			Long inboundRecordId,
			InboundRecordPottingRequest request) {
		return potting(inboundRecordId, request);
	}

	private InboundPottingResult potting(
			Long inboundRecordId,
			InboundRecordPottingRequest request) {
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
		inboundRecord.place(
				representative.getBedZone(), representative, request.pottingDate(), actualQuantity);
		return new InboundPottingResult(
				InboundRecordResponse.from(findInboundRecord(inboundRecord.getId())),
				createdGroups.stream().map(OrchidGroup::getId).toList(),
				actualQuantity);
	}

	public InboundRecordResponse cancel(Long inboundRecordId, InboundRecordCancelRequest request) {
		InboundRecord inboundRecord = findInboundRecord(inboundRecordId);
		if (inboundRecord.getCreatedOrchidGroup() != null) {
			throw new IllegalArgumentException("난 묶음이 생성된 입고 기록은 취소할 수 없습니다.");
		}
		inboundWorkOperationLifecycleService.cancelForInboundRecord(inboundRecordId);
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

	private Map<String, Object> inboundWorkDetails(InboundRecord record) {
		Map<String, Object> details = new LinkedHashMap<>();
		putDetail(details, "inboundRecordId", record.getId());
		putDetail(details, "inboundType", record.getInboundType());
		putDetail(details, "status", record.getStatus());
		putDetail(details, "varietyId", record.getVariety().getId());
		putDetail(details, "genus", record.getVariety().getGenus());
		putDetail(details, "varietyName", record.getVariety().getName());
		putDetail(details, "bottleCount", record.getBottleCount());
		putDetail(details, "estimatedQuantity", record.getEstimatedQuantity());
		putDetail(details, "actualQuantity", record.getActualQuantity());
		putDetail(details, "tempLocation", record.getTempLocation());
		putDetail(details, "pottingDueDate", record.getPottingDueDate());
		putDetail(details, "potSize", record.getPotSize());
		putDetail(details, "ageYear", record.getAgeYear());
		putDetail(details, "growthStage", record.getGrowthStage());
		putDetail(details, "placementType", record.getPlacementType());
		putDetail(details, "trayCount", record.getTrayCount());
		putDetail(details, "bedZoneId", record.getBedZone() == null ? null : record.getBedZone().getId());
		putDetail(details, "orchidGroupId",
				record.getCreatedOrchidGroup() == null ? null : record.getCreatedOrchidGroup().getId());
		return details;
	}

	private Map<String, Object> inboundLocationSnapshot(InboundRecord record) {
		Map<String, Object> location = new LinkedHashMap<>();
		if (record.getBedZone() != null) {
			BedZone zone = record.getBedZone();
			putDetail(location, "houseId", zone.getPhysicalBed().getHouse().getId());
			putDetail(location, "houseNumber", zone.getPhysicalBed().getHouse().getNumber());
			putDetail(location, "physicalBedId", zone.getPhysicalBed().getId());
			putDetail(location, "physicalBedNumber", zone.getPhysicalBed().getNumber());
			putDetail(location, "bedZoneId", zone.getId());
			putDetail(location, "bedZoneName", zone.getName());
		} else {
			putDetail(location, "tempLocation", record.getTempLocation());
			putDetail(location, "pottingDueDate", record.getPottingDueDate());
		}
		return location;
	}

	private String inboundWorkMemo(InboundRecord record) {
		String autoMemo = String.join("\n",
				"입고 유형: " + formatInboundType(record.getInboundType()),
				"품종: " + record.getVariety().getName(),
				"병수: " + formatBottleCount(record.getBottleCount()),
				"상태: " + formatInboundStatus(record.getStatus()));
		return appendMemo(record.getMemo(), autoMemo);
	}

	private String formatInboundType(InboundType inboundType) {
		return switch (inboundType) {
			case FLASK_SEEDLING -> "유리병 모종";
			case POTTED_SEEDLING -> "포트 모종";
			case PRODUCT_POT -> "상품분";
			case SAMPLE -> "샘플";
			case ETC -> "기타";
		};
	}

	private String formatInboundStatus(InboundStatus status) {
		return switch (status) {
			case TEMP_STORED -> "임시보관";
			case POTTING_PENDING -> "포트작업대기";
			case POTTING_IN_PROGRESS -> "작업중";
			case POTTED -> "포트작업완료";
			case PLACED -> "배치완료";
			case CANCELED -> "취소";
		};
	}

	private String formatBottleCount(Integer bottleCount) {
		return bottleCount == null ? "-" : bottleCount + "병";
	}

	private void putDetail(Map<String, Object> details, String key, Object value) {
		if (value != null) {
			details.put(key, value);
		}
	}

	private int resolveQuantity(Integer actualQuantity, Integer estimatedQuantity) {
		Integer resolved = actualQuantity != null ? actualQuantity : estimatedQuantity;
		if (resolved == null || resolved < 1) {
			throw new IllegalArgumentException("수량은 1 이상이어야 합니다.");
		}
		return resolved;
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
