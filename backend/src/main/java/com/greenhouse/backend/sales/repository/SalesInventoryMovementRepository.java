package com.greenhouse.backend.sales.repository;

import com.greenhouse.backend.sales.domain.SalesInventoryMovement;
import com.greenhouse.backend.sales.domain.SalesInventoryMovementType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SalesInventoryMovementRepository extends JpaRepository<SalesInventoryMovement, Long> {

	long countByOrchidGroupIdIn(Collection<Long> orchidGroupIds);

	List<SalesInventoryMovement> findBySalesSlipIdAndChangeType(Long salesSlipId,
			SalesInventoryMovementType changeType);

	@Query("""
			select movement.id
			from SalesInventoryMovement movement
			where (movement.mutationId is null and movement.correlationId is not null)
			   or (movement.mutationId is not null and movement.correlationId is null)
			order by movement.id
			""")
	List<Long> findIdsWithIncompleteMutationLink();

}
