package com.greenhouse.backend.sales.direct.application;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.greenhouse.backend.audit.application.AuditEventWriter;
import com.greenhouse.backend.audit.domain.AuditAction;
import com.greenhouse.backend.audit.domain.AuditSource;
import com.greenhouse.backend.common.api.PageRequests;
import com.greenhouse.backend.common.api.PageResponse;
import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.sales.direct.domain.*;
import com.greenhouse.backend.sales.direct.repository.*;
import com.greenhouse.backend.sales.direct.web.dto.*;
import com.greenhouse.backend.sales.document.api.DirectDocumentChangeApi;
import com.greenhouse.backend.sales.document.api.DocumentPaymentApi;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DirectSaleChangeService {
  private static final JsonMapper MAPPER =
      JsonMapper.builder()
          .findAndAddModules()
          .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .build();
  private final DirectSaleRepository sales;
  private final DirectSaleChangeRepository changes;
  private final DirectSaleFinancialReader financials;
  private final DirectSaleReviewReader reviews;
  private final DirectDocumentChangeApi documents;
  private final DocumentPaymentApi projections;
  private final AuditEventWriter audit;
  private final Clock clock;

  public DirectSaleChangeResponse get(Long id, int page, int size) {
    PageRequests.validate(page, size);
    var target = documents.find(id);
    var sale = sale(id);
    var history = changes.findByDocumentIdInOrderByIdAsc(List.of(id));
    var ledger = new DirectSaleChangeLedger(sale.getPrices(), history);
    var financial = financials.findAll(List.of(id)).get(id);
    boolean allowed =
        target.changeAllowed()
            && !financial.reviewRequired()
            && target.partnerId().equals(sale.getPartnerId());
    var items = target.items().stream().collect(Collectors.toMap(i -> i.id(), i -> i));
    var portions = toPortions(ledger.portions(), items);
    var returnedPortions = toPortions(ledger.returnedPortions(), items);
    var reversed = new ArrayList<>(history);
    Collections.reverse(reversed);
    long start = (long) page * size;
    var content =
        reversed.stream()
            .skip(start)
            .limit(size)
            .map(
                c -> {
                  var item = items.get(c.getItemId());
                  return new DirectSaleChangeResponse.History(
                      c.getId(),
                      c.getItemId(),
                      item.name(),
                      c.getType(),
                      c.getSourceChangeId(),
                      c.getCanceledChangeId(),
                      c.getQuantity(),
                      c.getPreviousUnitPrice(),
                      c.getNextUnitPrice(),
                      c.getAmountDelta(),
                      c.getChangeDate(),
                      c.getReason(),
                      c.getCreatedAt(),
                      ledger.isCanceled(c.getId()),
                      allowed && ledger.canCancel(c.getId()));
                })
            .toList();
    return new DirectSaleChangeResponse(
        id,
        sale.getPrices().stream().mapToLong(p -> p.getAmount()).sum(),
        sale.getTotalAmount(),
        financial.reviewRequired()
            ? null
            : Math.max(0, financial.allocatedAmount().longValueExact() - sale.getTotalAmount()),
        allowed && !portions.isEmpty()
            ? List.of(DirectSaleChangeType.PRICE_ADJUSTMENT, DirectSaleChangeType.RETURN)
            : List.of(),
        portions,
        returnedPortions,
        new PageResponse<>(
            content, page, size, history.size(), (history.size() + size - 1) / size));
  }

  private List<DirectSaleChangeResponse.Portion> toPortions(
      List<DirectSaleChangeLedger.Portion> portions,
      Map<Long, DirectDocumentChangeApi.Item> items) {
    return portions.stream()
        .map(
            portion -> {
              var item = items.get(portion.itemId());
              return new DirectSaleChangeResponse.Portion(
                  portion.itemId(),
                  item.name(),
                  item.spec(),
                  portion.sourceChangeId(),
                  portion.quantity(),
                  portion.unitPrice(),
                  (long) portion.quantity() * portion.unitPrice());
            })
        .toList();
  }

  @Transactional
  public DirectSaleChangeResult change(
      Long id, DirectSaleChangeType type, DirectSaleChangeRequest request, String key) {
    String fingerprint = fingerprint(type, request);
    var target = documents.lock(id);
    sales.findForUpdate(id).orElseThrow(() -> missing());
    var replay = replay(id, key, fingerprint);
    if (replay.isPresent()) return replay.get();
    requireTarget(target.changeAllowed(), request.changeDate(), target.saleDate());
    var financial = reviews.requireClear(id, target.partnerId());
    var sale = sale(id);
    var history = changes.findByDocumentIdInOrderByIdAsc(List.of(id));
    var ledger = new DirectSaleChangeLedger(sale.getPrices(), history);
    var plan =
        ledger.plan(
            type,
            request.itemId(),
            request.sourceChangeId(),
            request.quantity(),
            request.unitPrice());
    sale.applyChangedTotal(plan.totalAfter());
    var event =
        changes.saveAndFlush(
            new DirectSaleChange(
                id,
                request.itemId(),
                type,
                request.sourceChangeId(),
                null,
                request.quantity(),
                plan.previousUnitPrice(),
                plan.nextUnitPrice(),
                plan.delta(),
                financial.allocatedAmount().longValueExact(),
                request.changeDate(),
                request.reason().trim(),
                validKey(key),
                fingerprint,
                TimeConfig.utcNow(clock)));
    finish(sale, event, financial.totalAmount());
    return new DirectSaleChangeResult(event.getId());
  }

  @Transactional
  public DirectSaleChangeResult cancel(
      Long id, Long changeId, CancelDirectSaleChangeRequest request, String key) {
    String fingerprint = fingerprint("CANCEL:" + changeId, request);
    var target = documents.lock(id);
    sales.findForUpdate(id).orElseThrow(() -> missing());
    var replay = replay(id, key, fingerprint);
    if (replay.isPresent()) return replay.get();
    requireTarget(target.changeAllowed(), request.changeDate(), target.saleDate());
    var financial = reviews.requireClear(id, target.partnerId());
    var sale = sale(id);
    var history = changes.findByDocumentIdInOrderByIdAsc(List.of(id));
    var ledger = new DirectSaleChangeLedger(sale.getPrices(), history);
    var original =
        history.stream()
            .filter(c -> c.getId().equals(changeId))
            .findFirst()
            .orElseThrow(() -> missing());
    var plan = ledger.cancellationPlan(changeId);
    if (request.changeDate().isBefore(original.getChangeDate()))
      throw new IllegalArgumentException("취소일은 원래 처리일 이후여야 합니다.");
    sale.applyChangedTotal(plan.totalAfter());
    var event =
        changes.saveAndFlush(
            new DirectSaleChange(
                id,
                original.getItemId(),
                DirectSaleChangeType.CANCEL,
                original.getSourceChangeId(),
                changeId,
                original.getQuantity(),
                original.getPreviousUnitPrice(),
                original.getNextUnitPrice(),
                -original.getAmountDelta(),
                financial.allocatedAmount().longValueExact(),
                request.changeDate(),
                request.reason().trim(),
                validKey(key),
                fingerprint,
                TimeConfig.utcNow(clock)));
    finish(sale, event, financial.totalAmount());
    return new DirectSaleChangeResult(event.getId());
  }

  private void finish(DirectSale sale, DirectSaleChange event, int previousTotal) {
    sales.flush();
    projections.updateBalance(sale.getDocumentId(), null);
    audit.record(
        AuditAction.UPDATED,
        AuditSource.SALES_MANAGEMENT,
        "DIRECT_SALE",
        sale.getDocumentId(),
        Map.of("totalAmount", previousTotal),
        Map.of("totalAmount", sale.getTotalAmount(), "changeId", event.getId()),
        Map.of("operation", event.getType(), "itemId", event.getItemId()));
  }

  private Optional<DirectSaleChangeResult> replay(Long id, String key, String fingerprint) {
    return changes
        .findByDocumentIdAndRequestKey(id, validKey(key))
        .map(
            event -> {
              if (!event.getFingerprint().equals(fingerprint))
                throw new ConflictException(
                    "DIRECT_CHANGE_REQUEST_KEY_CONFLICT", "같은 요청 키의 입력이 다릅니다.");
              return new DirectSaleChangeResult(event.getId());
            });
  }

  private DirectSale sale(Long id) {
    return sales.findAllWithPrices(List.of(id)).stream().findFirst().orElseThrow(() -> missing());
  }

  private static void requireTarget(boolean allowed, LocalDate date, LocalDate saleDate) {
    if (!allowed)
      throw new ConflictException(
          "DIRECT_CHANGE_COMPLETED_REQUIRED", "출고 완료된 일반 판매 전표에서 처리해야 합니다.");
    if (date.isBefore(saleDate)) throw new IllegalArgumentException("처리일은 판매일 이후여야 합니다.");
  }

  private static String validKey(String key) {
    if (key == null || key.isBlank() || key.length() > 100)
      throw new IllegalArgumentException("요청 키는 1~100자여야 합니다.");
    return key;
  }

  private static String fingerprint(Object type, Object request) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(MAPPER.writeValueAsBytes(List.of(type, request))));
    } catch (Exception e) {
      throw new IllegalStateException("요청 지문을 만들 수 없습니다.", e);
    }
  }

  private static NotFoundException missing() {
    return new NotFoundException("일반 판매 금액 또는 변경 이력을 찾을 수 없습니다.");
  }
}
