package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.sales.dto.SalesSlipResponse;
import com.greenhouse.backend.sales.dto.SalesSlipStatusUpdateRequest;
import com.greenhouse.backend.sales.repository.SalesSlipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class SalesSlipStatusService {

	private final SalesSlipRepository salesSlipRepository;
	private final SalesSlipInventoryService salesSlipInventoryService;

	public SalesSlipResponse updateStatus(Long salesSlipId, SalesSlipStatusUpdateRequest request) {
		var salesSlip = salesSlipRepository.findWithDetailsById(salesSlipId)
				.orElseThrow(() -> new NotFoundException("판매 전표를 찾을 수 없습니다."));
		if (salesSlip.isOutboundCompleted()) {
			throw new IllegalArgumentException("출고 완료된 전표는 판매 상태를 변경할 수 없습니다.");
		}

		salesSlip.updateSalesStatus(request.salesStatus());
		if (salesSlip.isOutboundCompleted()) {
			salesSlipInventoryService.outbound(salesSlip);
		}
		return SalesSlipResponse.from(salesSlip);
	}
}
