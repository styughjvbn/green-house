package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.partner.application.BusinessPartnerReader;
import com.greenhouse.backend.partner.domain.PartnerType;
import com.greenhouse.backend.sales.application.document.SalesSlipDocument;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesType;
import com.greenhouse.backend.sales.dto.SalesSlipCreateRequest;
import com.greenhouse.backend.sales.repository.SalesSlipRepository;
import com.greenhouse.backend.settlement.application.ExpectedPaymentDateCalculator;
import com.greenhouse.backend.settlement.application.PartnerBalanceService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class SalesSlipCreationService {
	private final BusinessPartnerReader partnerReader;
	private final SalesSlipRepository salesSlipRepository;
	private final SalesSlipAllocationFactory salesSlipAllocationFactory;
	private final SalesSlipInventoryService salesSlipInventoryService;
	private final ExpectedPaymentDateCalculator paymentDateCalculator;
	private final SalesSlipNumberGenerator numberGenerator;
	private final PartnerBalanceService partnerBalanceService;
	private final SalesSlipOutboundService salesSlipOutboundService;
	private final SalesSlipDocumentAssembler responseAssembler;

	public SalesSlipDocument create(SalesSlipCreateRequest request) {
		SalesType type = request.salesType() == null ? SalesType.DIRECT : request.salesType();
		if (request.partnerId() == null) {
			throw new IllegalArgumentException(type == SalesType.DIRECT
					? "일반 판매는 거래처를 선택해야 합니다." : "경매 판매는 경매장을 선택해야 합니다.");
		}
		if (request.items().isEmpty()) {
			throw new IllegalArgumentException(type == SalesType.DIRECT
					? "일반 판매 품목은 1개 이상 입력해야 합니다." : "경매 판매는 1개 이상의 lot 품목이 필요합니다.");
		}
		var partner = partnerReader.getActiveInfo(request.partnerId());
		if (type == SalesType.DIRECT && partner.partnerType() == PartnerType.AUCTION_HOUSE) {
			throw new IllegalArgumentException("경매장 거래처는 경매 판매 전표에서 사용해야 합니다.");
		}
		if (type == SalesType.AUCTION && partner.partnerType() != PartnerType.AUCTION_HOUSE) {
			throw new IllegalArgumentException("경매 판매는 경매장 거래처만 선택할 수 있습니다.");
		}
		if (type == SalesType.DIRECT) {
			partnerBalanceService.lockPartners(List.of(partner.id()));
		}

		var salesSlip = new SalesSlip(
				numberGenerator.generate(request.saleDate(), type),
				request.saleDate(),
				type,
				null,
				partner.id(),
				SalesTextNormalizer.defaultText(request.paymentStatus(), type.defaultPaymentStatus()),
				SalesTextNormalizer.defaultText(request.salesStatus(), "작성중"),
				SalesTextNormalizer.defaultText(request.paymentMethod(), type.defaultPaymentMethod()),
				SalesTextNormalizer.normalize(request.memo()));

		salesSlipAllocationFactory.createItems(request.items()).forEach(salesSlip::addItem);
		if (type == SalesType.DIRECT) {
			salesSlip.updateExpectedPaymentDate(paymentDateCalculator.calculate(partner.id(), request.saleDate()));
		}
		var saved = salesSlipRepository.save(salesSlip);
		salesSlipInventoryService.reserve(saved);
		if (saved.isOutboundCompleted()) {
			salesSlipOutboundService.complete(saved);
		}
		if (type == SalesType.DIRECT) {
			partnerBalanceService.updateReceivable(
					partner.id(), salesSlipRepository.sumDirectReceivableByPartnerId(partner.id()), null);
		}
		return responseAssembler.assemble(saved);
	}
}
