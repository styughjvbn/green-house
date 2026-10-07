package com.greenhouse.backend.sales.dto.partner;

import com.greenhouse.backend.sales.application.partner.BusinessPartnerInfo;
import com.greenhouse.backend.sales.domain.partner.BusinessPartner;
import com.greenhouse.backend.sales.domain.partner.PartnerType;

public record BusinessPartnerResponse(
    Long id,
    String name,
    PartnerType partnerType,
    String ownerName,
    String phone,
    String address,
    String memo,
    boolean active) {
  public static BusinessPartnerResponse from(BusinessPartnerInfo partner) {
    return new BusinessPartnerResponse(
        partner.id(),
        partner.name(),
        partner.partnerType(),
        partner.ownerName(),
        partner.phone(),
        partner.address(),
        partner.memo(),
        partner.active());
  }

  public static BusinessPartnerResponse from(BusinessPartner partner) {
    return new BusinessPartnerResponse(
        partner.getId(),
        partner.getName(),
        partner.getPartnerType(),
        partner.getOwnerName(),
        partner.getPhone(),
        partner.getAddress(),
        partner.getMemo(),
        partner.isActive());
  }
}
