package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.auction.application.AuctionDataReader;
import com.greenhouse.backend.auction.domain.AuctionShipment;
import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesSlipItem;
import com.greenhouse.backend.sales.domain.SalesType;
import com.greenhouse.backend.sales.dto.SalesSlipCreateRequest;
import com.greenhouse.backend.sales.dto.SalesSlipResponse;
import com.greenhouse.backend.sales.repository.SalesSlipRepository;
import org.springframework.stereotype.Service;

@Service
public class AuctionSalesSlipCreator {
	private final SalesSlipRepository salesSlipRepository;
	private final AuctionDataReader auctionDataReader;
	private final SalesSlipNumberGenerator numberGenerator;

	public AuctionSalesSlipCreator(
		SalesSlipRepository salesSlipRepository,
		AuctionDataReader auctionDataReader,
		SalesSlipNumberGenerator numberGenerator
	) {
		this.salesSlipRepository = salesSlipRepository;
		this.auctionDataReader = auctionDataReader;
		this.numberGenerator = numberGenerator;
	}

	public SalesSlipResponse create(SalesSlipCreateRequest request) {
		if (request.auctionShipmentId() == null) throw new IllegalArgumentException("경매 판매는 출하 기록을 선택해야 합니다.");
		if (salesSlipRepository.existsByAuctionShipmentId(request.auctionShipmentId())) {
			throw new IllegalArgumentException("이미 판매 전표가 생성된 경매 출하 기록입니다.");
		}
		AuctionShipment shipment = auctionDataReader.getShipmentWithLots(request.auctionShipmentId());
		if (shipment.getLots().isEmpty()) throw new IllegalArgumentException("출하 lot이 없는 경매 출하 기록입니다.");
		BusinessPartner partner = shipment.getAuctionHouse();
		if (request.partnerId() != null && !request.partnerId().equals(partner.getId())) {
			throw new IllegalArgumentException("선택한 거래처가 출하 기록의 경매장과 다릅니다.");
		}

		var salesSlip = new SalesSlip(
			numberGenerator.generate(shipment.getShipmentDate(), SalesType.AUCTION),
			shipment.getShipmentDate(),
			SalesType.AUCTION,
			shipment,
			partner,
			"정산 대기",
			"출하 완료",
			"경매 정산",
			SalesTextNormalizer.normalize(request.memo()));
		shipment.getLots().forEach(lot -> salesSlip.addItem(new SalesSlipItem(
			null,
			lot,
			lot.getVarietyName(),
			null,
			lot.getShipmentGrade(),
			lot.getShippedQuantity(),
			0,
			"경매 출하 lot #" + lot.getId())));
		return SalesSlipResponse.from(salesSlipRepository.save(salesSlip));
	}
}
