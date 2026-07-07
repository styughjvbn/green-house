package com.greenhouse.backend.partner.application;

import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.partner.domain.PartnerType;
import com.greenhouse.backend.partner.dto.BusinessPartnerCreateRequest;
import com.greenhouse.backend.partner.dto.BusinessPartnerResponse;
import com.greenhouse.backend.partner.repository.BusinessPartnerRepository;

import lombok.RequiredArgsConstructor;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class BusinessPartnerService {
	private final BusinessPartnerRepository repository;

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

	public BusinessPartnerResponse create(BusinessPartnerCreateRequest request) {
		var partner = new BusinessPartner(
				request.name().trim(), request.partnerType(), normalize(request.ownerName()),
				normalize(request.phone()), normalize(request.address()), normalize(request.memo()));
		return BusinessPartnerResponse.from(repository.save(partner));
	}

	private String normalize(String value) {
		if (value == null || value.isBlank())
			return null;
		return value.trim();
	}
}
