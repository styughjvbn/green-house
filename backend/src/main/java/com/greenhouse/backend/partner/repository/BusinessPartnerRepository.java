package com.greenhouse.backend.partner.repository;

import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.partner.domain.PartnerType;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BusinessPartnerRepository
		extends JpaRepository<BusinessPartner, Long>, BusinessPartnerRepositoryCustom {
	List<BusinessPartner> findAllByActiveTrueOrderByNameAsc();

	List<BusinessPartner> findAllByPartnerTypeAndActiveTrueOrderByNameAsc(PartnerType partnerType);

	List<BusinessPartner> findByNameContainingIgnoreCaseAndActiveTrueOrderByNameAsc(String keyword);

	List<BusinessPartner> findByNameContainingIgnoreCaseAndPartnerTypeAndActiveTrueOrderByNameAsc(String keyword,
			PartnerType partnerType);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select partner from BusinessPartner partner where partner.id in :partnerIds order by partner.id")
	List<BusinessPartner> findAllForUpdateByIdIn(@Param("partnerIds") Collection<Long> partnerIds);
}
