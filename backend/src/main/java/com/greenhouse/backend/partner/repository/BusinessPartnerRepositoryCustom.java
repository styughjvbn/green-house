package com.greenhouse.backend.partner.repository;

import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.partner.domain.PartnerType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BusinessPartnerRepositoryCustom {

	Page<BusinessPartner> searchPage(
			String keyword,
			PartnerType partnerType,
			Boolean active,
			Pageable pageable);
}
