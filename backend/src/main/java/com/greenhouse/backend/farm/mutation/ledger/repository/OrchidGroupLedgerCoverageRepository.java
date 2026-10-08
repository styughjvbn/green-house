package com.greenhouse.backend.farm.mutation.ledger.repository;

import com.greenhouse.backend.farm.mutation.ledger.domain.OrchidGroupLedgerCoverage;
import com.greenhouse.backend.farm.mutation.ledger.domain.OrchidGroupLedgerCoverageStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrchidGroupLedgerCoverageRepository
    extends JpaRepository<OrchidGroupLedgerCoverage, Long> {

  Optional<OrchidGroupLedgerCoverage> findByCutoverKey(UUID cutoverKey);

  Optional<OrchidGroupLedgerCoverage> findFirstByStatus(OrchidGroupLedgerCoverageStatus status);

  Optional<OrchidGroupLedgerCoverage> findFirstByStatusOrderByIdDesc(
      OrchidGroupLedgerCoverageStatus status);

  long countByStatus(OrchidGroupLedgerCoverageStatus status);
}
