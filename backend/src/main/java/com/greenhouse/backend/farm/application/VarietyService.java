package com.greenhouse.backend.farm.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.domain.OrchidGroup;
import com.greenhouse.backend.farm.domain.Variety;
import com.greenhouse.backend.farm.dto.VarietyConnectedOrchidGroupResponse;
import com.greenhouse.backend.farm.dto.VarietyCreateRequest;
import com.greenhouse.backend.farm.dto.VarietyPageResponse;
import com.greenhouse.backend.farm.dto.VarietyResponse;
import com.greenhouse.backend.farm.dto.VarietyUpdateRequest;
import com.greenhouse.backend.farm.repository.InboundRecordRepository;
import com.greenhouse.backend.farm.repository.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.VarietyRepository;
import com.greenhouse.backend.work.application.WorkRecordMetricsReader;

import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class VarietyService {
	private final VarietyRepository varietyRepository;
	private final OrchidGroupRepository orchidGroupRepository;
	private final InboundRecordRepository inboundRecordRepository;
	private final WorkRecordMetricsReader workRecordMetricsReader;

	@Transactional(readOnly = true)
	public VarietyPageResponse getVarieties(
			String keyword,
			String genus,
			Boolean saleEnabled,
			Boolean active,
			int page,
			int size) {
		validatePageRequest(page, size);
		return VarietyPageResponse.from(varietyRepository.search(
				normalize(keyword) == null ? "" : normalize(keyword),
				normalize(genus) == null ? "" : normalize(genus),
				saleEnabled,
				active,
				PageRequest.of(page, size, Sort.by(
						Sort.Order.desc("active"),
						Sort.Order.asc("genus"),
						Sort.Order.asc("name"))))
				.map(this::toResponse));
	}

	@Transactional(readOnly = true)
	public VarietyResponse getVariety(Long varietyId) {
		return toResponse(findVariety(varietyId));
	}

	@Transactional(readOnly = true)
	public List<String> getGenera() {
		return varietyRepository.findDistinctGenera();
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
				normalize(request.memo()));
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
				normalize(request.memo()));
		orchidGroupRepository.findByVarietyIdOrderByLocation(varietyId)
				.forEach(group -> group.assignVariety(variety));
		return toResponse(variety);
	}

	public VarietyResponse deactivate(Long varietyId) {
		var variety = findVariety(varietyId);
		variety.deactivate();
		return toResponse(variety);
	}

	public void delete(Long varietyId) {
		var variety = findVariety(varietyId);
		if (orchidGroupRepository.existsByVarietyId(varietyId)
				|| inboundRecordRepository.existsByVarietyId(varietyId)) {
			throw new IllegalArgumentException("연결된 난 묶음 또는 입고 기록이 있는 품종은 삭제할 수 없습니다.");
		}
		varietyRepository.delete(variety);
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
						latestWorkDates.get(group.getId())))
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
				recentWorkDate);
	}

	private Map<Long, LocalDate> latestWorkDates(List<OrchidGroup> orchidGroups) {
		return workRecordMetricsReader.getLatestWorkDates(
				"ORCHID_GROUP",
				orchidGroups.stream().map(OrchidGroup::getId).toList());
	}

	private String formatLocation(OrchidGroup orchidGroup) {
		var bedZone = orchidGroup.getBedZone();
		var physicalBed = bedZone.getPhysicalBed();
		var house = physicalBed.getHouse();
		return "%d동-%d다이 %s".formatted(house.getNumber(), physicalBed.getNumber(), bedZone.getName());
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

	private void validatePageRequest(int page, int size) {
		if (page < 0) {
			throw new IllegalArgumentException("페이지 번호는 0 이상이어야 합니다.");
		}
		if (size < 1 || size > 100) {
			throw new IllegalArgumentException("페이지 크기는 1~100이어야 합니다.");
		}
	}
}
