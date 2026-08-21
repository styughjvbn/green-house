package com.greenhouse.backend.migration.repository.orchid;

import com.greenhouse.backend.migration.domain.orchid.OrchidGroupHistoryMigrationRun;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrchidGroupHistoryMigrationRunRepository
		extends JpaRepository<OrchidGroupHistoryMigrationRun, Long> {

	Optional<OrchidGroupHistoryMigrationRun> findByRunKey(UUID runKey);

	Optional<OrchidGroupHistoryMigrationRun> findFirstByOrderByIdDesc();

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select run from OrchidGroupHistoryMigrationRun run where run.runKey = :runKey")
	Optional<OrchidGroupHistoryMigrationRun> findForUpdateByRunKey(@Param("runKey") UUID runKey);
}
