package com.greenhouse.backend.farm.application.variety;

import com.greenhouse.backend.common.api.PageResponse;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.domain.variety.Variety;
import com.greenhouse.backend.farm.dto.variety.VarietyConnectedOrchidGroupResponse;
import com.greenhouse.backend.farm.dto.variety.VarietyCreateRequest;
import com.greenhouse.backend.farm.dto.variety.VarietyResponse;
import com.greenhouse.backend.farm.dto.variety.VarietyUpdateRequest;
import com.greenhouse.backend.farm.repository.inbound.InboundRecordRepository;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.variety.VarietyRepository;
import lombok.RequiredArgsConstructor;

import java.util.List;
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
	private final VarietyResponseAssembler responseAssembler;

	@Transactional(readOnly = true)
	public PageResponse<VarietyResponse> getVarieties(
			String keyword,
			String genus,
			Boolean saleEnabled,
			Boolean active,
			int page,
			int size) {
		validatePageRequest(page, size);
		var result = varietyRepository.search(
				normalize(keyword) == null ? "" : normalize(keyword),
				normalize(genus) == null ? "" : normalize(genus),
				saleEnabled,
				active,
				PageRequest.of(page, size, Sort.by(
						Sort.Order.desc("active"),
						Sort.Order.asc("genus"),
						Sort.Order.asc("name"))));
		return PageResponse.from(responseAssembler.assemble(result));
	}

	@Transactional(readOnly = true)
	public VarietyResponse getVariety(Long varietyId) {
		return responseAssembler.assemble(findVariety(varietyId));
	}

	@Transactional(readOnly = true)
	public List<String> getGenera() {
		return varietyRepository.findDistinctGenera();
	}

	public VarietyResponse create(VarietyCreateRequest request) {
		String genus = normalizeRequired(request.genus());
		String name = normalizeRequired(request.name());
		validateUniqueVariety(genus, name, null);
		var variety = new Variety(
				nextCode(),
				genus,
				name,
				normalize(request.alias()),
				normalize(request.defaultPotSize()),
				request.saleEnabled() == null || request.saleEnabled(),
				true,
				normalize(request.description()),
				normalize(request.memo()));
		return responseAssembler.assemble(varietyRepository.save(variety));
	}

	public VarietyResponse update(Long varietyId, VarietyUpdateRequest request) {
		var variety = findVariety(varietyId);
		String genus = normalizeRequired(request.genus());
		String name = normalizeRequired(request.name());
		validateUniqueVariety(genus, name, varietyId);
		variety.update(
				genus,
				name,
				normalize(request.alias()),
				normalize(request.defaultPotSize()),
				request.saleEnabled() == null || request.saleEnabled(),
				normalize(request.description()),
				normalize(request.memo()));
		orchidGroupRepository.findByVarietyIdOrderByLocation(varietyId)
				.forEach(group -> group.assignVariety(variety));
		return responseAssembler.assemble(variety);
	}

	public VarietyResponse deactivate(Long varietyId) {
		var variety = findVariety(varietyId);
		variety.deactivate();
		return responseAssembler.assemble(variety);
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
		return responseAssembler.connectedOrchidGroups(findVariety(varietyId));
	}

	public Variety findVariety(Long varietyId) {
		return varietyRepository.findById(varietyId)
				.orElseThrow(() -> new NotFoundException("품종을 찾을 수 없습니다."));
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

	private void validateUniqueVariety(String genus, String name, Long currentId) {
		boolean duplicated = currentId == null
				? varietyRepository.existsByGenusAndName(genus, name)
				: varietyRepository.existsByGenusAndNameAndIdNot(genus, name, currentId);
		if (duplicated) {
			throw new IllegalArgumentException("같은 속과 품종명을 가진 품종이 이미 있습니다.");
		}
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
