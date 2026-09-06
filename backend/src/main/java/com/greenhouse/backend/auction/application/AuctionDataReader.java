package com.greenhouse.backend.auction.application;

import com.greenhouse.backend.auction.domain.AuctionResultLine;
import com.greenhouse.backend.auction.domain.AuctionShipment;
import com.greenhouse.backend.auction.repository.AuctionResultLineRepository;
import com.greenhouse.backend.auction.repository.AuctionShipmentRepository;
import com.greenhouse.backend.partner.application.BusinessPartnerReader;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
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

	public List<Long> getShipmentIdsNewestFirst(int page, int size) {
		return shipmentRepository.findIdsNewestFirst(PageRequest.of(page, Math.min(Math.max(size, 1), 200)));
	}

	public List<Shipment> getShipmentsWithLotsNewestFirst(Collection<Long> shipmentIds) {
		if (shipmentIds.isEmpty()) {
			return List.of();
		}
		var shipments = shipmentRepository.findAllByIdInOrderByShipmentDateDescIdDesc(shipmentIds);
		var partners = partnerReader.getAllInfo(shipments.stream().map(AuctionShipment::getAuctionHouseId).toList());
		return shipments.stream().map(shipment -> new Shipment(shipment.getId(), shipment.getShipmentDate(),
				shipment.getAuctionHouseId(), partners.get(shipment.getAuctionHouseId()).name(),
				shipment.getLots().stream().map(lot -> new Lot(lot.getId(), lot.getItemName(), lot.getVarietyName(),
						lot.getShipmentGrade(), lot.getShippedQuantity())).toList())).toList();
	}

	public List<AuctionResultLine> getSoldResultLines(Long auctionHouseId, LocalDate auctionDate) {
		return resultLineRepository.findSoldLines(auctionHouseId, auctionDate);
	}

	public record Shipment(Long id, LocalDate shipmentDate, Long auctionHouseId, String auctionMarket, List<Lot> lots) {
		public Shipment {
			lots = List.copyOf(lots);
		}
	}

	public record Lot(Long id, String itemName, String varietyName, String shipmentGrade, Integer shippedQuantity) {
	}
}
