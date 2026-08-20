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
			select new com.greenhouse.backend.sales.repository.SalesReservationReconciliationRow(
				allocation.orchidGroup.id, sum(allocation.allocatedQuantity))
			from SalesSlipItemAllocation allocation
			join allocation.salesSlipItem item
			join item.salesSlip slip
			where allocation.orchidGroup.id in :orchidGroupIds
			  and slip.salesStatus = :draftStatus
			group by allocation.orchidGroup.id
			order by allocation.orchidGroup.id
			""")
	List<SalesReservationReconciliationRow> sumDraftReservationsByOrchidGroupIdIn(
			@Param("orchidGroupIds") Collection<Long> orchidGroupIds,
			@Param("draftStatus") String draftStatus);

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
