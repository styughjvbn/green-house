package com.greenhouse.backend.farm.repository.orchid.mutation;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupLedgerCoverage;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupLedgerCoverageStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrchidGroupLedgerCoverageRepository extends JpaRepository<OrchidGroupLedgerCoverage, Long> {

	Optional<OrchidGroupLedgerCoverage> findByCutoverKey(UUID cutoverKey);

	Optional<OrchidGroupLedgerCoverage> findFirstByStatus(OrchidGroupLedgerCoverageStatus status);

	Optional<OrchidGroupLedgerCoverage> findFirstByStatusOrderByIdDesc(
			OrchidGroupLedgerCoverageStatus status);

	long countByStatus(OrchidGroupLedgerCoverageStatus status);
}
