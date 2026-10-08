package com.greenhouse.backend.sales.application.payment;

import com.greenhouse.backend.common.api.PageRequests;
import com.greenhouse.backend.common.api.PageResponse;
import com.greenhouse.backend.sales.api.partner.BusinessPartnerQueryApi;
import com.greenhouse.backend.sales.domain.payment.PartnerPaymentEvent;
import com.greenhouse.backend.sales.domain.payment.PaymentEventType;
import com.greenhouse.backend.sales.domain.payment.PaymentTargetType;
import com.greenhouse.backend.sales.dto.payment.PartnerPaymentEventResponse;
import com.greenhouse.backend.sales.repository.payment.PartnerPaymentEventRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class PaymentService {

  private static final int LEGACY_LIST_LIMIT = 500;

  private final PartnerPaymentEventRepository eventRepository;

  private final BusinessPartnerQueryApi partnerReader;
  private final PaymentReceiptIntegrity integrity;

  @Transactional(readOnly = true)
  public List<PartnerPaymentEventResponse> getEvents(
      Long partnerId, PaymentTargetType targetType, Long targetId) {
    return eventResponses(
            eventRepository.search(
                partnerId, targetType, targetId, null, PageRequest.of(0, LEGACY_LIST_LIMIT)))
        .getContent();
  }

  @Transactional(readOnly = true)
  public PageResponse<PartnerPaymentEventResponse> getEventPage(
      Long partnerId,
      PaymentTargetType targetType,
      Long targetId,
      PaymentEventType eventType,
      int page,
      int size) {
    return PageResponse.from(
        eventResponses(
            eventRepository.search(
                partnerId, targetType, targetId, eventType, PageRequests.clamped(page, size))));
  }

  private Page<PartnerPaymentEventResponse> eventResponses(Page<PartnerPaymentEvent> events) {
    var partners =
        partnerReader.getAllInfo(events.stream().map(PartnerPaymentEvent::getPartnerId).toList());
    var states =
        integrity.findAll(
            events.stream()
                .filter(event -> event.getEventType() == PaymentEventType.PAYMENT_RECEIVED)
                .map(PartnerPaymentEvent::getId)
                .toList());
    return events.map(
        event ->
            PartnerPaymentEventResponse.from(
                event,
                partners.get(event.getPartnerId()).name(),
                states.containsKey(event.getId()) && states.get(event.getId()).reviewRequired()));
  }
}
