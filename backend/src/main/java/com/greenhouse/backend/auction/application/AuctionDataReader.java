package com.greenhouse.backend.auction.application;

import com.greenhouse.backend.auction.domain.AuctionResultLine;
import com.greenhouse.backend.auction.domain.AuctionShipment;
import com.greenhouse.backend.auction.repository.AuctionResultLineRepository;
import com.greenhouse.backend.auction.repository.AuctionShipmentLotRepository;
import com.greenhouse.backend.auction.repository.AuctionShipmentRepository;
import com.greenhouse.backend.partner.application.BusinessPartnerReader;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuctionDataReader {

	private final BusinessPartnerReader partnerReader;

	private final AuctionShipmentRepository shipmentRepository;

	private final AuctionResultLineRepository resultLineRepository;

	private final AuctionShipmentLotRepository lotRepository;

	public Map<Long, String> getMarketNames(Collection<Long> shipmentIds) {
		if (shipmentIds.isEmpty()) {
			return Map.of();
		}
		var shipments = shipmentRepository.findAllById(shipmentIds);
		var partners = partnerReader.getAllInfo(shipments.stream().map(AuctionShipment::getAuctionHouseId).toList());
		return shipments.stream()
			.collect(Collectors.toMap(AuctionShipment::getId,
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
		return shipments.stream()
			.map(shipment -> new Shipment(shipment.getId(), shipment.getShipmentDate(), shipment.getAuctionHouseId(),
					partners.get(shipment.getAuctionHouseId()).name(),
					shipment.getLots()
						.stream()
						.map(lot -> new Lot(lot.getId(), lot.getItemName(), lot.getVarietyName(),
								lot.getShipmentGrade(), lot.getShippedQuantity()))
						.toList()))
			.toList();
	}

	public List<Result> getSoldResultLines(Long auctionHouseId, LocalDate auctionDate) {
		return resultLineRepository.findSoldLines(auctionHouseId, auctionDate).stream().map(Result::from).toList();
	}

	public List<Long> getSoldResultIdsAfter(Long afterId, int size) {
		return resultLineRepository.findSoldIdsAfter(afterId, PageRequest.of(0, Math.min(Math.max(size, 1), 500)));
	}

	public Map<Long, Result> getResults(Collection<Long> resultIds) {
		if (resultIds.isEmpty()) {
			return Map.of();
		}
		return resultLineRepository.findAllByIdIn(resultIds)
			.stream()
			.map(Result::from)
			.collect(Collectors.toMap(Result::id, result -> result));
	}

	public Map<Long, Long> getLotShipmentIds(Collection<Long> shipmentIds) {
		if (shipmentIds.isEmpty()) {
			return Map.of();
		}
		return lotRepository.findAllByShipmentIdIn(shipmentIds)
			.stream()
			.collect(Collectors.toMap(lot -> lot.getId(), lot -> lot.getShipment().getId()));
	}

	public record Shipment(Long id, LocalDate shipmentDate, Long auctionHouseId, String auctionMarket, List<Lot> lots) {
		public Shipment {
			lots = List.copyOf(lots);
		}
	}

	public record Lot(Long id, String itemName, String varietyName, String shipmentGrade, Integer shippedQuantity) {
	}

	public record Result(Long id, Long lotId, Long auctionHouseId, LocalDate auctionDate, LocalDate shipmentDate,
			String varietyName, String shipmentGrade, Integer quantity, Integer unitPrice, Long amount) {
		static Result from(AuctionResultLine line) {
			var lot = line.getAuctionAttempt().getShipmentLot();
			var shipment = lot.getShipment();
			return new Result(line.getId(), lot.getId(), shipment.getAuctionHouseId(), line.getAuctionDate(),
					shipment.getShipmentDate(), lot.getVarietyName(), lot.getShipmentGrade(), line.getQuantity(),
					line.getUnitPrice(), line.getAmount().longValue());
		}
	}

}
