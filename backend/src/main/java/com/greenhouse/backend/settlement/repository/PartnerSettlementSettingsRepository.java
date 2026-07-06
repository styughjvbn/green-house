package com.greenhouse.backend.settlement.repository;

import com.greenhouse.backend.settlement.domain.PartnerSettlementSettings;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartnerSettlementSettingsRepository extends JpaRepository<PartnerSettlementSettings, Long> {
	@EntityGraph(attributePaths = "partner")
	Optional<PartnerSettlementSettings> findByPartnerId(Long partnerId);
}
