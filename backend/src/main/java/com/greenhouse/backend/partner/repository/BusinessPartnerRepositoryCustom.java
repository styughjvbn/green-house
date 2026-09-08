package com.greenhouse.backend.partner.repository;

import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.partner.domain.PartnerTextMatch;
import com.greenhouse.backend.partner.domain.PartnerType;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BusinessPartnerRepositoryCustom {

	List<Long> findMatchingIds(PartnerTextMatch match, String value, long afterId, int limit);

	List<BusinessPartner> findActiveByName(String keyword, PartnerType partnerType, int limit);

	Page<BusinessPartner> searchPage(String keyword, PartnerType partnerType, Boolean active, Boolean auctionHouse,
			Pageable pageable);

}
