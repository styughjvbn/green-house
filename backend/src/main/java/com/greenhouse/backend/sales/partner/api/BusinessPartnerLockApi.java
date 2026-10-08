package com.greenhouse.backend.sales.partner.api;

import com.greenhouse.backend.sales.api.partner.BusinessPartnerInfo;
import java.util.Collection;
import java.util.List;

public interface BusinessPartnerLockApi {
  List<BusinessPartnerInfo> lockAll(Collection<Long> partnerIds);
}
