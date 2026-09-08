package com.greenhouse.backend.farm.application.variety;

import com.greenhouse.backend.audit.domain.AuditAction;
import com.greenhouse.backend.common.api.PageRequests;
import com.greenhouse.backend.common.api.PageResponse;
import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationDetails;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationEngine;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationSources;
import com.greenhouse.backend.farm.application.orchid.mutation.UpdateOrchidGroupMutationCommand;
import com.greenhouse.backend.farm.domain.variety.Variety;
import com.greenhouse.backend.farm.dto.variety.VarietyConnectedOrchidGroupResponse;
import com.greenhouse.backend.farm.dto.variety.VarietyCreateRequest;
import com.greenhouse.backend.farm.dto.variety.VarietyGeneraResponse;
import com.greenhouse.backend.farm.dto.variety.VarietyNameResponse;
import com.greenhouse.backend.farm.dto.variety.VarietyResponse;
import com.greenhouse.backend.farm.dto.variety.VarietyUpdateRequest;
import com.greenhouse.backend.farm.repository.inbound.InboundRecordRepository;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.variety.VarietyRepository;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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

	private final VarietyAuditSupport auditSupport;

	private final OrchidGroupMutationEngine mutationEngine;

	private final Clock clock;

	@Transactional(readOnly = true)
	public PageResponse<VarietyResponse> getVarieties(String keyword, String genus, Boolean saleEnabled, Boolean active,
			int page, int size) {
		PageRequests.validate(page, size);
		var result = varietyRepository.search(normalize(keyword) == null ? "" : normalize(keyword),
				normalize(genus) == null ? "" : normalize(genus), saleEnabled, active, PageRequest.of(page, size,
						Sort.by(Sort.Order.desc("active"), Sort.Order.asc("genus"), Sort.Order.asc("name"))));
		return PageResponse.from(responseAssembler.assemble(result));
	}

	@Transactional(readOnly = true)
	public VarietyResponse getVariety(Long varietyId) {
		return responseAssembler.assemble(findVariety(varietyId));
	}

	@Transactional(readOnly = true)
	public VarietyGeneraResponse getGenera() {
		var varieties = varietyRepository.findActiveNames()
			.stream()
			.map(variety -> new VarietyNameResponse(variety.id(), variety.genus(), variety.name()))
			.toList();
		return new VarietyGeneraResponse(varietyRepository.findDistinctGenera(), varieties);
	}

	public VarietyResponse create(VarietyCreateRequest request) {
		String genus = normalizeRequired(request.genus());
		String name = normalizeRequired(request.name());
		validateUniqueVariety(genus, name, null);
		var variety = new Variety(nextCode(), genus, name, normalize(request.alias()),
				normalize(request.defaultPotSize()), normalizeColor(request.color()),
				request.saleEnabled() == null || request.saleEnabled(), true, normalize(request.description()),
				normalize(request.memo()));
		Variety saved = varietyRepository.save(variety);
		auditSupport.record(AuditAction.CREATED, saved, null, auditSupport.snapshot(saved));
		return responseAssembler.assemble(saved);
	}

	public Variety resolveInboundVariety(Long varietyId, InboundVarietyInput newVariety) {
		if (varietyId != null) {
			return varietyRepository.findById(varietyId).orElseThrow(() -> new NotFoundException("품종을 찾을 수 없습니다."));
		}
		if (newVariety == null) {
			throw new IllegalArgumentException("품종을 선택하거나 새 품종을 입력해야 합니다.");
		}
		String genus = normalizeRequired(newVariety.genus());
		String name = normalizeRequired(newVariety.name());
		return varietyRepository.findByGenusAndName(genus, name)
			.orElseGet(() -> varietyRepository.save(new Variety(nextCode(), genus, name, null,
					normalize(newVariety.defaultPotSize()), true, true, null, normalize(newVariety.memo()))));
	}

	public VarietyResponse update(Long varietyId, VarietyUpdateRequest request) {
		var variety = findVariety(varietyId);
		Map<String, Object> before = auditSupport.snapshot(variety);
		String genus = normalizeRequired(request.genus());
		String name = normalizeRequired(request.name());
		validateUniqueVariety(genus, name, varietyId);
		variety.update(genus, name, normalize(request.alias()), normalize(request.defaultPotSize()),
				normalizeColor(request.color()), request.saleEnabled() == null || request.saleEnabled(),
				normalize(request.description()), normalize(request.memo()));
		var connectedGroups = orchidGroupRepository.findByVarietyIdOrderByLocation(varietyId);
		UUID correlationId = UUID.randomUUID();
		var changedGroups = connectedGroups.stream()
			.filter(group -> !java.util.Objects.equals(group.getGenus(), variety.getGenus())
					|| !java.util.Objects.equals(group.getVarietyName(), variety.getName()))
			.toList();
		changedGroups
			.forEach(
					group -> mutationEngine
						.updateDetails(new UpdateOrchidGroupMutationCommand(
								OrchidGroupMutationSources.farmBatch("VARIETY", varietyId.toString(),
										"PROPAGATE:" + correlationId + ":" + group.getId(), correlationId),
								group.getId(),
								new OrchidGroupMutationDetails(varietyId, group.getQuantity(), group.getPotSize(),
										group.getAgeYear(), group.getStatus(), group.getPlacementType(),
										group.getTrayCount(), group.getSplitPlacementAllowed(),
										group.getStartPosition(), group.getEndPosition(), group.getMemo()),
								TimeConfig.farmToday(clock), "품종 정보 변경 전파")));

		auditSupport.record(AuditAction.UPDATED, variety, before, auditSupport.snapshot(variety));
		return responseAssembler.assemble(variety);
	}

	public VarietyResponse deactivate(Long varietyId) {
		var variety = findVariety(varietyId);
		Map<String, Object> before = auditSupport.snapshot(variety);
		variety.deactivate();
		auditSupport.record(AuditAction.DEACTIVATED, variety, before, auditSupport.snapshot(variety));
		return responseAssembler.assemble(variety);
	}

	public void delete(Long varietyId) {
		var variety = findVariety(varietyId);
		Map<String, Object> before = auditSupport.snapshot(variety);
		if (orchidGroupRepository.existsByVarietyId(varietyId)
				|| inboundRecordRepository.existsByVarietyId(varietyId)) {
			throw new IllegalArgumentException("연결된 난 묶음 또는 입고 기록이 있는 품종은 삭제할 수 없습니다.");
		}
		varietyRepository.delete(variety);
		auditSupport.record(AuditAction.DELETED, variety, before, null);
	}

	@Transactional(readOnly = true)
	public List<VarietyConnectedOrchidGroupResponse> getOrchidGroups(Long varietyId) {
		return responseAssembler.connectedOrchidGroups(findVariety(varietyId));
	}

	public Variety findVariety(Long varietyId) {
		return varietyRepository.findById(varietyId).orElseThrow(() -> new NotFoundException("품종을 찾을 수 없습니다."));
	}

	private String nextCode() {
		return "VAR-%04d".formatted(varietyRepository.nextCodeValue());
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

	private String normalizeColor(String value) {
		String color = normalize(value);
		return color == null ? null : color.toUpperCase();
	}

	private void validateUniqueVariety(String genus, String name, Long currentId) {
		boolean duplicated = currentId == null ? varietyRepository.existsByGenusAndName(genus, name)
				: varietyRepository.existsByGenusAndNameAndIdNot(genus, name, currentId);
		if (duplicated) {
			throw new IllegalArgumentException("같은 속과 품종명을 가진 품종이 이미 있습니다.");
		}
	}

}
