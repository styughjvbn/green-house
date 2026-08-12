package com.greenhouse.backend.auction.repository;

import com.greenhouse.backend.auction.domain.AuctionShipmentLot;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import com.greenhouse.backend.auction.domain.AuctionLotStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuctionShipmentLotRepository
		extends JpaRepository<AuctionShipmentLot, Long>, AuctionShipmentLotRepositoryCustom {
	boolean existsByShipmentIdAndCurrentStatusNot(Long shipmentId, AuctionLotStatus status);

	@EntityGraph(attributePaths = { "shipment", "shipment.auctionHouse" })
	List<AuctionShipmentLot> findAllByOrderByIdDesc();

	@EntityGraph(attributePaths = { "shipment", "shipment.auctionHouse" })
	Optional<AuctionShipmentLot> findWithDetailsById(Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@EntityGraph(attributePaths = { "shipment", "shipment.auctionHouse" })
	@Query("select lot from AuctionShipmentLot lot where lot.id = :id")
	Optional<AuctionShipmentLot> findForUpdateById(@Param("id") Long id);
}
