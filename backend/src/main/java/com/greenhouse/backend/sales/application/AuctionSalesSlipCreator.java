package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.auction.application.AuctionShipmentCreator;
import com.greenhouse.backend.auction.domain.AuctionShipment;
import com.greenhouse.backend.auction.domain.AuctionShipmentLot;
import com.greenhouse.backend.partner.application.BusinessPartnerReader;
import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.partner.domain.PartnerType;
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
	private final AuctionShipmentCreator auctionShipmentCreator;
	private final BusinessPartnerReader partnerReader;
	private final SalesSlipNumberGenerator numberGenerator;

	public AuctionSalesSlipCreator(
		SalesSlipRepository salesSlipRepository,
		AuctionShipmentCreator auctionShipmentCreator,
		BusinessPartnerReader partnerReader,
		SalesSlipNumberGenerator numberGenerator
	) {
		this.salesSlipRepository = salesSlipRepository;
		this.auctionShipmentCreator = auctionShipmentCreator;
		this.partnerReader = partnerReader;
		this.numberGenerator = numberGenerator;
	}

	public SalesSlipResponse create(SalesSlipCreateRequest request) {
		if (request.partnerId() == null) {
			throw new IllegalArgumentException("경매 판매는 경매장을 선택해야 합니다.");
		}
		if (request.items().isEmpty()) {
			throw new IllegalArgumentException("경매 판매는 1개 이상의 lot 항목이 필요합니다.");
		}

		BusinessPartner partner = partnerReader.getActive(request.partnerId());
		if (partner.getPartnerType() != PartnerType.AUCTION_HOUSE) {
			throw new IllegalArgumentException("경매 판매는 경매장 거래처만 선택할 수 있습니다.");
		}

		var shipment = new AuctionShipment(request.saleDate(), partner);
		request.items().forEach(item -> shipment.addLot(new AuctionShipmentLot(
			SalesTextNormalizer.required(
				item.genus() == null || item.genus().isBlank()
					? item.itemName()
					: item.genus()
			),
			SalesTextNormalizer.required(item.itemName()),
			SalesTextNormalizer.normalize(item.spec()),
			null,
			item.quantity()
		)));
		AuctionShipment savedShipment = auctionShipmentCreator.save(shipment);

		var salesSlip = new SalesSlip(
			numberGenerator.generate(savedShipment.getShipmentDate(), SalesType.AUCTION),
			savedShipment.getShipmentDate(),
			SalesType.AUCTION,
			savedShipment,
			partner,
			SalesTextNormalizer.defaultText(request.paymentStatus(), "정산 대기"),
			SalesTextNormalizer.defaultText(request.salesStatus(), "출하 완료"),
			SalesTextNormalizer.defaultText(request.paymentMethod(), "경매 정산"),
			SalesTextNormalizer.normalize(request.memo())
		);

		for (int index = 0; index < request.items().size(); index++) {
			var item = request.items().get(index);
			var lot = savedShipment.getLots().get(index);
			salesSlip.addItem(new SalesSlipItem(
				null,
				lot,
				SalesTextNormalizer.required(item.itemName()),
				SalesTextNormalizer.normalize(item.genus()),
				SalesTextNormalizer.normalize(item.spec()),
				item.quantity(),
				item.unitPrice(),
				SalesTextNormalizer.normalize(item.memo())
			));
		}

		return SalesSlipResponse.from(salesSlipRepository.save(salesSlip));
	}
}
