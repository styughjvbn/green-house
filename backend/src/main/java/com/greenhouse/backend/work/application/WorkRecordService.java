package com.greenhouse.backend.work.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.repository.BedZoneRepository;
import com.greenhouse.backend.farm.repository.HouseRepository;
import com.greenhouse.backend.farm.repository.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.PhysicalBedRepository;
import com.greenhouse.backend.work.domain.WorkRecord;
import com.greenhouse.backend.work.dto.WorkRecordCreateRequest;
import com.greenhouse.backend.work.dto.WorkRecordResponse;
import com.greenhouse.backend.work.repository.WorkRecordRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class WorkRecordService {

	public static final List<String> DEFAULT_WORK_TYPES = List.of(
		"농약",
		"비료",
		"분갈이",
		"상태 기록",
		"일반 메모",
		"잎 정리",
		"잡초 정리",
		"단화/꽃 정리",
		"위치 이동"
	);

	private final WorkRecordRepository workRecordRepository;
	private final HouseRepository houseRepository;
	private final PhysicalBedRepository physicalBedRepository;
	private final BedZoneRepository bedZoneRepository;
	private final OrchidGroupRepository orchidGroupRepository;

	public WorkRecordService(
		WorkRecordRepository workRecordRepository,
		HouseRepository houseRepository,
		PhysicalBedRepository physicalBedRepository,
		BedZoneRepository bedZoneRepository,
		OrchidGroupRepository orchidGroupRepository
	) {
		this.workRecordRepository = workRecordRepository;
		this.houseRepository = houseRepository;
		this.physicalBedRepository = physicalBedRepository;
		this.bedZoneRepository = bedZoneRepository;
		this.orchidGroupRepository = orchidGroupRepository;
	}

	@Transactional(readOnly = true)
	public List<WorkRecordResponse> getWorkRecords(
		String targetType,
		Long targetId,
		String workType,
		LocalDate from,
		LocalDate to
	) {
		if (targetType != null) {
			validateTarget(targetType, targetId);
		}
		return workRecordRepository.search(normalize(targetType), targetId, normalize(workType), from, to).stream()
			.map(WorkRecordResponse::from)
			.toList();
	}

	public WorkRecordResponse create(WorkRecordCreateRequest request) {
		validateWorkType(request.workType());
		validateTarget(request.targetType(), request.targetId());
		var workRecord = new WorkRecord(
			normalizeRequired(request.workType()),
			request.workDate(),
			normalizeRequired(request.targetType()),
			request.targetId(),
			normalize(request.materialName()),
			normalize(request.dilutionRatio()),
			normalize(request.quantity()),
			normalize(request.worker()),
			normalize(request.memo())
		);
		return WorkRecordResponse.from(workRecordRepository.save(workRecord));
	}

	public List<String> getWorkTypes() {
		return DEFAULT_WORK_TYPES;
	}

	private void validateWorkType(String workType) {
		if (!DEFAULT_WORK_TYPES.contains(normalizeRequired(workType))) {
			throw new IllegalArgumentException("지원하지 않는 작업 유형입니다.");
		}
	}

	private void validateTarget(String targetType, Long targetId) {
		String normalizedTargetType = normalizeRequired(targetType);
		if ("FARM".equals(normalizedTargetType)) {
			return;
		}
		if (targetId == null) {
			throw new IllegalArgumentException("작업 대상 ID가 필요합니다.");
		}
		boolean exists = switch (normalizedTargetType) {
			case "HOUSE" -> houseRepository.existsById(targetId);
			case "PHYSICAL_BED" -> physicalBedRepository.existsById(targetId);
			case "BED_ZONE" -> bedZoneRepository.existsById(targetId);
			case "ORCHID_GROUP" -> orchidGroupRepository.existsById(targetId);
			default -> throw new IllegalArgumentException("지원하지 않는 작업 대상입니다.");
		};
		if (!exists) {
			throw new NotFoundException("작업 대상을 찾을 수 없습니다.");
		}
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
