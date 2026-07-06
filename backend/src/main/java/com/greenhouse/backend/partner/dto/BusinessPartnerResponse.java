package com.greenhouse.backend.partner.dto;

import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.partner.domain.PartnerType;

public record BusinessPartnerResponse(
	Long id,
	String name,
	PartnerType partnerType,
	String ownerName,
	String phone,
	String address,
	String memo,
	boolean active
) {
	public static BusinessPartnerResponse from(BusinessPartner partner) {
		return new BusinessPartnerResponse(
			partner.getId(), partner.getName(), partner.getPartnerType(), partner.getOwnerName(),
			partner.getPhone(), partner.getAddress(), partner.getMemo(), partner.isActive());
	}
}
