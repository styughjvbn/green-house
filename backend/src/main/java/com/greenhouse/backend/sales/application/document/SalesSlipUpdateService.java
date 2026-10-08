package com.greenhouse.backend.sales.application.document;

import com.greenhouse.backend.audit.domain.AuditAction;
import com.greenhouse.backend.sales.application.document.command.SalesSlipCommand;
import com.greenhouse.backend.sales.application.partner.BusinessPartnerReader;
import com.greenhouse.backend.sales.domain.document.SalesSlip;
import com.greenhouse.backend.sales.domain.document.SalesSlipInputPolicy;
import com.greenhouse.backend.sales.domain.document.SalesSlipItem;
import com.greenhouse.backend.sales.domain.document.SalesSlipItemAllocation;
import com.greenhouse.backend.sales.domain.document.SalesType;
import com.greenhouse.backend.sales.domain.partner.PartnerType;
import com.greenhouse.backend.sales.repository.document.SalesSlipRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class SalesSlipUpdateService {

  private final DirectDocumentAccountingPort accounting;

  private final SalesSlipRepository salesSlipRepository;

  private final SalesSlipAggregateLoader aggregateLoader;

  private final BusinessPartnerReader businessPartnerReader;

  private final SalesSlipAllocationFactory salesSlipAllocationFactory;

  private final SalesSlipInventoryService salesSlipInventoryService;

  private final SalesSlipAuditSupport auditSupport;

  private final SalesSlipDocumentAssembler responseAssembler;

  public SalesSlipDocument update(Long salesSlipId, SalesSlipCommand request) {
    SalesSlip salesSlip =
        aggregateLoader.getForUpdate(
            salesSlipId, request.partnerId() == null ? List.of() : List.of(request.partnerId()));
    Long previousPartnerId = salesSlip.getPartnerId();
    Map<String, Object> before = auditSupport.snapshot(salesSlip);

    accounting.requireFinancialReviewCleared(salesSlipId, salesSlip.getPartnerId());
    validateEditable(salesSlip, request);
    SalesSlipInputPolicy.requirePartner(SalesType.DIRECT, request.partnerId());
    SalesSlipInputPolicy.requireItems(SalesType.DIRECT, request.items().size());

    var partner = businessPartnerReader.getActiveInfo(request.partnerId());
    SalesSlipInputPolicy.requirePartnerType(
        SalesType.DIRECT, partner.partnerType() == PartnerType.AUCTION_HOUSE);
    var expectedPaymentDate = accounting.calculate(partner.id(), request.saleDate());

    // Lock old/new allocations together before releasing stock. Per-mutation sorting is too late.
    salesSlipAllocationFactory.lockForReplacement(salesSlip, request.items());

    // Child-only edits need a new identity even when JPA does not increment the slip version.
    UUID editId = UUID.randomUUID();
    salesSlipInventoryService.releaseForEdit(salesSlip, editId);

    var quote =
        accounting.quotePrices(
            request.items().stream()
                .map(
                    item ->
                        new DirectDocumentAccountingPort.Price(
                            null, item.quantity(), item.unitPrice()))
                .toList());
    List<SalesSlipItem> items =
        salesSlipAllocationFactory.createItems(request.items(), quote.prices());
    if (salesSlip.getItems().size() != items.size()) {
      throw new IllegalArgumentException("품목 개수 변경 수정은 아직 지원하지 않습니다.");
    }

    salesSlip.updateDraftInfo(
        request.saleDate(),
        partner.id(),
        SalesTextNormalizer.defaultText(
            request.paymentStatus(), SalesType.DIRECT.defaultPaymentStatus()),
        SalesTextNormalizer.normalize(request.paymentMethod()),
        SalesTextNormalizer.normalize(request.memo()));
    for (int index = 0; index < salesSlip.getItems().size(); index++) {
      var currentItem = salesSlip.getItems().get(index);
      var nextItem = items.get(index);
      currentItem.updateDetails(
          nextItem.getItemName(),
          nextItem.getGenus(),
          nextItem.getSpec(),
          nextItem.getQuantity(),
          nextItem.getUnitPrice(),
          nextItem.getAmount(),
          nextItem.getMemo());
      currentItem.replaceAllocations(
          nextItem.getAllocations().stream().map(SalesSlipItemAllocation::copy).toList());
    }
    salesSlip.applyFinancialProjection(
        quote.totalAmount(),
        expectedPaymentDate,
        salesSlip.getPaymentMethod(),
        0L,
        quote.totalAmount().longValue(),
        salesSlip.getPaymentStatus());
    salesSlip.updateExpectedPaymentDate(expectedPaymentDate);
    salesSlipRepository.saveAndFlush(salesSlip);
    accounting.storeTerms(
        new DirectDocumentAccountingPort.Terms(
            salesSlip.getId(),
            salesSlip.getPartnerId(),
            salesSlip.getSaleDate(),
            salesSlip.getExpectedPaymentDate(),
            salesSlip.getPaymentMethod(),
            salesSlip.getPaymentStatus(),
            salesSlip.getItems().stream()
                .map(
                    item ->
                        new DirectDocumentAccountingPort.Price(
                            item.getId(), item.getQuantity(), item.getUnitPrice()))
                .toList()));
    salesSlipInventoryService.reserveForEdit(salesSlip, editId);
    accounting.updateReceivable(
        partner.id(), salesSlipRepository.sumDirectReceivableByPartnerId(partner.id()), null);
    if (!previousPartnerId.equals(partner.id())) {
      accounting.updateReceivable(
          previousPartnerId,
          salesSlipRepository.sumDirectReceivableByPartnerId(previousPartnerId),
          null);
    }
    auditSupport.record(AuditAction.UPDATED, salesSlip, before, auditSupport.snapshot(salesSlip));

    return responseAssembler.assemble(salesSlip);
  }

  private void validateEditable(SalesSlip salesSlip, SalesSlipCommand request) {
    if (request.salesType() == SalesType.AUCTION) {
      throw new IllegalArgumentException("경매 판매 전표 수정은 아직 지원하지 않습니다.");
    }
    salesSlip.requireEditable(accounting.existsPayment(salesSlip.getId()));
  }
}
