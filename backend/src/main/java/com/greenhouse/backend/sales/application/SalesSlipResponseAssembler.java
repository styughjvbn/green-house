package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.auction.application.AuctionDataReader;
import com.greenhouse.backend.partner.application.BusinessPartnerReader;
import com.greenhouse.backend.sales.dto.SalesSlipListItemResponse;
import org.springframework.data.domain.Page;
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

	private final BusinessPartnerReader partnerReader;
	private final AuctionDataReader auctionReader;
	private final SalesSlipActionResolver actionResolver;

	public Page<SalesSlipListItemResponse> assemblePage(Page<SalesSlip> page) {
		var partners = partnerReader.getAllInfo(page.map(SalesSlip::getPartnerId).getContent());
		var marketNames = marketNames(page.getContent());
		return page.map(slip -> SalesSlipListItemResponse.from(slip, partners.get(slip.getPartnerId()),
				slip.getAuctionShipmentId() == null ? null : marketNames.get(slip.getAuctionShipmentId())));
	}

	private Map<Long, String> marketNames(List<SalesSlip> slips) {
		return auctionReader.getMarketNames(slips.stream().filter(slip -> slip.getAuctionShipmentId() != null)
				.map(slip -> slip.getAuctionShipmentId()).distinct().toList());
	}

	public SalesSlipResponse assemble(SalesSlip salesSlip) {
		return assemble(List.of(salesSlip), null).getFirst();
	}

	public List<SalesSlipResponse> assemble(
			List<SalesSlip> salesSlips,
			Map<Long, List<SalesSlipItemAllocation>> allocationsByItemId) {
		var partners = partnerReader.getAllInfo(salesSlips.stream().map(SalesSlip::getPartnerId).toList());
		var marketNames = marketNames(salesSlips);
		Map<Long, List<SalesSlipAction>> actionsBySalesSlipId =
				actionResolver.resolveAll(salesSlips);
		return salesSlips.stream()
				.map(salesSlip -> SalesSlipResponse.from(
						salesSlip, partners.get(salesSlip.getPartnerId()),
						salesSlip.getAuctionShipmentId() == null ? null : marketNames.get(salesSlip.getAuctionShipmentId()),
						allocationsByItemId,
						actionsBySalesSlipId.getOrDefault(salesSlip.getId(), List.of())))
				.toList();
	}
}
