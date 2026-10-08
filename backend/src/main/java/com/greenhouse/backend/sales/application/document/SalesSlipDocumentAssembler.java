package com.greenhouse.backend.sales.application.document;

import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.farm.api.orchid.OrchidGroupQueryApi;
import com.greenhouse.backend.sales.api.document.SalesSlipDocument;
import com.greenhouse.backend.sales.api.document.SalesSlipSummary;
import com.greenhouse.backend.sales.api.document.SalesType;
import com.greenhouse.backend.sales.api.partner.BusinessPartnerQueryApi;
import com.greenhouse.backend.sales.domain.document.SalesSlip;
import com.greenhouse.backend.sales.domain.document.SalesSlipItem;
import com.greenhouse.backend.sales.domain.document.SalesSlipItemAllocation;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SalesSlipDocumentAssembler {

  private final BusinessPartnerQueryApi partnerReader;

  private final AuctionDocumentPort auctionReader;

  private final SalesSlipActionResolver actionResolver;

  private final DirectDocumentAccountingPort accounting;

  private final OrchidGroupQueryApi orchidGroupReader;

  public Page<SalesSlipSummary> assemblePage(Page<SalesSlip> page) {
    var partners = partnerReader.getAllInfo(page.map(SalesSlip::getPartnerId).getContent());
    var marketNames = marketNames(page.getContent());
    var financials = financials(page.getContent());
    return page.map(
        slip ->
            SalesSlipSummaryFactory.from(
                slip,
                partners.get(slip.getPartnerId()),
                slip.getAuctionShipmentId() == null
                    ? null
                    : marketNames.get(slip.getAuctionShipmentId()),
                financials.get(slip.getId())));
  }

  private Map<Long, String> marketNames(List<SalesSlip> slips) {
    return auctionReader.getMarketNames(
        slips.stream()
            .filter(slip -> slip.getAuctionShipmentId() != null)
            .map(slip -> slip.getAuctionShipmentId())
            .distinct()
            .toList());
  }

  public SalesSlipDocument assemble(SalesSlip salesSlip) {
    var allocations =
        salesSlip.getItems().stream()
            .collect(Collectors.toMap(SalesSlipItem::getId, SalesSlipItem::getAllocations));
    return assemble(List.of(salesSlip), allocations).getFirst();
  }

  public List<SalesSlipDocument> assemble(
      List<SalesSlip> salesSlips, Map<Long, List<SalesSlipItemAllocation>> allocationsByItemId) {
    var states =
        orchidGroupReader.getStates(
            allocationsByItemId.values().stream()
                .flatMap(List::stream)
                .map(SalesSlipItemAllocation::getOrchidGroupId)
                .toList());
    var partners =
        partnerReader.getAllInfo(salesSlips.stream().map(SalesSlip::getPartnerId).toList());
    var marketNames = marketNames(salesSlips);
    var financials = financials(salesSlips);
    Map<Long, SalesSlipActionResolver.Actions> actionsBySalesSlipId =
        actionResolver.resolveAll(salesSlips, financials);
    return salesSlips.stream()
        .map(
            salesSlip ->
                SalesSlipDocumentFactory.from(
                    salesSlip,
                    partners.get(salesSlip.getPartnerId()),
                    salesSlip.getAuctionShipmentId() == null
                        ? null
                        : marketNames.get(salesSlip.getAuctionShipmentId()),
                    allocationsByItemId,
                    states,
                    financials.get(salesSlip.getId()),
                    actionsBySalesSlipId
                        .getOrDefault(
                            salesSlip.getId(),
                            new SalesSlipActionResolver.Actions(List.of(), false))
                        .availableActions(),
                    actionsBySalesSlipId
                        .getOrDefault(
                            salesSlip.getId(),
                            new SalesSlipActionResolver.Actions(List.of(), false))
                        .financialReviewRequired()))
        .toList();
  }

  private Map<Long, DirectDocumentAccountingPort.FinancialSnapshot> financials(
      List<SalesSlip> slips) {
    var ids =
        slips.stream()
            .filter(slip -> slip.getSalesType() != SalesType.AUCTION)
            .map(SalesSlip::getId)
            .toList();
    var values = accounting.findFinancials(ids);
    if (!values.keySet().containsAll(ids))
      throw new ConflictException("DIRECT_AMOUNT_SOURCE_MISSING", "일반 판매 금액 자료를 찾을 수 없습니다.");
    return values;
  }
}
