package com.greenhouse.backend.sales.document.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.greenhouse.backend.audit.domain.AuditAction;
import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.sales.api.document.SalesSlipDocument;
import com.greenhouse.backend.sales.api.document.SalesType;
import com.greenhouse.backend.sales.api.partner.BusinessPartnerQueryApi;
import com.greenhouse.backend.sales.api.partner.PartnerType;
import com.greenhouse.backend.sales.document.application.command.SalesInitialReceiptInput;
import com.greenhouse.backend.sales.document.application.command.SalesSlipCommand;
import com.greenhouse.backend.sales.document.domain.SalesSlip;
import com.greenhouse.backend.sales.document.domain.SalesSlipInputPolicy;
import com.greenhouse.backend.sales.document.domain.SalesSlipPhoto;
import com.greenhouse.backend.sales.document.repository.SalesCreationReceiptRepository;
import com.greenhouse.backend.sales.document.repository.SalesSlipPhotoRepository;
import com.greenhouse.backend.sales.document.repository.SalesSlipRepository;
import com.greenhouse.backend.sales.document.spi.DirectCreationReceiptPort;
import com.greenhouse.backend.sales.document.spi.DirectDocumentAccountingPort;
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
          .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
          .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .build();

  private final DirectDocumentAccountingPort accounting;
  private final DirectCreationReceiptPort initialReceipts;

  private final SalesCreationReceiptRepository receiptRepository;
  private final Clock clock;

  private final BusinessPartnerQueryApi partnerReader;

  private final SalesSlipRepository salesSlipRepository;
  private final SalesSlipPhotoRepository photoRepository;

  private final SalesSlipAllocationFactory salesSlipAllocationFactory;

  private final SalesSlipInventoryService salesSlipInventoryService;

  private final SalesSlipOutboundService salesSlipOutboundService;

  private final SalesSlipDocumentAssembler responseAssembler;

  private final SalesSlipAuditSupport auditSupport;

  static SalesSlipDocument readStoredResponse(String snapshot) throws JsonProcessingException {
    var source = RECEIPT_MAPPER.readTree(snapshot);
    for (var item : source.path("items")) {
      if (item instanceof ObjectNode row && row.path("unit").isTextual()) {
        String raw = row.path("unit").asText().trim();
        Integer unit = null;
        if (raw.matches("[0-9]+")) {
          try {
            int parsed = Integer.parseInt(raw);
            if (parsed > 0) unit = parsed;
          } catch (NumberFormatException ignored) {
            // Historical text stays in the receipt; only this response projection is numeric.
          }
        }
        if (unit == null) row.putNull("unit");
        else row.put("unit", unit);
      }
    }
    return RECEIPT_MAPPER.treeToValue(source, SalesSlipDocument.class);
  }

  public SalesSlipDocument create(SalesSlipCommand request) {
    return create(request, null);
  }

  public SalesSlipDocument create(SalesSlipCommand request, String idempotencyKey) {
    if (idempotencyKey == null) {
      if (Boolean.TRUE.equals(request.recordOnly()))
        throw new IllegalArgumentException("기록만 등록에는 생성 요청 키가 필요합니다.");
      if (request.initialReceipt() != null)
        throw new IllegalArgumentException("수납 함께 기록에는 생성 요청 키가 필요합니다.");
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
        return readStoredResponse(receipt.getResponseSnapshot());
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
    var photo = request.sourcePhoto();
    byte[] photoBytes =
        photo == null
            ? null
            : SalesSlipPhoto.validate(photo.fileName(), photo.contentType(), photo.contentBase64());
    SalesType type = request.salesType() == null ? SalesType.DIRECT : request.salesType();
    SalesSlipInputPolicy.requireCreationReceipt(
        type, request.initialReceipt() != null, request.paymentStatus());
    SalesSlipInputPolicy.requirePartner(type, request.partnerId());
    SalesSlipInputPolicy.requireItems(type, request.items().size());
    boolean recordOnly = Boolean.TRUE.equals(request.recordOnly());
    if (recordOnly)
      SalesSlipInputPolicy.requireCompletedRecord(
          type,
          request.salesStatus(),
          request.auctionShipmentId(),
          request.items().stream()
              .anyMatch(item -> item.allocations() != null && !item.allocations().isEmpty()));
    var partner = partnerReader.getActiveInfo(request.partnerId());
    SalesSlipInputPolicy.requirePartnerType(
        type, partner.partnerType() == PartnerType.AUCTION_HOUSE);
    accounting.lockPartners(List.of(partner.id()));

    var salesSlip =
        new SalesSlip(
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
    var prices = quote == null ? null : quote.prices();
    var items =
        recordOnly
            ? salesSlipAllocationFactory.createRecordedItems(request.items(), prices)
            : salesSlipAllocationFactory.createItems(request.items(), prices);
    items.forEach(salesSlip::addItem);
    if (quote != null)
      salesSlip.applyFinancialProjection(
          quote.totalAmount(),
          salesSlip.getExpectedPaymentDate(),
          salesSlip.getPaymentMethod(),
          0L,
          quote.totalAmount().longValue(),
          salesSlip.getPaymentStatus());
    if (type == SalesType.AUCTION) {
      // Shipment item prices are not auction result amounts or receivables.
      salesSlip.applyFinancialProjection(
          0,
          salesSlip.getExpectedPaymentDate(),
          salesSlip.getPaymentMethod(),
          0L,
          0L,
          salesSlip.getPaymentStatus());
    }
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
    if (recordOnly) {
      salesSlipRepository.flush();
      salesSlipOutboundService.createAuctionShipment(saved);
    } else {
      salesSlipInventoryService.reserve(saved);
      if (saved.isOutboundCompleted()) salesSlipOutboundService.complete(saved);
    }
    if (type == SalesType.DIRECT) {
      accounting.updateReceivable(
          partner.id(), salesSlipRepository.sumDirectReceivableByPartnerId(partner.id()), null);
    }
    if (photo != null)
      photoRepository.save(
          new SalesSlipPhoto(
              saved.getId(),
              photo.fileName(),
              photo.contentType(),
              photoBytes,
              TimeConfig.utcNow(clock)));
    // Cascaded IDs must be present in both the first response and its stored replay snapshot.
    salesSlipRepository.flush();
    auditSupport.record(AuditAction.CREATED, saved, null, auditSupport.snapshot(saved));
    if (request.initialReceipt() != null) {
      var receipt = request.initialReceipt();
      initialReceipts.collect(
          saved.getId(),
          receipt.amount(),
          receipt.paymentDate(),
          receipt.method() == SalesInitialReceiptInput.Method.CASH);
    }
    return responseAssembler.assemble(saved);
  }
}
