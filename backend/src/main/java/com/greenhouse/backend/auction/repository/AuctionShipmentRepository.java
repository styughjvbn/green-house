package com.greenhouse.backend.auction.repository;

import com.greenhouse.backend.auction.domain.AuctionShipment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuctionShipmentRepository extends JpaRepository<AuctionShipment, Long> { }
