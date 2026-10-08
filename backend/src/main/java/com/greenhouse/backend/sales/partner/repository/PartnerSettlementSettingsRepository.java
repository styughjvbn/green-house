package com.greenhouse.backend.sales.partner.repository;

import com.greenhouse.backend.sales.partner.domain.PartnerSettlementSettings;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartnerSettlementSettingsRepository
    extends JpaRepository<PartnerSettlementSettings, Long> {

  Optional<PartnerSettlementSettings> findByPartnerId(Long partnerId);

  List<PartnerSettlementSettings> findByPartnerIdIn(Collection<Long> partnerIds);
}
