package com.greenhouse.backend.sales.payment.application;

import com.greenhouse.backend.common.api.PageRequests;
import com.greenhouse.backend.common.api.PageResponse;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.sales.payment.api.PaymentAllocationTargetOption;
import com.greenhouse.backend.sales.payment.api.PaymentTargetType;
import com.greenhouse.backend.sales.payment.domain.*;
import com.greenhouse.backend.sales.payment.repository.*;
import com.greenhouse.backend.sales.payment.spi.PaymentAllocationTargetPort;
import com.greenhouse.backend.sales.payment.web.dto.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentReceiptReader {
  private final PartnerPaymentEventRepository events;
  private final PaymentReceiptIntegrity cash;
  private final List<PaymentAllocationTargetPort<?>> targets;

  public PaymentAllocationMetadata metadata() {
    return new PaymentAllocationMetadata(
        targets.stream().map(PaymentAllocationTargetPort::targetType).distinct().sorted().toList());
  }

  public PageResponse<PaymentReceiptResponse> page(Long partnerId, int page, int size) {
    var roots =
        events.search(
            partnerId,
            null,
            null,
            PaymentEventType.PAYMENT_RECEIVED,
            PageRequests.clamped(page, size));
    var states = cash.findAll(roots.stream().map(PartnerPaymentEvent::getId).toList());
    return PageResponse.from(roots.map(event -> response(event, states.get(event.getId()))));
  }

  public PaymentReceiptResponse get(Long partnerId, Long id) {
    var root = receipt(partnerId, id);
    return response(root, cash.findAll(List.of(id)).get(id));
  }

  public PageResponse<PaymentAllocationResponse> allocations(
      Long partnerId, Long receiptId, int page, int size) {
    var root = receipt(partnerId, receiptId);
    var state = cash.findAll(List.of(receiptId)).get(receiptId);
    return PageResponse.from(
        events
            .findAllocationPage(partnerId, receiptId, PageRequests.clamped(page, size))
            .map(
                event ->
                    PaymentAllocationResponse.from(
                        event,
                        state != null
                            && !state.reviewRequired()
                            && root.getStatus() != PaymentEventStatus.CANCELLED
                            && event.getStatus() == PaymentEventStatus.CONFIRMED)));
  }

  public PageResponse<PaymentAllocationTargetOption> options(
      Long partnerId, PaymentTargetType type, String keyword, int page, int size) {
    var paging = PageRequests.clamped(page, size);
    var port =
        targets.stream()
            .filter(value -> value.targetType() == type)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 배분 대상입니다."));
    return port.allocationOptions(
        partnerId,
        keyword == null ? "" : keyword.trim(),
        paging.getPageNumber(),
        paging.getPageSize());
  }

  private PartnerPaymentEvent receipt(Long partnerId, Long id) {
    return events
        .findById(id)
        .filter(
            event ->
                partnerId.equals(event.getPartnerId())
                    && event.getEventType() == PaymentEventType.PAYMENT_RECEIVED)
        .orElseThrow(() -> new NotFoundException("거래처의 수납을 찾을 수 없습니다."));
  }

  private PaymentReceiptResponse response(
      PartnerPaymentEvent event, PaymentReceiptQueryRepository.State state) {
    return PaymentReceiptResponse.from(
        event,
        state == null ? 0 : state.availableAmount(),
        event.getStatus() != PaymentEventStatus.CANCELLED
            && (state == null || state.reviewRequired()));
  }
}
