package com.greenhouse.backend.sales.application.document;

import com.greenhouse.backend.audit.domain.AuditAction;
import com.greenhouse.backend.sales.api.document.SalesSlipDocument;
import com.greenhouse.backend.sales.api.document.SalesType;
import com.greenhouse.backend.sales.domain.document.SalesSlip;
import com.greenhouse.backend.sales.dto.document.SalesSlipStatusUpdateRequest;
import com.greenhouse.backend.sales.repository.document.SalesSlipRepository;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class SalesSlipStatusService {

  private final DirectDocumentAccountingPort accounting;

  private final SalesSlipRepository salesSlipRepository;

  private final SalesSlipAggregateLoader aggregateLoader;

  private final AuctionSalesSlipCancellationPolicy auctionSalesSlipCancellationPolicy;

  private final SalesSlipInventoryService salesSlipInventoryService;

  private final SalesSlipOutboundService salesSlipOutboundService;

  private final SalesSlipAuditSupport auditSupport;

  private final SalesSlipDocumentAssembler responseAssembler;

  public SalesSlipDocument updateStatus(Long salesSlipId, SalesSlipStatusUpdateRequest request) {
    var salesSlip = aggregateLoader.getForUpdate(salesSlipId);
    String nextStatus = request.salesStatus().trim();
    if (salesSlip.isCanceled()) {
      throw new IllegalArgumentException("취소된 전표는 상태를 변경할 수 없습니다.");
    }
    if (nextStatus.equals(salesSlip.getSalesStatus())) {
      return responseAssembler.assemble(salesSlip);
    }
    Map<String, Object> before = auditSupport.snapshot(salesSlip);
    if (SalesSlip.STATUS_CANCELED.equals(nextStatus)) {
      cancel(salesSlip);
      auditSupport.record(
          AuditAction.DEACTIVATED, salesSlip, before, auditSupport.snapshot(salesSlip));
      return responseAssembler.assemble(salesSlip);
    }
    if (salesSlip.isOutboundCompleted()) {
      throw new IllegalArgumentException("출고 완료된 전표는 판매 상태를 변경할 수 없습니다.");
    }

    salesSlip.updateSalesStatus(nextStatus);
    if (salesSlip.isOutboundCompleted()) {
      salesSlipOutboundService.complete(salesSlip);
    }
    auditSupport.record(AuditAction.UPDATED, salesSlip, before, auditSupport.snapshot(salesSlip));
    return responseAssembler.assemble(salesSlip);
  }

  private void cancel(SalesSlip salesSlip) {
    if (salesSlip.getSalesType() == SalesType.DIRECT
        && accounting.existsPayment(salesSlip.getId())) {
      throw new IllegalArgumentException("입금 이력이 있는 판매 전표는 취소할 수 없습니다.");
    }

    if (salesSlip.isOutboundCompleted()) {
      salesSlipInventoryService.cancelOutbound(salesSlip);
    } else {
      salesSlipInventoryService.cancelReserve(salesSlip);
    }

    if (salesSlip.getSalesType() == SalesType.AUCTION) {
      auctionSalesSlipCancellationPolicy.cancelShipmentIfPossible(salesSlip);
    }

    salesSlip.updateSalesStatus(SalesSlip.STATUS_CANCELED);
    if (salesSlip.getSalesType() == SalesType.DIRECT) {
      accounting.updateReceivable(
          salesSlip.getPartnerId(),
          salesSlipRepository.sumDirectReceivableByPartnerId(salesSlip.getPartnerId()),
          null);
    }
  }
}
