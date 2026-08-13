package com.greenhouse.backend.sales.repository;

import com.greenhouse.backend.sales.domain.SalesSlipItemAllocation;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesSlipItemAllocationRepository extends JpaRepository<SalesSlipItemAllocation, Long> {

	long countByOrchidGroupIdIn(Collection<Long> orchidGroupIds);

	@Query("""
			select allocation from SalesSlipItemAllocation allocation
			join fetch allocation.salesSlipItem item
			join fetch item.salesSlip slip
			join fetch allocation.orchidGroup orchidGroup
			join fetch orchidGroup.bedZone zone
			join fetch zone.physicalBed bed
			join fetch bed.house
			left join fetch allocation.snapshots
			where slip.id in :salesSlipIds
			order by item.id asc, allocation.id asc
			""")
	List<SalesSlipItemAllocation> findAllWithLocationBySalesSlipIdIn(
			@Param("salesSlipIds") Collection<Long> salesSlipIds);
}
