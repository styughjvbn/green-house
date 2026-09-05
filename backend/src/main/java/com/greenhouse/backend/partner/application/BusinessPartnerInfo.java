package com.greenhouse.backend.partner.application;

import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.partner.domain.PartnerType;

/** Current master data for application callers; contains no managed entity or contact details. */
public record BusinessPartnerInfo(Long id, String name, PartnerType partnerType, boolean active) {

	static BusinessPartnerInfo from(BusinessPartner partner) {
		return new BusinessPartnerInfo(
				partner.getId(), partner.getName(), partner.getPartnerType(), partner.isActive());
	}
}
