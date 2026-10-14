package com.greenhouse.backend.sales.auction.repository;

import com.greenhouse.backend.sales.auction.domain.AuctionShipment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AuctionShipmentRepository extends JpaRepository<AuctionShipment, Long> {

  @Query(
      "select shipment.id from AuctionShipment shipment order by shipment.shipmentDate desc, shipment.id desc")
  List<Long> findIdsNewestFirst(Pageable pageable);

  @EntityGraph(attributePaths = {"lots"})
  List<AuctionShipment> findAllByIdInOrderByShipmentDateDescIdDesc(Collection<Long> ids);

  @EntityGraph(attributePaths = {"lots"})
  Optional<AuctionShipment> findWithLotsById(Long id);
}
