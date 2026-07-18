package com.greenhouse.backend.sales.repository;

import com.greenhouse.backend.sales.domain.SalesInventoryMovement;
import com.greenhouse.backend.sales.domain.SalesInventoryMovementType;
import java.util.List;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesInventoryMovementRepository extends JpaRepository<SalesInventoryMovement, Long> {
	long countByOrchidGroupIdIn(Collection<Long> orchidGroupIds);
	List<SalesInventoryMovement> findBySalesSlipIdAndChangeType(Long salesSlipId, SalesInventoryMovementType changeType);
}
