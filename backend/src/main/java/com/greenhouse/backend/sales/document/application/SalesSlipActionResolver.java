package com.greenhouse.backend.sales.document.application;

import com.greenhouse.backend.sales.api.document.SalesSlipAction;
import com.greenhouse.backend.sales.api.document.SalesType;
import com.greenhouse.backend.sales.document.domain.SalesSlip;
import com.greenhouse.backend.sales.document.spi.DirectDocumentAccountingPort;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SalesSlipActionResolver {

  private final DirectDocumentAccountingPort accounting;

  private final AuctionSalesSlipCancellationPolicy auctionCancellationPolicy;

  public List<SalesSlipAction> resolve(SalesSlip salesSlip) {
    return resolveAll(List.of(salesSlip))
        .getOrDefault(salesSlip.getId(), new Actions(List.of(), false))
        .availableActions();
  }

  public Map<Long, Actions> resolveAll(List<SalesSlip> salesSlips) {
    return resolveAll(
        salesSlips,
        accounting.findFinancials(
            salesSlips.stream()
                .filter(slip -> slip.getSalesType() == SalesType.DIRECT)
                .map(SalesSlip::getId)
                .toList()));
  }

  public Map<Long, Actions> resolveAll(
      List<SalesSlip> salesSlips,
      Map<Long, DirectDocumentAccountingPort.FinancialSnapshot> financials) {
    List<Long> directSalesSlipIds =
        salesSlips.stream()
            .filter(salesSlip -> salesSlip.getSalesType() == SalesType.DIRECT)
            .map(SalesSlip::getId)
            .toList();
    Set<Long> reviews =
        financials.values().stream()
            .filter(DirectDocumentAccountingPort.FinancialSnapshot::reviewRequired)
            .map(DirectDocumentAccountingPort.FinancialSnapshot::documentId)
            .collect(Collectors.toSet());
    for (var slip : salesSlips) {
      var financial = financials.get(slip.getId());
      if (financial != null && !Objects.equals(financial.partnerId(), slip.getPartnerId()))
        reviews.add(slip.getId());
    }
    Set<Long> paidSalesSlipIds = accounting.findPaidDocumentIds(directSalesSlipIds);
    List<Long> auctionShipmentIds =
        salesSlips.stream()
            .filter(salesSlip -> salesSlip.getSalesType() == SalesType.AUCTION)
            .filter(salesSlip -> salesSlip.getAuctionShipmentId() != null)
            .map(salesSlip -> salesSlip.getAuctionShipmentId())
            .toList();
    Set<Long> nonCancelableShipmentIds =
        auctionCancellationPolicy.findNonCancelableShipmentIds(auctionShipmentIds);

    Map<Long, Actions> actionsBySalesSlipId = new LinkedHashMap<>();
    for (SalesSlip salesSlip : salesSlips) {
      actionsBySalesSlipId.put(
          salesSlip.getId(),
          new Actions(
              resolve(
                  salesSlip,
                  paidSalesSlipIds,
                  nonCancelableShipmentIds,
                  reviews.contains(salesSlip.getId()),
                  financials.get(salesSlip.getId())),
              reviews.contains(salesSlip.getId())));
    }
    return actionsBySalesSlipId;
  }

  private List<SalesSlipAction> resolve(
      SalesSlip salesSlip,
      Set<Long> paidSalesSlipIds,
      Set<Long> nonCancelableShipmentIds,
      boolean reviewRequired,
      DirectDocumentAccountingPort.FinancialSnapshot financial) {
    if (salesSlip.isCanceled()) {
      return List.of();
    }

    EnumSet<SalesSlipAction> actions = EnumSet.noneOf(SalesSlipAction.class);
    boolean hasPaymentEvent = paidSalesSlipIds.contains(salesSlip.getId());

    if (!reviewRequired && salesSlip.canEdit(hasPaymentEvent)) {
      actions.add(SalesSlipAction.EDIT);
    }
    if (salesSlip.canComplete()) {
      actions.add(SalesSlipAction.COMPLETE);
    }
    if (canCancel(salesSlip, hasPaymentEvent, nonCancelableShipmentIds)) {
      actions.add(SalesSlipAction.CANCEL);
    }
    if (!reviewRequired
        && financial != null
        && financial.paymentAllowed()
        && salesSlip.getSalesType() == SalesType.DIRECT) {
      actions.add(SalesSlipAction.CONFIRM_PAYMENT);
    }

    return List.copyOf(actions);
  }

  public record Actions(List<SalesSlipAction> availableActions, boolean financialReviewRequired) {}

  private boolean canCancel(
      SalesSlip salesSlip, boolean hasPaymentEvent, Set<Long> nonCancelableShipmentIds) {
    if (salesSlip.getSalesType() == SalesType.DIRECT) {
      return !hasPaymentEvent;
    }
    return salesSlip.getAuctionShipmentId() == null
        || !nonCancelableShipmentIds.contains(salesSlip.getAuctionShipmentId());
  }
}
