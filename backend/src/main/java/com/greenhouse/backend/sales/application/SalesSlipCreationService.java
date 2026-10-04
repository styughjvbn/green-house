package com.greenhouse.backend.sales.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.greenhouse.backend.audit.domain.AuditAction;
import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.partner.application.BusinessPartnerReader;
import com.greenhouse.backend.partner.domain.PartnerType;
import com.greenhouse.backend.sales.application.command.SalesSlipCommand;
import com.greenhouse.backend.sales.application.document.SalesSlipDocument;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesType;
import com.greenhouse.backend.sales.repository.SalesCreationReceiptRepository;
import com.greenhouse.backend.sales.repository.SalesSlipRepository;
import com.greenhouse.backend.settlement.application.ExpectedPaymentDateCalculator;
import com.greenhouse.backend.settlement.application.PartnerBalanceService;
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

  private final SalesCreationReceiptRepository receiptRepository;
  private final Clock clock;

  private final BusinessPartnerReader partnerReader;

  private final SalesSlipRepository salesSlipRepository;

  private final SalesSlipAllocationFactory salesSlipAllocationFactory;

  private final SalesSlipInventoryService salesSlipInventoryService;

  private final ExpectedPaymentDateCalculator paymentDateCalculator;

  private final SalesSlipNumberGenerator numberGenerator;

  private final PartnerBalanceService partnerBalanceService;

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
    if (request.partnerId() == null) {
      throw new IllegalArgumentException(
          type == SalesType.DIRECT ? "일반 판매는 거래처를 선택해야 합니다." : "경매 판매는 경매장을 선택해야 합니다.");
    }
    if (request.items().isEmpty()) {
      throw new IllegalArgumentException(
          type == SalesType.DIRECT ? "일반 판매 품목은 1개 이상 입력해야 합니다." : "경매 판매는 1개 이상의 lot 품목이 필요합니다.");
    }
    var partner = partnerReader.getActiveInfo(request.partnerId());
    if (type == SalesType.DIRECT && partner.partnerType() == PartnerType.AUCTION_HOUSE) {
      throw new IllegalArgumentException("경매장 거래처는 경매 판매 전표에서 사용해야 합니다.");
    }
    if (type == SalesType.AUCTION && partner.partnerType() != PartnerType.AUCTION_HOUSE) {
      throw new IllegalArgumentException("경매 판매는 경매장 거래처만 선택할 수 있습니다.");
    }
    if (type == SalesType.DIRECT) {
      partnerBalanceService.lockPartners(List.of(partner.id()));
    }

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

    salesSlipAllocationFactory.createItems(request.items()).forEach(salesSlip::addItem);
    if (type == SalesType.DIRECT) {
      salesSlip.updateExpectedPaymentDate(
          paymentDateCalculator.calculate(partner.id(), request.saleDate()));
    }
    var saved = salesSlipRepository.save(salesSlip);
    salesSlipInventoryService.reserve(saved);
    if (saved.isOutboundCompleted()) {
      salesSlipOutboundService.complete(saved);
    }
    if (type == SalesType.DIRECT) {
      partnerBalanceService.updateReceivable(
          partner.id(), salesSlipRepository.sumDirectReceivableByPartnerId(partner.id()), null);
    }
    // Cascaded IDs must be present in both the first response and its stored replay snapshot.
    salesSlipRepository.flush();
    auditSupport.record(AuditAction.CREATED, saved, null, auditSupport.snapshot(saved));
    return responseAssembler.assemble(saved);
  }
}
