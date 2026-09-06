package com.greenhouse.backend.auction.repository;

import com.greenhouse.backend.auction.domain.AuctionShipment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;

public interface AuctionShipmentRepository extends JpaRepository<AuctionShipment, Long> {
	@Query("select shipment.id from AuctionShipment shipment order by shipment.shipmentDate desc, shipment.id desc")
	List<Long> findIdsNewestFirst(Pageable pageable);

	@EntityGraph(attributePaths = { "lots" })
	List<AuctionShipment> findAllByIdInOrderByShipmentDateDescIdDesc(java.util.Collection<Long> ids);

	@EntityGraph(attributePaths = { "lots" })
	Optional<AuctionShipment> findWithLotsById(Long id);
}
