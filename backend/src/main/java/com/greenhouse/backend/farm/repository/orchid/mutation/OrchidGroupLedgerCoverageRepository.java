package com.greenhouse.backend.farm.repository.orchid.mutation;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupLedgerCoverage;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupLedgerCoverageStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface OrchidGroupLedgerCoverageRepository extends JpaRepository<OrchidGroupLedgerCoverage, Long> {

	Optional<OrchidGroupLedgerCoverage> findByCutoverKey(UUID cutoverKey);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select coverage from OrchidGroupLedgerCoverage coverage where coverage.cutoverKey = :cutoverKey")
	Optional<OrchidGroupLedgerCoverage> findForUpdateByCutoverKey(@Param("cutoverKey") UUID cutoverKey);

	Optional<OrchidGroupLedgerCoverage> findFirstByStatus(OrchidGroupLedgerCoverageStatus status);

	Optional<OrchidGroupLedgerCoverage> findFirstByStatusOrderByIdDesc(
			OrchidGroupLedgerCoverageStatus status);

	long countByStatus(OrchidGroupLedgerCoverageStatus status);
}
