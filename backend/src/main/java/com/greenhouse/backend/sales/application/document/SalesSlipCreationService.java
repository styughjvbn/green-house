package com.greenhouse.backend.sales.application.document;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.greenhouse.backend.audit.domain.AuditAction;
import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.sales.api.document.SalesSlipDocument;
import com.greenhouse.backend.sales.api.document.SalesType;
import com.greenhouse.backend.sales.api.partner.BusinessPartnerQueryApi;
import com.greenhouse.backend.sales.api.partner.PartnerType;
import com.greenhouse.backend.sales.application.document.command.SalesSlipCommand;
import com.greenhouse.backend.sales.document.spi.DirectDocumentAccountingPort;
import com.greenhouse.backend.sales.domain.document.SalesSlip;
import com.greenhouse.backend.sales.domain.document.SalesSlipInputPolicy;
import com.greenhouse.backend.sales.repository.document.SalesCreationReceiptRepository;
import com.greenhouse.backend.sales.repository.document.SalesSlipRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class SalesSlipCreationService {
  private static final JsonMapper RECEIPT_MAPPER =
      JsonMapper.builder()
          .findAndAddModules()
          .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .build();

  private final DirectDocumentAccountingPort accounting;

  private final SalesCreationReceiptRepository receiptRepository;
  private final Clock clock;

  private final BusinessPartnerQueryApi partnerReader;

  private final SalesSlipRepository salesSlipRepository;

  private final SalesSlipAllocationFactory salesSlipAllocationFactory;

  private final SalesSlipInventoryService salesSlipInventoryService;

  private final SalesSlipNumberGenerator numberGenerator;

  private final SalesSlipOutboundService salesSlipOutboundService;

  private final SalesSlipDocumentAssembler responseAssembler;

  private final SalesSlipAuditSupport auditSupport;

  public SalesSlipDocument create(SalesSlipCommand request) {
    return createNew(request);
  }

  public SalesSlipDocument create(SalesSlipCommand request, String idempotencyKey) {
    if (idempotencyKey == null) {
      return createNew(request);
    }
    if (idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
      throw new IllegalArgumentException("판매 생성 요청 키는 1~100자의 공백이 아닌 값이 필요합니다.");
    }
    String fingerprint = fingerprint(request);
    int inserted = receiptRepository.claim(idempotencyKey, fingerprint, TimeConfig.utcNow(clock));
    var receipt = receiptRepository.findForUpdate(idempotencyKey);
    receipt.validate(fingerprint);
    try {
      if (receipt.getResponseSnapshot() != null) {
        return RECEIPT_MAPPER.readValue(receipt.getResponseSnapshot(), SalesSlipDocument.class);
      }
      if (inserted != 1) {
        throw new IllegalStateException("완료되지 않은 판매 생성 요청 기록입니다.");
      }
      var response = createNew(request);
      receipt.complete(response.id(), RECEIPT_MAPPER.writeValueAsString(response));
      receiptRepository.flush();
      return response;
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("판매 생성 요청의 저장된 응답을 처리할 수 없습니다.", exception);
    }
  }

  private String fingerprint(SalesSlipCommand request) {
    try {
      // Typed property order is canonical; array order and null/default distinctions are retained.
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(
                      RECEIPT_MAPPER.writeValueAsBytes(SalesCreationRequestPayload.from(request))));
    } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
      throw new IllegalStateException("판매 생성 요청의 지문을 계산할 수 없습니다.", exception);
    }
  }

  private SalesSlipDocument createNew(SalesSlipCommand request) {
    SalesType type = request.salesType() == null ? SalesType.DIRECT : request.salesType();
    SalesSlipInputPolicy.requirePartner(type, request.partnerId());
    SalesSlipInputPolicy.requireItems(type, request.items().size());
    var partner = partnerReader.getActiveInfo(request.partnerId());
    SalesSlipInputPolicy.requirePartnerType(
        type, partner.partnerType() == PartnerType.AUCTION_HOUSE);
    accounting.lockPartners(List.of(partner.id()));

    var salesSlip =
        new SalesSlip(
            numberGenerator.generate(request.saleDate(), type),
            request.saleDate(),
            type,
            null,
            partner.id(),
            SalesTextNormalizer.defaultText(request.paymentStatus(), type.defaultPaymentStatus()),
            SalesTextNormalizer.defaultText(request.salesStatus(), "작성중"),
            SalesTextNormalizer.defaultText(request.paymentMethod(), type.defaultPaymentMethod()),
            SalesTextNormalizer.normalize(request.memo()));

    var quote =
        type == SalesType.DIRECT
            ? accounting.quotePrices(
                request.items().stream()
                    .map(
                        item ->
                            new DirectDocumentAccountingPort.Price(
                                null, item.quantity(), item.unitPrice()))
                    .toList())
            : null;
    salesSlipAllocationFactory
        .createItems(request.items(), quote == null ? null : quote.prices())
        .forEach(salesSlip::addItem);
    if (quote != null)
      salesSlip.applyFinancialProjection(
          quote.totalAmount(),
          salesSlip.getExpectedPaymentDate(),
          salesSlip.getPaymentMethod(),
          0L,
          quote.totalAmount().longValue(),
          salesSlip.getPaymentStatus());
    if (type == SalesType.DIRECT) {
      salesSlip.updateExpectedPaymentDate(accounting.calculate(partner.id(), request.saleDate()));
    }
    var saved = salesSlipRepository.save(salesSlip);
    if (type == SalesType.DIRECT) {
      // The Direct foreign keys reference the stable document/item IDs from this transaction.
      salesSlipRepository.flush();
      accounting.storeTerms(
          new DirectDocumentAccountingPort.Terms(
              saved.getId(),
              saved.getPartnerId(),
              saved.getSaleDate(),
              saved.getExpectedPaymentDate(),
              saved.getPaymentMethod(),
              saved.getPaymentStatus(),
              saved.getItems().stream()
                  .map(
                      item ->
                          new DirectDocumentAccountingPort.Price(
                              item.getId(), item.getQuantity(), item.getUnitPrice()))
                  .toList()));
    }
    salesSlipInventoryService.reserve(saved);
    if (saved.isOutboundCompleted()) {
      salesSlipOutboundService.complete(saved);
    }
    if (type == SalesType.DIRECT) {
      accounting.updateReceivable(
          partner.id(), salesSlipRepository.sumDirectReceivableByPartnerId(partner.id()), null);
    }
    // Cascaded IDs must be present in both the first response and its stored replay snapshot.
    salesSlipRepository.flush();
    auditSupport.record(AuditAction.CREATED, saved, null, auditSupport.snapshot(saved));
    return responseAssembler.assemble(saved);
  }
}
