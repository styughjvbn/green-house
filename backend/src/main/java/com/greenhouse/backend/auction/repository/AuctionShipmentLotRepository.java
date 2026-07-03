package com.greenhouse.backend.auction.repository;

import com.greenhouse.backend.auction.domain.AuctionShipmentLot;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuctionShipmentLotRepository extends JpaRepository<AuctionShipmentLot, Long> {
	@EntityGraph(attributePaths = "shipment")
	List<AuctionShipmentLot> findAllByOrderByIdDesc();

	@EntityGraph(attributePaths = {"shipment", "sourceRow"})
	Optional<AuctionShipmentLot> findWithDetailsById(Long id);
}
