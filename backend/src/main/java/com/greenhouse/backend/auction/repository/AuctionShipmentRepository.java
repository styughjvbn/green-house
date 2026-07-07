package com.greenhouse.backend.auction.repository;

import com.greenhouse.backend.auction.domain.AuctionShipment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;

public interface AuctionShipmentRepository extends JpaRepository<AuctionShipment, Long> {
	@EntityGraph(attributePaths = { "lots", "auctionHouse" })
	List<AuctionShipment> findAllByOrderByShipmentDateDescIdDesc();

	@EntityGraph(attributePaths = { "lots", "auctionHouse" })
	Optional<AuctionShipment> findWithLotsById(Long id);
}
