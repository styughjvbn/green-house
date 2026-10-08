package com.greenhouse.backend.sales.auction.repository;

import com.greenhouse.backend.sales.auction.domain.AuctionProceeds;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AuctionProceedsRepository extends JpaRepository<AuctionProceeds, Long> {
  @Query(
      "select p.id as id, p.auctionHouseId as partnerId from AuctionProceeds p where p.id in :ids")
  List<PaymentOwner> findPaymentOwners(Collection<Long> ids);

  interface PaymentOwner {
    Long getId();

    Long getPartnerId();
  }

  @Query("select p.auctionHouseId from AuctionProceeds p where p.id = :id")
  Optional<Long> findAuctionHouseId(Long id);

  @Query(
      "select p from AuctionProceeds p where p.auctionHouseId = :partnerId and (:keyword = '' or lower(coalesce(p.sourceReference, '')) like lower(concat('%', :keyword, '%')))")
  Page<AuctionProceeds> findPaymentTargets(Long partnerId, String keyword, Pageable pageable);

  @Query("select p from AuctionProceeds p where (:houseId is null or p.auctionHouseId = :houseId)")
  Page<AuctionProceeds> findByAuctionHouseIdFilter(Long houseId, Pageable pageable);

  @Query(
      "select r.proceeds.id as proceedsId, r.resultLineId as resultLineId from AuctionProceedsResult r where r.proceeds.id in :ids order by r.resultLineId")
  List<ResultReference> findResultReferences(List<Long> ids);

  @Query(
      "select distinct lot.shipment.id from AuctionProceedsResult reference, AuctionResultLine line join line.auctionAttempt attempt join attempt.shipmentLot lot where reference.resultLineId = line.id and lot.shipment.id in :shipmentIds")
  List<Long> findReferencedShipmentIds(Collection<Long> shipmentIds);

  interface ResultReference {
    Long getProceedsId();

    Long getResultLineId();
  }

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select proceeds from AuctionProceeds proceeds where proceeds.id = :id")
  Optional<AuctionProceeds> findForUpdate(Long id);
}
