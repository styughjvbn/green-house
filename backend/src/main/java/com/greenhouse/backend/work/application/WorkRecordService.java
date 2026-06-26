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

	private final WorkRecordRepository workRecordRepository;
	private final WorkTypeService workTypeService;
	private final HouseRepository houseRepository;
	private final PhysicalBedRepository physicalBedRepository;
	private final BedZoneRepository bedZoneRepository;
	private final OrchidGroupRepository orchidGroupRepository;

	public WorkRecordService(
		WorkRecordRepository workRecordRepository,
		WorkTypeService workTypeService,
		HouseRepository houseRepository,
		PhysicalBedRepository physicalBedRepository,
		BedZoneRepository bedZoneRepository,
		OrchidGroupRepository orchidGroupRepository
	) {
		this.workRecordRepository = workRecordRepository;
		this.workTypeService = workTypeService;
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
			.map(this::toResponse)
			.toList();
	}

	public WorkRecordResponse create(WorkRecordCreateRequest request) {
		var workType = workTypeService.getActiveForCreate(request.workTypeId());
		validateTarget(request.targetType(), request.targetId());
		var workRecord = new WorkRecord(
			workType,
			request.workDate(),
			normalizeRequired(request.targetType()),
			request.targetId(),
			normalize(request.materialName()),
			normalize(request.dilutionRatio()),
			normalize(request.quantity()),
			normalize(request.worker()),
			normalize(request.memo())
		);
		return toResponse(workRecordRepository.save(workRecord));
	}

	private WorkRecordResponse toResponse(WorkRecord workRecord) {
		return WorkRecordResponse.from(
			workRecord,
			workTypeService.resolveTemplate(workRecord.getWorkTypeRef(), workRecord.getWorkType())
		);
	}

	private void validateTarget(String targetType, Long targetId) {
		String normalizedTargetType = normalizeRequired(targetType);
		if ("FARM".equals(normalizedTargetType)) {
			return;
		}
		if (targetId == null) {
			throw new IllegalArgumentException("Work target id is required.");
		}
		boolean exists = switch (normalizedTargetType) {
			case "HOUSE" -> houseRepository.existsById(targetId);
			case "PHYSICAL_BED" -> physicalBedRepository.existsById(targetId);
			case "BED_ZONE" -> bedZoneRepository.existsById(targetId);
			case "ORCHID_GROUP" -> orchidGroupRepository.existsById(targetId);
			default -> throw new IllegalArgumentException("Unsupported work target.");
		};
		if (!exists) {
			throw new NotFoundException("Work target not found.");
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
			throw new IllegalArgumentException("Required text value is empty.");
		}
		return normalized;
	}
}
