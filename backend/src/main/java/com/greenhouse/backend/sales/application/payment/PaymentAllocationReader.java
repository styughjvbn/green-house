package com.greenhouse.backend.sales.application.payment;

import com.greenhouse.backend.sales.domain.payment.PaymentTargetType;
import com.greenhouse.backend.sales.repository.payment.PartnerPaymentEventRepository;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentAllocationReader {
  private final PartnerPaymentEventRepository repository;

  public Map<Long, Allocation> findAll(PaymentTargetType type, Collection<Long> targetIds) {
    List<Long> ids = targetIds.stream().distinct().sorted().toList();
    Map<Long, Allocation> result = new LinkedHashMap<>();
    ids.forEach(id -> result.put(id, new Allocation(BigDecimal.ZERO, null, false)));
    for (int offset = 0; offset < ids.size(); offset += 500) {
      for (var row :
          repository.findAllocationTotals(
              type.name(), ids.subList(offset, Math.min(offset + 500, ids.size())))) {
        result.put(
            row.getTargetId(),
            new Allocation(
                row.getAmount(), row.getPartnerId(), Boolean.TRUE.equals(row.getReviewRequired())));
      }
    }
    return Map.copyOf(result);
  }

  public record Allocation(BigDecimal amount, Long partnerId, boolean reviewRequired) {}
}
