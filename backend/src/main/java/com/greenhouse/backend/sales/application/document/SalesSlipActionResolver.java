package com.greenhouse.backend.sales.application.document;

import com.greenhouse.backend.sales.domain.document.SalesSlip;
import com.greenhouse.backend.sales.domain.document.SalesSlipAction;
import com.greenhouse.backend.sales.domain.document.SalesType;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    List<Long> directSalesSlipIds =
        salesSlips.stream()
            .filter(salesSlip -> salesSlip.getSalesType() == SalesType.DIRECT)
            .map(SalesSlip::getId)
            .toList();
    Set<Long> reviews = accounting.findFinancialReviewRequiredIds(directSalesSlipIds);
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
                  reviews.contains(salesSlip.getId())),
              reviews.contains(salesSlip.getId())));
    }
    return actionsBySalesSlipId;
  }

  private List<SalesSlipAction> resolve(
      SalesSlip salesSlip,
      Set<Long> paidSalesSlipIds,
      Set<Long> nonCancelableShipmentIds,
      boolean reviewRequired) {
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
    if (!reviewRequired && salesSlip.canConfirmPayment()) {
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
