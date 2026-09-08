package com.greenhouse.backend.settlement.application;

import com.greenhouse.backend.auction.application.AuctionDataReader;
import com.greenhouse.backend.partner.application.BusinessPartnerReader;
import com.greenhouse.backend.settlement.domain.AuctionSettlement;
import com.greenhouse.backend.settlement.dto.AuctionSettlementResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuctionSettlementResponseAssembler {

	private final BusinessPartnerReader partnerReader;

	private final AuctionDataReader auctionReader;

	public AuctionSettlementResponse assemble(AuctionSettlement settlement) {
		return assembleAll(List.of(settlement)).getFirst();
	}

	public List<AuctionSettlementResponse> assembleAll(List<AuctionSettlement> settlements) {
		var partners = partnerReader
			.getAllInfo(settlements.stream().map(AuctionSettlement::getAuctionHouseId).toList());
		var results = auctionReader.getResults(settlements.stream()
			.flatMap(settlement -> settlement.getLines().stream())
			.map(line -> line.getAuctionResultLineId())
			.toList());
		return settlements.stream()
			.map(settlement -> AuctionSettlementResponse.from(settlement,
					partners.get(settlement.getAuctionHouseId()).name(), results))
			.toList();
	}

}
