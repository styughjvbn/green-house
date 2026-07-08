package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.partner.application.BusinessPartnerReader;
import com.greenhouse.backend.partner.domain.PartnerType;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesSlipItem;
import com.greenhouse.backend.sales.domain.SalesType;
import com.greenhouse.backend.sales.dto.SalesSlipCreateRequest;
import com.greenhouse.backend.sales.dto.SalesSlipResponse;
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
public class SalesSlipUpdateService {

	private final SalesSlipRepository salesSlipRepository;
	private final BusinessPartnerReader businessPartnerReader;
	private final SalesSlipAllocationFactory salesSlipAllocationFactory;
	private final SalesSlipInventoryService salesSlipInventoryService;
	private final ExpectedPaymentDateCalculator paymentDateCalculator;
	private final PartnerBalanceService partnerBalanceService;

	public SalesSlipResponse update(Long salesSlipId, SalesSlipCreateRequest request) {
		SalesSlip salesSlip = salesSlipRepository.findWithDetailsById(salesSlipId)
				.orElseThrow(() -> new NotFoundException("판매 전표를 찾을 수 없습니다."));

		validateEditable(salesSlip, request);
		if (request.partnerId() == null) {
			throw new IllegalArgumentException("일반 판매는 거래처를 선택해야 합니다.");
		}
		if (request.items().isEmpty()) {
			throw new IllegalArgumentException("일반 판매 품목은 1개 이상 입력해야 합니다.");
		}

		var partner = businessPartnerReader.getActive(request.partnerId());
		if (partner.getPartnerType() == PartnerType.AUCTION_HOUSE) {
			throw new IllegalArgumentException("경매장 거래처는 경매 판매 전표에서 사용해야 합니다.");
		}
		var expectedPaymentDate = paymentDateCalculator.calculate(partner, request.saleDate());

		salesSlipInventoryService.releaseForEdit(salesSlip);

		List<SalesSlipItem> items = request.items().stream()
				.map(salesSlipAllocationFactory::createItem)
				.toList();
		if (salesSlip.getItems().size() != items.size()) {
			throw new IllegalArgumentException("품목 개수 변경 수정은 아직 지원하지 않습니다.");
		}

		salesSlip.updateDraftInfo(
				request.saleDate(),
				partner,
				SalesTextNormalizer.defaultText(request.paymentStatus(), "미입금"),
				SalesTextNormalizer.normalize(request.paymentMethod()),
				SalesTextNormalizer.normalize(request.memo()));
		for (int index = 0; index < salesSlip.getItems().size(); index++) {
			var currentItem = salesSlip.getItems().get(index);
			var nextItem = items.get(index);
			currentItem.updateDetails(
					nextItem.getItemName(),
					nextItem.getGenus(),
					nextItem.getSpec(),
					nextItem.getQuantity(),
					nextItem.getUnitPrice(),
					nextItem.getMemo());
			currentItem.replaceAllocations(nextItem.getAllocations().stream()
					.map(allocation -> new com.greenhouse.backend.sales.domain.SalesSlipItemAllocation(
							allocation.getOrchidGroup(),
							allocation.getAllocatedQuantity()))
					.toList());
		}
		salesSlip.refreshAmounts();
		salesSlip.updateExpectedPaymentDate(expectedPaymentDate);
		salesSlipRepository.saveAndFlush(salesSlip);
		SalesSlip persisted = salesSlipRepository.findWithDetailsById(salesSlipId)
				.orElseThrow(() -> new NotFoundException("판매 전표를 찾을 수 없습니다."));
		salesSlipInventoryService.reserve(persisted);
		partnerBalanceService.updateReceivable(
				partner.getId(), salesSlipRepository.sumDirectReceivableByPartnerId(partner.getId()), null);

		return SalesSlipResponse.from(persisted);
	}

	private void validateEditable(SalesSlip salesSlip, SalesSlipCreateRequest request) {
		if (salesSlip.getSalesType() != SalesType.DIRECT || request.salesType() == SalesType.AUCTION) {
			throw new IllegalArgumentException("경매 판매 전표 수정은 아직 지원하지 않습니다.");
		}
		if (!"작성중".equals(salesSlip.getSalesStatus())) {
			throw new IllegalArgumentException("작성중 상태 전표만 수정할 수 있습니다.");
		}
		if (salesSlip.getPaidAmount() != null && salesSlip.getPaidAmount() > 0) {
			throw new IllegalArgumentException("입금 이력이 있는 전표는 수정할 수 없습니다.");
		}
	}
}
