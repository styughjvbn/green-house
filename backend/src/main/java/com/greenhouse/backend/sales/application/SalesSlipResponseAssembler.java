package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesSlipItemAllocation;
import com.greenhouse.backend.sales.dto.SalesSlipAction;
import com.greenhouse.backend.sales.dto.SalesSlipResponse;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SalesSlipResponseAssembler {

	private final SalesSlipActionResolver actionResolver;

	public SalesSlipResponse assemble(SalesSlip salesSlip) {
		return SalesSlipResponse.from(salesSlip, null, actionResolver.resolve(salesSlip));
	}

	public List<SalesSlipResponse> assemble(
			List<SalesSlip> salesSlips,
			Map<Long, List<SalesSlipItemAllocation>> allocationsByItemId) {
		Map<Long, List<SalesSlipAction>> actionsBySalesSlipId =
				actionResolver.resolveAll(salesSlips);
		return salesSlips.stream()
				.map(salesSlip -> SalesSlipResponse.from(
						salesSlip,
						allocationsByItemId,
						actionsBySalesSlipId.getOrDefault(salesSlip.getId(), List.of())))
				.toList();
	}
}
