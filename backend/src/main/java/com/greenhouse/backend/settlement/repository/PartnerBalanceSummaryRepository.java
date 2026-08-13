package com.greenhouse.backend.settlement.repository;

import com.greenhouse.backend.settlement.domain.PartnerBalanceSummary;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PartnerBalanceSummaryRepository extends JpaRepository<PartnerBalanceSummary, Long> {
	@EntityGraph(attributePaths = "partner")
	Optional<PartnerBalanceSummary> findByPartnerId(Long partnerId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@EntityGraph(attributePaths = "partner")
	@Query("select summary from PartnerBalanceSummary summary where summary.partner.id = :partnerId")
	Optional<PartnerBalanceSummary> findForUpdateByPartnerId(@Param("partnerId") Long partnerId);
}
