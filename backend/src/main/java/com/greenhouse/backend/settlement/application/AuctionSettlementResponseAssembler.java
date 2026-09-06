package com.greenhouse.backend.settlement.application;

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

	public AuctionSettlementResponse assemble(AuctionSettlement settlement) {
		return AuctionSettlementResponse.from(settlement,
				partnerReader.getInfo(settlement.getAuctionHouseId()).name());
	}

	public List<AuctionSettlementResponse> assembleAll(List<AuctionSettlement> settlements) {
		var partners = partnerReader.getAllInfo(settlements.stream().map(AuctionSettlement::getAuctionHouseId).toList());
		return settlements.stream().map(settlement -> AuctionSettlementResponse.from(
				settlement, partners.get(settlement.getAuctionHouseId()).name())).toList();
	}
}
