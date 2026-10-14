package com.greenhouse.backend.sales.api.partner;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface BusinessPartnerQueryApi {
  List<Long> findMatchingIds(PartnerTextMatch match, String value);

  Map<PartnerTextSearch, List<Long>> findMatchingIds(Collection<PartnerTextSearch> searches);

  Map<Long, Identity> getIdentities(Collection<Long> partnerIds);

  BusinessPartnerInfo getInfo(Long partnerId);

  BusinessPartnerInfo getActiveInfo(Long partnerId);

  Map<Long, BusinessPartnerInfo> getAllInfo(Collection<Long> partnerIds);

  record Identity(Long id, String name, PartnerType partnerType) {}
}
