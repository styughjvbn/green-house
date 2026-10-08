package com.greenhouse.backend.sales.application.payment;

import com.greenhouse.backend.sales.payment.api.PaymentTargetType;
import com.greenhouse.backend.sales.payment.spi.PaymentAllocationTargetPort;
import com.greenhouse.backend.sales.repository.payment.PartnerPaymentEventRepository;
import com.greenhouse.backend.sales.repository.payment.PaymentReceiptQueryRepository;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Internal receipt checks combine Payment evidence with batched target ownership value contracts.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class PaymentReceiptIntegrity {
  private final PaymentReceiptQueryRepository cash;
  private final PartnerPaymentEventRepository events;
  private final List<PaymentAllocationTargetPort<?>> ports;

  Map<Long, PaymentReceiptQueryRepository.State> findAll(Collection<Long> receiptIds) {
    if (receiptIds.isEmpty()) return Map.of();
    var states = new HashMap<>(cash.findAll(receiptIds));
    var references = events.findAllocationReferences(receiptIds);
    Map<PaymentTargetType, Map<Long, Long>> owners = new EnumMap<>(PaymentTargetType.class);
    for (var port : ports) {
      var ids =
          references.stream()
              .filter(row -> row.getTargetType() == port.targetType())
              .map(PartnerPaymentEventRepository.AllocationReference::getTargetId)
              .filter(Objects::nonNull)
              .distinct()
              .sorted()
              .toList();
      var values = new HashMap<Long, Long>();
      for (int offset = 0; offset < ids.size(); offset += 500)
        values.putAll(
            port.findTargetOwners(ids.subList(offset, Math.min(offset + 500, ids.size()))));
      owners.put(port.targetType(), values);
    }
    for (var reference : references) {
      var values =
          reference.getTargetType() == null
              ? Map.<Long, Long>of()
              : owners.getOrDefault(reference.getTargetType(), Map.of());
      var owner = reference.getTargetId() == null ? null : values.get(reference.getTargetId());
      if (owner == null || !owner.equals(reference.getPartnerId())) {
        var state = states.get(reference.getReceiptId());
        if (state != null)
          states.put(
              reference.getReceiptId(),
              new PaymentReceiptQueryRepository.State(state.availableAmount(), true));
      }
    }
    return Map.copyOf(states);
  }
}
