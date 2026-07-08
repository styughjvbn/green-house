package com.greenhouse.backend.settlement.application;

import com.greenhouse.backend.settlement.repository.AuctionSettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuctionSettlementReader {

	private final AuctionSettlementRepository auctionSettlementRepository;

	public boolean existsByAuctionShipmentId(Long shipmentId) {
		return auctionSettlementRepository.existsByAuctionShipmentId(shipmentId);
	}
}
