package com.greenhouse.backend.auction.application;

import com.greenhouse.backend.auction.domain.AuctionShipment;
import com.greenhouse.backend.auction.repository.AuctionShipmentRepository;
import org.springframework.stereotype.Service;

@Service
public class AuctionShipmentCreator {
	private final AuctionShipmentRepository auctionShipmentRepository;

	public AuctionShipmentCreator(AuctionShipmentRepository auctionShipmentRepository) {
		this.auctionShipmentRepository = auctionShipmentRepository;
	}

	public AuctionShipment save(AuctionShipment shipment) {
		return auctionShipmentRepository.save(shipment);
	}
}
