package com.greenhouse.backend.sales.repository.partner;

import com.greenhouse.backend.sales.domain.partner.BusinessPartner;
import com.greenhouse.backend.sales.domain.partner.PartnerTextMatch;
import com.greenhouse.backend.sales.domain.partner.PartnerTextSearch;
import com.greenhouse.backend.sales.domain.partner.PartnerType;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BusinessPartnerRepositoryCustom {

  List<Long> findMatchingIds(PartnerTextMatch match, String value, long afterId, int limit);

  List<PartnerSearchMatchRow> findMatchingIds(
      List<PartnerTextSearch> searches, long afterId, int limit);

  List<BusinessPartner> findActiveByName(String keyword, PartnerType partnerType, int limit);

  Page<BusinessPartner> searchPage(
      String keyword,
      PartnerType partnerType,
      Boolean active,
      Boolean auctionHouse,
      Pageable pageable);
}
