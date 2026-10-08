package com.greenhouse.backend.sales.partner.web.dto;

import com.greenhouse.backend.sales.partner.domain.BusinessPartner;

public record BusinessPartnerOptionResponse(Long id, String name, boolean active) {
  public static BusinessPartnerOptionResponse from(BusinessPartner partner) {
    return new BusinessPartnerOptionResponse(
        partner.getId(), partner.getName(), partner.isActive());
  }
}
