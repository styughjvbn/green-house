package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.repository.OrchidGroupRepository;
import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.partner.domain.PartnerType;
import com.greenhouse.backend.partner.repository.BusinessPartnerRepository;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesSlipItem;
import com.greenhouse.backend.sales.domain.SalesType;
import com.greenhouse.backend.sales.dto.SalesSlipCreateRequest;
import com.greenhouse.backend.sales.dto.SalesSlipResponse;
import com.greenhouse.backend.sales.repository.SalesSlipRepository;
import com.greenhouse.backend.settlement.application.ExpectedPaymentDateCalculator;
import com.greenhouse.backend.settlement.application.PartnerBalanceService;
import org.springframework.stereotype.Service;

@Service
public class DirectSalesSlipCreator {
	private final BusinessPartnerRepository partnerRepository;
	private final SalesSlipRepository salesSlipRepository;
	private final OrchidGroupRepository orchidGroupRepository;
	private final ExpectedPaymentDateCalculator paymentDateCalculator;
	private final SalesSlipNumberGenerator numberGenerator;
	private final PartnerBalanceService partnerBalanceService;

	public DirectSalesSlipCreator(
		BusinessPartnerRepository partnerRepository,
		SalesSlipRepository salesSlipRepository,
		OrchidGroupRepository orchidGroupRepository,
		ExpectedPaymentDateCalculator paymentDateCalculator,
		SalesSlipNumberGenerator numberGenerator,
		PartnerBalanceService partnerBalanceService
	) {
		this.partnerRepository = partnerRepository;
		this.salesSlipRepository = salesSlipRepository;
		this.orchidGroupRepository = orchidGroupRepository;
		this.paymentDateCalculator = paymentDateCalculator;
		this.numberGenerator = numberGenerator;
		this.partnerBalanceService = partnerBalanceService;
	}

	public SalesSlipResponse create(SalesSlipCreateRequest request) {
		if (request.partnerId() == null) throw new IllegalArgumentException("일반 판매는 거래처를 선택해야 합니다.");
		if (request.items().isEmpty()) throw new IllegalArgumentException("일반 판매 품목을 한 개 이상 입력해야 합니다.");
		BusinessPartner partner = findPartner(request.partnerId());
		if (partner.getPartnerType() == PartnerType.AUCTION_HOUSE) {
			throw new IllegalArgumentException("경매장 거래처는 경매 판매 전표에서 사용해야 합니다.");
		}

		var salesSlip = new SalesSlip(
			numberGenerator.generate(request.saleDate(), SalesType.DIRECT),
			request.saleDate(),
			SalesType.DIRECT,
			null,
			partner,
			SalesTextNormalizer.defaultText(request.paymentStatus(), "미입금"),
			SalesTextNormalizer.defaultText(request.salesStatus(), "작성중"),
			SalesTextNormalizer.normalize(request.paymentMethod()),
			SalesTextNormalizer.normalize(request.memo()));

		request.items().forEach(item -> salesSlip.addItem(new SalesSlipItem(
			item.orchidGroupId() == null
				? null
				: orchidGroupRepository.findById(item.orchidGroupId())
					.orElseThrow(() -> new NotFoundException("난 묶음을 찾을 수 없습니다.")),
			null,
			SalesTextNormalizer.required(item.itemName()),
			SalesTextNormalizer.normalize(item.genus()),
			SalesTextNormalizer.normalize(item.spec()),
			item.quantity(),
			item.unitPrice(),
			SalesTextNormalizer.normalize(item.memo()))));
		salesSlip.updateExpectedPaymentDate(paymentDateCalculator.calculate(partner, request.saleDate()));
		var saved = salesSlipRepository.save(salesSlip);
		partnerBalanceService.updateReceivable(
			partner.getId(), salesSlipRepository.sumDirectReceivableByPartnerId(partner.getId()), null);
		return SalesSlipResponse.from(saved);
	}

	private BusinessPartner findPartner(Long partnerId) {
		BusinessPartner partner = partnerRepository.findById(partnerId)
			.orElseThrow(() -> new NotFoundException("거래처를 찾을 수 없습니다."));
		if (!partner.isActive()) throw new IllegalArgumentException("비활성 거래처는 사용할 수 없습니다.");
		return partner;
	}
}
