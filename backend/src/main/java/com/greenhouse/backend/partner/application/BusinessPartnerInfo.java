package com.greenhouse.backend.partner.application;

import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.partner.domain.PartnerType;
import io.swagger.v3.oas.annotations.media.Schema;

/** Current master data for application callers; contains no managed entity. */
@Schema(name = "BusinessPartnerResponse")
public record BusinessPartnerInfo(Long id, String name, PartnerType partnerType, boolean active,
		String ownerName, String phone, String address, String memo) {

	static BusinessPartnerInfo from(BusinessPartner partner) {
		return new BusinessPartnerInfo(
				partner.getId(), partner.getName(), partner.getPartnerType(), partner.isActive(),
				partner.getOwnerName(), partner.getPhone(), partner.getAddress(), partner.getMemo());
	}
}
