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
				allocation.orchidGroupId, sum(allocation.allocatedQuantity))
			from SalesSlipItemAllocation allocation
			join allocation.salesSlipItem item
			join item.salesSlip slip
			where allocation.orchidGroupId in :orchidGroupIds
			  and slip.salesStatus = :draftStatus
			group by allocation.orchidGroupId
			order by allocation.orchidGroupId
			""")
	List<SalesReservationReconciliationRow> sumDraftReservationsByOrchidGroupIdIn(
			@Param("orchidGroupIds") Collection<Long> orchidGroupIds,
			@Param("draftStatus") String draftStatus);

	@Query("""
			select allocation from SalesSlipItemAllocation allocation
			join fetch allocation.salesSlipItem item
			join fetch item.salesSlip slip
			left join fetch allocation.snapshots
			where slip.id in :salesSlipIds
			order by item.id asc, allocation.id asc
			""")
	List<SalesSlipItemAllocation> findAllWithSnapshotsBySalesSlipIdIn(
			@Param("salesSlipIds") Collection<Long> salesSlipIds);
}
