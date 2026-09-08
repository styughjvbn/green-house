package com.greenhouse.backend.partner.dto;

import com.greenhouse.backend.partner.domain.BusinessPartner;

public record BusinessPartnerOptionResponse(Long id, String name, boolean active) {
	public static BusinessPartnerOptionResponse from(BusinessPartner partner) {
		return new BusinessPartnerOptionResponse(partner.getId(), partner.getName(), partner.isActive());
	}
}
