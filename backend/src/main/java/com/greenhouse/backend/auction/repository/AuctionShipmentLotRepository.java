package com.greenhouse.backend.auction.repository;

import com.greenhouse.backend.auction.domain.AuctionShipmentLot;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import com.greenhouse.backend.auction.domain.AuctionLotStatus;

public interface AuctionShipmentLotRepository
		extends JpaRepository<AuctionShipmentLot, Long>, AuctionShipmentLotRepositoryCustom {
	boolean existsByShipmentIdAndCurrentStatusNot(Long shipmentId, AuctionLotStatus status);

	@EntityGraph(attributePaths = { "shipment", "shipment.auctionHouse" })
	List<AuctionShipmentLot> findAllByOrderByIdDesc();

	@EntityGraph(attributePaths = { "shipment", "shipment.auctionHouse" })
	Optional<AuctionShipmentLot> findWithDetailsById(Long id);
}
