package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.auction.application.AuctionDataReader;
import com.greenhouse.backend.farm.application.orchid.OrchidGroupReader;
import com.greenhouse.backend.partner.application.BusinessPartnerReader;
import com.greenhouse.backend.sales.application.document.SalesSlipDocument;
import com.greenhouse.backend.sales.application.document.SalesSlipSummary;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesSlipAction;
import com.greenhouse.backend.sales.domain.SalesSlipItem;
import com.greenhouse.backend.sales.domain.SalesSlipItemAllocation;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SalesSlipDocumentAssembler {

	private final BusinessPartnerReader partnerReader;
	private final AuctionDataReader auctionReader;
	private final SalesSlipActionResolver actionResolver;
	private final OrchidGroupReader orchidGroupReader;

	public Page<SalesSlipSummary> assemblePage(Page<SalesSlip> page) {
		var partners = partnerReader.getAllInfo(page.map(SalesSlip::getPartnerId).getContent());
		var marketNames = marketNames(page.getContent());
		return page.map(slip -> SalesSlipSummary.from(slip, partners.get(slip.getPartnerId()),
				slip.getAuctionShipmentId() == null ? null : marketNames.get(slip.getAuctionShipmentId())));
	}

	private Map<Long, String> marketNames(List<SalesSlip> slips) {
		return auctionReader.getMarketNames(slips.stream().filter(slip -> slip.getAuctionShipmentId() != null)
				.map(slip -> slip.getAuctionShipmentId()).distinct().toList());
	}

	public SalesSlipDocument assemble(SalesSlip salesSlip) {
		var allocations = salesSlip.getItems().stream()
				.collect(Collectors.toMap(SalesSlipItem::getId, SalesSlipItem::getAllocations));
		return assemble(List.of(salesSlip), allocations).getFirst();
	}

	public List<SalesSlipDocument> assemble(
			List<SalesSlip> salesSlips,
			Map<Long, List<SalesSlipItemAllocation>> allocationsByItemId) {
		var states = orchidGroupReader.getStates(allocationsByItemId.values().stream()
				.flatMap(List::stream).map(SalesSlipItemAllocation::getOrchidGroupId).toList());
		var partners = partnerReader.getAllInfo(salesSlips.stream().map(SalesSlip::getPartnerId).toList());
		var marketNames = marketNames(salesSlips);
		Map<Long, List<SalesSlipAction>> actionsBySalesSlipId =
				actionResolver.resolveAll(salesSlips);
		return salesSlips.stream()
				.map(salesSlip -> SalesSlipDocument.from(
						salesSlip, partners.get(salesSlip.getPartnerId()),
						salesSlip.getAuctionShipmentId() == null ? null : marketNames.get(salesSlip.getAuctionShipmentId()),
						allocationsByItemId, states,
						actionsBySalesSlipId.getOrDefault(salesSlip.getId(), List.of())))
				.toList();
	}
}
