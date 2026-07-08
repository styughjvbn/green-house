package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.partner.application.BusinessPartnerReader;
import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.partner.domain.PartnerType;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesType;
import com.greenhouse.backend.sales.dto.SalesSlipCreateRequest;
import com.greenhouse.backend.sales.dto.SalesSlipResponse;
import com.greenhouse.backend.sales.repository.SalesSlipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuctionSalesSlipCreator {
	private final SalesSlipRepository salesSlipRepository;
	private final BusinessPartnerReader partnerReader;
	private final SalesSlipNumberGenerator numberGenerator;
	private final SalesSlipAllocationFactory salesSlipAllocationFactory;
	private final SalesSlipInventoryService salesSlipInventoryService;
	private final AuctionShipmentMaterializer auctionShipmentMaterializer;

	public SalesSlipResponse create(SalesSlipCreateRequest request) {
		if (request.partnerId() == null) {
			throw new IllegalArgumentException("경매 판매는 경매장을 선택해야 합니다.");
		}
		if (request.items().isEmpty()) {
			throw new IllegalArgumentException("경매 판매는 1개 이상의 lot 품목이 필요합니다.");
		}

		BusinessPartner partner = partnerReader.getActive(request.partnerId());
		if (partner.getPartnerType() != PartnerType.AUCTION_HOUSE) {
			throw new IllegalArgumentException("경매 판매는 경매장 거래처만 선택할 수 있습니다.");
		}

		var salesSlip = new SalesSlip(
				numberGenerator.generate(request.saleDate(), SalesType.AUCTION),
				request.saleDate(),
				SalesType.AUCTION,
				null,
				partner,
				SalesTextNormalizer.defaultText(request.paymentStatus(), "정산 대기"),
				SalesTextNormalizer.defaultText(request.salesStatus(), "작성중"),
				SalesTextNormalizer.defaultText(request.paymentMethod(), "경매 정산"),
				SalesTextNormalizer.normalize(request.memo()));

		request.items().forEach(item -> salesSlip.addItem(salesSlipAllocationFactory.createItem(item)));

		var saved = salesSlipRepository.save(salesSlip);
		salesSlipInventoryService.reserve(saved);
		if (saved.isOutboundCompleted()) {
			auctionShipmentMaterializer.materialize(saved);
			salesSlipInventoryService.outbound(saved);
		}
		return SalesSlipResponse.from(saved);
	}
}
