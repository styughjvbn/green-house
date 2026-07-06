package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.auction.repository.AuctionShipmentRepository;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.sales.dto.AuctionShipmentOptionResponse;
import com.greenhouse.backend.sales.dto.SalesSlipResponse;
import com.greenhouse.backend.sales.repository.SalesSlipRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SalesQueryService {
	private final SalesSlipRepository salesSlipRepository;
	private final AuctionShipmentRepository auctionShipmentRepository;

	public SalesQueryService(
		SalesSlipRepository salesSlipRepository,
		AuctionShipmentRepository auctionShipmentRepository
	) {
		this.salesSlipRepository = salesSlipRepository;
		this.auctionShipmentRepository = auctionShipmentRepository;
	}

	public List<SalesSlipResponse> getSalesSlips(Long partnerId, LocalDate from, LocalDate to) {
		return salesSlipRepository.search(partnerId, from, to).stream()
			.map(SalesSlipResponse::from)
			.toList();
	}

	public SalesSlipResponse getSalesSlip(Long salesSlipId) {
		return salesSlipRepository.findWithDetailsById(salesSlipId)
			.map(SalesSlipResponse::from)
			.orElseThrow(() -> new NotFoundException("판매 전표를 찾을 수 없습니다."));
	}

	public List<AuctionShipmentOptionResponse> getAuctionShipmentOptions() {
		return auctionShipmentRepository.findAllByOrderByShipmentDateDescIdDesc().stream()
			.filter(shipment -> !salesSlipRepository.existsByAuctionShipmentId(shipment.getId()))
			.map(AuctionShipmentOptionResponse::from)
			.toList();
	}
}
