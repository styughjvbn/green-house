package com.greenhouse.backend.farm.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.domain.OrchidGroup;
import com.greenhouse.backend.farm.domain.Variety;
import com.greenhouse.backend.farm.dto.VarietyConnectedOrchidGroupResponse;
import com.greenhouse.backend.farm.dto.VarietyCreateRequest;
import com.greenhouse.backend.farm.dto.VarietyResponse;
import com.greenhouse.backend.farm.dto.VarietyUpdateRequest;
import com.greenhouse.backend.farm.repository.InboundRecordRepository;
import com.greenhouse.backend.farm.repository.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.VarietyRepository;
import com.greenhouse.backend.work.application.WorkRecordMetricsReader;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class VarietyService {
	private final VarietyRepository varietyRepository;
	private final OrchidGroupRepository orchidGroupRepository;
	private final InboundRecordRepository inboundRecordRepository;
	private final WorkRecordMetricsReader workRecordMetricsReader;

	public VarietyService(
		VarietyRepository varietyRepository,
		OrchidGroupRepository orchidGroupRepository,
		InboundRecordRepository inboundRecordRepository,
		WorkRecordMetricsReader workRecordMetricsReader
	) {
		this.varietyRepository = varietyRepository;
		this.orchidGroupRepository = orchidGroupRepository;
		this.inboundRecordRepository = inboundRecordRepository;
		this.workRecordMetricsReader = workRecordMetricsReader;
	}

	@Transactional(readOnly = true)
	public List<VarietyResponse> getVarieties(
		String keyword,
		String genus,
		Boolean saleEnabled,
		Boolean active
	) {
		return varietyRepository.search(
				normalize(keyword) == null ? "" : normalize(keyword),
				normalize(genus) == null ? "" : normalize(genus),
				saleEnabled,
				active
			).stream()
			.map(this::toResponse)
			.toList();
	}

	@Transactional(readOnly = true)
	public VarietyResponse getVariety(Long varietyId) {
		return toResponse(findVariety(varietyId));
	}

	public VarietyResponse create(VarietyCreateRequest request) {
		var variety = new Variety(
			nextCode(),
			normalizeRequired(request.genus()),
			normalizeRequired(request.name()),
			normalize(request.alias()),
			normalize(request.defaultPotSize()),
			request.saleEnabled() == null || request.saleEnabled(),
			true,
			normalize(request.description()),
			normalize(request.memo())
		);
		return toResponse(varietyRepository.save(variety));
	}

	public VarietyResponse update(Long varietyId, VarietyUpdateRequest request) {
		var variety = findVariety(varietyId);
		variety.update(
			normalizeRequired(request.genus()),
			normalizeRequired(request.name()),
			normalize(request.alias()),
			normalize(request.defaultPotSize()),
			request.saleEnabled() == null || request.saleEnabled(),
			normalize(request.description()),
			normalize(request.memo())
		);
		return toResponse(variety);
	}

	public VarietyResponse deactivate(Long varietyId) {
		var variety = findVariety(varietyId);
		variety.deactivate();
		return toResponse(variety);
	}

	@Transactional(readOnly = true)
	public List<VarietyConnectedOrchidGroupResponse> getOrchidGroups(Long varietyId) {
		var variety = findVariety(varietyId);
		var orchidGroups = orchidGroupRepository.findByVarietyIdOrderByLocation(variety.getId());
		var latestWorkDates = latestWorkDates(orchidGroups);
		return orchidGroups.stream()
			.map(group -> new VarietyConnectedOrchidGroupResponse(
				group.getId(),
				formatLocation(group),
				group.getQuantity(),
				group.getStatus(),
				latestWorkDates.get(group.getId())
			))
			.toList();
	}

	public Variety findVariety(Long varietyId) {
		return varietyRepository.findById(varietyId)
			.orElseThrow(() -> new NotFoundException("품종을 찾을 수 없습니다."));
	}

	private VarietyResponse toResponse(Variety variety) {
		var orchidGroups = orchidGroupRepository.findByVarietyIdOrderByLocation(variety.getId());
		var latestWorkDates = latestWorkDates(orchidGroups);
		long totalQuantity = orchidGroups.stream().mapToLong(OrchidGroup::getQuantity).sum();
		long saleableQuantity = orchidGroups.stream()
			.filter(group -> !List.of("주의", "이상", "병해충").contains(group.getStatus()))
			.mapToLong(OrchidGroup::getQuantity)
			.sum();
		LocalDate recentWorkDate = latestWorkDates.values().stream()
			.filter(java.util.Objects::nonNull)
			.max(Comparator.naturalOrder())
			.orElse(null);
		return VarietyResponse.from(
			variety,
			orchidGroups.size(),
			totalQuantity,
			saleableQuantity,
			inboundRecordRepository.findLatestInboundDateByVarietyId(variety.getId()),
			recentWorkDate
		);
	}

	private Map<Long, LocalDate> latestWorkDates(List<OrchidGroup> orchidGroups) {
		return workRecordMetricsReader.getLatestWorkDates(
			"ORCHID_GROUP",
			orchidGroups.stream().map(OrchidGroup::getId).toList()
		);
	}

	private String formatLocation(OrchidGroup orchidGroup) {
		var bedZone = orchidGroup.getBedZone();
		var physicalBed = bedZone.getPhysicalBed();
		var house = physicalBed.getHouse();
		return "%d동-%d배드 %s".formatted(house.getNumber(), physicalBed.getNumber(), bedZone.getName());
	}

	private String nextCode() {
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
