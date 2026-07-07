package com.greenhouse.backend.partner.repository;

import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.partner.domain.PartnerType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessPartnerRepository extends JpaRepository<BusinessPartner, Long> {
	List<BusinessPartner> findAllByActiveTrueOrderByNameAsc();

	List<BusinessPartner> findAllByPartnerTypeAndActiveTrueOrderByNameAsc(PartnerType partnerType);

	List<BusinessPartner> findByNameContainingIgnoreCaseAndActiveTrueOrderByNameAsc(String keyword);

	List<BusinessPartner> findByNameContainingIgnoreCaseAndPartnerTypeAndActiveTrueOrderByNameAsc(String keyword,
			PartnerType partnerType);
}
