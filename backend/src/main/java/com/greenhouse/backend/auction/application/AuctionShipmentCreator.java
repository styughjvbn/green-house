package com.greenhouse.backend.auction.application;

import com.greenhouse.backend.auction.domain.AuctionShipment;
import com.greenhouse.backend.auction.repository.AuctionShipmentRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuctionShipmentCreator {
	private final AuctionShipmentRepository auctionShipmentRepository;

	public AuctionShipment save(AuctionShipment shipment) {
		return auctionShipmentRepository.save(shipment);
	}
}
