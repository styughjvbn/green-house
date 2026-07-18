package com.greenhouse.backend.sales.repository;

import com.greenhouse.backend.sales.domain.SalesSlipItemAllocation;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesSlipItemAllocationRepository extends JpaRepository<SalesSlipItemAllocation, Long> {

	long countByOrchidGroupIdIn(Collection<Long> orchidGroupIds);
}
