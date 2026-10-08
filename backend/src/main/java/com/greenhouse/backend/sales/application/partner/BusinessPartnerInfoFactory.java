package com.greenhouse.backend.sales.application.partner;

import com.greenhouse.backend.sales.api.partner.BusinessPartnerInfo;
import com.greenhouse.backend.sales.domain.partner.BusinessPartner;

final class BusinessPartnerInfoFactory {

  private BusinessPartnerInfoFactory() {}

  static BusinessPartnerInfo from(BusinessPartner partner) {
    return new BusinessPartnerInfo(
        partner.getId(),
        partner.getName(),
        partner.getPartnerType(),
        partner.isActive(),
        partner.getOwnerName(),
        partner.getPhone(),
        partner.getAddress(),
        partner.getMemo());
  }
}
