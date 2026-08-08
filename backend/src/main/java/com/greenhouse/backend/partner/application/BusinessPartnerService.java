package com.greenhouse.backend.partner.application;

import com.greenhouse.backend.common.api.PageResponse;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.partner.domain.PartnerType;
import com.greenhouse.backend.partner.dto.BusinessPartnerCreateRequest;
import com.greenhouse.backend.partner.dto.BusinessPartnerResponse;
import com.greenhouse.backend.partner.dto.BusinessPartnerUpdateRequest;
import com.greenhouse.backend.partner.repository.BusinessPartnerRepository;

import lombok.RequiredArgsConstructor;

import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class BusinessPartnerService {
	private final BusinessPartnerRepository repository;
	private final BusinessPartnerAuditSupport auditSupport;

	@Transactional(readOnly = true)
	public List<BusinessPartnerResponse> getPartners(String keyword, PartnerType partnerType) {
		String normalized = keyword == null ? "" : keyword.trim();
		List<BusinessPartner> partners;
		if (normalized.isEmpty()) {
			partners = partnerType == null
					? repository.findAllByActiveTrueOrderByNameAsc()
					: repository.findAllByPartnerTypeAndActiveTrueOrderByNameAsc(partnerType);
		} else {
			partners = partnerType == null
					? repository.findByNameContainingIgnoreCaseAndActiveTrueOrderByNameAsc(normalized)
					: repository.findByNameContainingIgnoreCaseAndPartnerTypeAndActiveTrueOrderByNameAsc(normalized,
							partnerType);
		}
		return partners.stream().map(BusinessPartnerResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public PageResponse<BusinessPartnerResponse> getPartnerPage(
			String keyword,
			PartnerType partnerType,
			Boolean active,
			int page,
			int size) {
		PageRequest pageable = PageRequest.of(
				Math.max(page, 0),
				Math.min(Math.max(size, 1), 100));
		return PageResponse.from(repository
				.searchPage(keyword, partnerType, active, pageable)
				.map(BusinessPartnerResponse::from));
	}

	public BusinessPartnerResponse create(BusinessPartnerCreateRequest request) {
		var partner = new BusinessPartner(
				request.name().trim(), request.partnerType(), normalize(request.ownerName()),
				normalize(request.phone()), normalize(request.address()), normalize(request.memo()));
		var saved = repository.save(partner);
		auditSupport.recordCreated(saved);
		return BusinessPartnerResponse.from(saved);
	}

	public BusinessPartnerResponse update(Long partnerId, BusinessPartnerUpdateRequest request) {
		var partner = repository.findById(partnerId)
				.orElseThrow(() -> new NotFoundException("거래처를 찾을 수 없습니다."));
		var before = auditSupport.snapshot(partner);
		partner.update(
				request.name().trim(),
				request.partnerType(),
				normalize(request.ownerName()),
				normalize(request.phone()),
				normalize(request.address()),
				normalize(request.memo()));
		auditSupport.recordUpdated(partner, before);
		return BusinessPartnerResponse.from(partner);
	}

	private String normalize(String value) {
		if (value == null || value.isBlank())
			return null;
		return value.trim();
	}
}
