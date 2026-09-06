package com.greenhouse.backend.settlement.application;

import com.greenhouse.backend.auction.application.AuctionDataReader;
import com.greenhouse.backend.settlement.repository.AuctionSettlementRepository;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuctionSettlementReader {

	private final AuctionSettlementRepository auctionSettlementRepository;
	private final AuctionDataReader auctionReader;

	public boolean existsByAuctionShipmentId(Long shipmentId) {
		return !findSettledAuctionShipmentIds(List.of(shipmentId)).isEmpty();
	}

	public Set<Long> findSettledAuctionShipmentIds(Collection<Long> shipmentIds) {
		if (shipmentIds.isEmpty()) {
			return Set.of();
		}
		var shipmentByLotId = auctionReader.getLotShipmentIds(shipmentIds);
		if (shipmentByLotId.isEmpty()) {
			return Set.of();
		}
		return auctionSettlementRepository.findSettledLotIds(shipmentByLotId.keySet()).stream()
				.map(shipmentByLotId::get).collect(Collectors.toUnmodifiableSet());
	}
}
