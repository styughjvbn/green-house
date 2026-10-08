package com.greenhouse.backend.sales.repository.document;

import com.greenhouse.backend.sales.domain.document.SalesSlip;
import com.greenhouse.backend.sales.domain.document.SalesSlipItem;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesSlipRepository
    extends JpaRepository<SalesSlip, Long>, SalesSlipRepositoryCustom {

  @Query(
      "select s.id as id, s.partnerId as partnerId from SalesSlip s where s.id in :ids and s.salesType = com.greenhouse.backend.sales.domain.document.SalesType.DIRECT")
  List<PaymentOwner> findPaymentOwners(Collection<Long> ids);

  interface PaymentOwner {
    Long getId();

    Long getPartnerId();
  }

  @Query("select slip.partnerId from SalesSlip slip where slip.id = :id")
  Optional<Long> findPartnerId(@Param("id") Long id);

  @Query(
      "select s from SalesSlip s where s.partnerId = :partnerId and s.salesType = com.greenhouse.backend.sales.domain.document.SalesType.DIRECT and s.salesStatus <> '취소' and (:keyword = '' or lower(s.slipNumber) like lower(concat('%', :keyword, '%')))")
  Page<SalesSlip> findPaymentTargets(Long partnerId, String keyword, Pageable pageable);

  boolean existsByAuctionShipmentId(Long auctionShipmentId);

  @Query(
      """
			select slip.auctionShipmentId from SalesSlip slip
			where slip.auctionShipmentId in :shipmentIds
			""")
  List<Long> findUsedAuctionShipmentIds(@Param("shipmentIds") Collection<Long> shipmentIds);

  @EntityGraph(attributePaths = {"items"})
  Optional<SalesSlip> findWithDetailsById(Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select slip from SalesSlip slip where slip.id = :id")
  Optional<SalesSlip> findForUpdateById(@Param("id") Long id);

  @Query(
      """
      select item from SalesSlipItem item
      left join fetch item.allocations allocation
      where item.salesSlip.id = :salesSlipId
      order by item.id, allocation.id
      """)
  List<SalesSlipItem> findItemsWithAllocationsBySalesSlipId(@Param("salesSlipId") Long salesSlipId);

  @Query(
      """
			select coalesce(sum(coalesce(s.remainingAmount, s.totalAmount)), 0)
			from SalesSlip s
			where s.partnerId = :partnerId
			  and (s.salesType is null or s.salesType = com.greenhouse.backend.sales.domain.document.SalesType.DIRECT)
			  and s.salesStatus <> '취소'
			""")
  Long sumDirectReceivableByPartnerId(@Param("partnerId") Long partnerId);

  @Query("select count(item) from SalesSlipItem item")
  long countItems();

  @Query("select count(snapshot) from SalesOrchidGroupSnapshot snapshot")
  long countOrchidGroupSnapshots();
}
