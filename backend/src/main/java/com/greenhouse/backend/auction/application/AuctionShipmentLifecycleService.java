package com.greenhouse.backend.auction.application;

import com.greenhouse.backend.auction.domain.AuctionLotStatus;
import com.greenhouse.backend.auction.repository.AuctionShipmentLotRepository;
import com.greenhouse.backend.auction.repository.AuctionShipmentRepository;
import lombok.RequiredArgsConstructor;
import java.util.Collection;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuctionShipmentLifecycleService {

	private final AuctionShipmentRepository auctionShipmentRepository;
	private final AuctionShipmentLotRepository auctionShipmentLotRepository;

	public void deleteDraftShipment(Long shipmentId) {
		if (shipmentId == null) {
			return;
		}
		if (auctionShipmentLotRepository.existsByShipmentIdAndCurrentStatusNot(shipmentId, AuctionLotStatus.WAITING)) {
			throw new IllegalArgumentException("경매 결과가 반영된 출하 lot이 있어 전표를 취소할 수 없습니다.");
		}
		auctionShipmentRepository.deleteById(shipmentId);
	}

	public Set<Long> findShipmentIdsWithResults(Collection<Long> shipmentIds) {
		if (shipmentIds.isEmpty()) {
			return Set.of();
		}
		return Set.copyOf(auctionShipmentLotRepository.findShipmentIdsWithStatusNot(
				shipmentIds,
				AuctionLotStatus.WAITING));
	}
}
