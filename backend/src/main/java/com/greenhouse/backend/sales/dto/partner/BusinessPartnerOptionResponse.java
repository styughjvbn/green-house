package com.greenhouse.backend.sales.dto.partner;

import com.greenhouse.backend.sales.domain.partner.BusinessPartner;

public record BusinessPartnerOptionResponse(Long id, String name, boolean active) {
  public static BusinessPartnerOptionResponse from(BusinessPartner partner) {
    return new BusinessPartnerOptionResponse(
        partner.getId(), partner.getName(), partner.isActive());
  }
}
