package com.greenhouse.backend.auction.application;

import com.greenhouse.backend.auction.domain.AuctionResultLine;
import com.greenhouse.backend.auction.domain.AuctionShipment;
import com.greenhouse.backend.auction.repository.AuctionResultLineRepository;
import com.greenhouse.backend.auction.repository.AuctionShipmentRepository;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.partner.application.BusinessPartnerReader;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuctionDataReader {
	private final BusinessPartnerReader partnerReader;
	private final AuctionShipmentRepository shipmentRepository;
	private final AuctionResultLineRepository resultLineRepository;

	public Map<Long, String> getMarketNames(Collection<Long> shipmentIds) {
		if (shipmentIds.isEmpty()) {
			return Map.of();
		}
		var shipments = shipmentRepository.findAllById(shipmentIds);
		var partners = partnerReader.getAllInfo(shipments.stream().map(AuctionShipment::getAuctionHouseId).toList());
		return shipments.stream().collect(Collectors.toMap(AuctionShipment::getId,
				shipment -> partners.get(shipment.getAuctionHouseId()).name()));
	}

	public List<AuctionShipment> getShipmentsWithLotsNewestFirst(java.util.Collection<Long> shipmentIds) {
		if (shipmentIds.isEmpty()) {
			return List.of();
		}
		return shipmentRepository.findAllByIdInOrderByShipmentDateDescIdDesc(shipmentIds);
	}

	public AuctionShipment getShipmentWithLots(Long shipmentId) {
		return shipmentRepository.findWithLotsById(shipmentId)
				.orElseThrow(() -> new NotFoundException("경매 출하 기록을 찾을 수 없습니다."));
	}

	public List<AuctionResultLine> getSoldResultLines(Long auctionHouseId, LocalDate auctionDate) {
		return resultLineRepository.findSoldLines(auctionHouseId, auctionDate);
	}

}
