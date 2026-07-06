package com.greenhouse.backend.settlement.repository;

import com.greenhouse.backend.settlement.domain.PartnerBalanceSummary;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartnerBalanceSummaryRepository extends JpaRepository<PartnerBalanceSummary, Long> {
	@EntityGraph(attributePaths = "partner")
	Optional<PartnerBalanceSummary> findByPartnerId(Long partnerId);
}
