package com.greenhouse.backend.sales.application.auction.settlement;

import com.greenhouse.backend.sales.application.auction.AuctionDataReader;
import com.greenhouse.backend.sales.application.partner.BusinessPartnerReader;
import com.greenhouse.backend.sales.domain.auction.settlement.AuctionSettlement;
import com.greenhouse.backend.sales.dto.auction.settlement.AuctionSettlementResponse;
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
    var partners =
        partnerReader.getAllInfo(
            settlements.stream().map(AuctionSettlement::getAuctionHouseId).toList());
    var results =
        auctionReader.getResults(
            settlements.stream()
                .flatMap(settlement -> settlement.getLines().stream())
                .map(line -> line.getAuctionResultLineId())
                .toList());
    return settlements.stream()
        .map(
            settlement ->
                AuctionSettlementResponse.from(
                    settlement, partners.get(settlement.getAuctionHouseId()).name(), results))
        .toList();
  }
}
