package com.greenhouse.backend.auction.application;

import com.greenhouse.backend.auction.domain.AuctionResultLine;
import com.greenhouse.backend.auction.domain.AuctionShipment;
import com.greenhouse.backend.auction.repository.AuctionResultLineRepository;
import com.greenhouse.backend.auction.repository.AuctionShipmentRepository;
import com.greenhouse.backend.common.exception.NotFoundException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuctionDataReader {
	private final AuctionShipmentRepository shipmentRepository;
	private final AuctionResultLineRepository resultLineRepository;

	public AuctionDataReader(
		AuctionShipmentRepository shipmentRepository,
		AuctionResultLineRepository resultLineRepository
	) {
		this.shipmentRepository = shipmentRepository;
		this.resultLineRepository = resultLineRepository;
	}

	public List<AuctionShipment> getShipmentsNewestFirst() {
		return shipmentRepository.findAllByOrderByShipmentDateDescIdDesc();
	}

	public AuctionShipment getShipmentWithLots(Long shipmentId) {
		return shipmentRepository.findWithLotsById(shipmentId)
			.orElseThrow(() -> new NotFoundException("경매 출하 기록을 찾을 수 없습니다."));
	}

	public List<AuctionResultLine> getSoldResultLines(Long auctionHouseId, LocalDate auctionDate) {
		return resultLineRepository.findSoldLines(auctionHouseId, auctionDate);
	}

	public List<AuctionResultLine> getAllSoldResultLines() {
		return resultLineRepository.findAllSoldLines();
	}
}
