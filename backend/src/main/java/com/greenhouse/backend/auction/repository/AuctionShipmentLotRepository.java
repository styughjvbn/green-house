package com.greenhouse.backend.auction.repository;

import com.greenhouse.backend.auction.domain.AuctionLotStatus;
import com.greenhouse.backend.auction.domain.AuctionShipmentLot;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuctionShipmentLotRepository
    extends JpaRepository<AuctionShipmentLot, Long>, AuctionShipmentLotRepositoryCustom {

  @Query(
      """
      select lot.id as lotId, lot.shipment.id as shipmentId from AuctionShipmentLot lot
      where lot.shipment.id in :shipmentIds
      """)
  List<LotShipmentIdRow> findLotShipmentIds(@Param("shipmentIds") Collection<Long> shipmentIds);

  interface LotShipmentIdRow {
    Long getLotId();

    Long getShipmentId();
  }

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select lot from AuctionShipmentLot lot where lot.shipment.id = :shipmentId order by lot.id")
  List<AuctionShipmentLot> findAllForUpdateByShipmentId(@Param("shipmentId") Long shipmentId);

  @Query(
      """
			select distinct lot.shipment.id from AuctionShipmentLot lot
			where lot.shipment.id in :shipmentIds
			  and (lot.currentStatus <> :waitingStatus
			       or lot.soldQuantity <> 0
			       or lot.returnedQuantity <> 0
			       or lot.attempts is not empty
			       or lot.statusHistory is not empty)
			""")
  List<Long> findNonCancelableShipmentIds(
      @Param("shipmentIds") Collection<Long> shipmentIds,
      @Param("waitingStatus") AuctionLotStatus waitingStatus);

  @EntityGraph(attributePaths = {"shipment"})
  List<AuctionShipmentLot> findAllByOrderByIdDesc();

  @EntityGraph(attributePaths = {"shipment"})
  Optional<AuctionShipmentLot> findWithDetailsById(Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  // Lock only the lot root; a shipment fetch graph would also introduce parent row locks.
  @Query("select lot from AuctionShipmentLot lot where lot.id = :id")
  Optional<AuctionShipmentLot> findForUpdateById(@Param("id") Long id);
}
