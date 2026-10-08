package com.greenhouse.backend.sales.application.payment;

import com.greenhouse.backend.sales.payment.api.PaymentAllocationQueryApi;
import com.greenhouse.backend.sales.payment.api.PaymentAllocationQueryApi.Allocation;
import com.greenhouse.backend.sales.payment.api.PaymentTargetType;
import com.greenhouse.backend.sales.repository.payment.PaymentAllocationQueryRepository;
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
public class PaymentAllocationReader implements PaymentAllocationQueryApi {
  private final PaymentAllocationQueryRepository repository;

  public Map<Long, Allocation> findAll(PaymentTargetType type, Collection<Long> targetIds) {
    Map<Long, Long> targets = new LinkedHashMap<>();
    targetIds.forEach(id -> targets.put(id, null));
    return findAll(type, targets, false);
  }

  /** Current allocation totals require the target owner's partner identity. */
  public Map<Long, Allocation> findAll(PaymentTargetType type, Map<Long, Long> owners) {
    return findAll(type, owners, true);
  }

  private Map<Long, Allocation> findAll(
      PaymentTargetType type, Map<Long, Long> owners, boolean validateOwner) {
    List<Long> ids = owners.keySet().stream().sorted().toList();
    Map<Long, Allocation> result = new LinkedHashMap<>();
    ids.forEach(id -> result.put(id, new Allocation(BigDecimal.ZERO, null, false)));
    for (int offset = 0; offset < ids.size(); offset += 500) {
      for (var row :
          repository.find(
              type.name(),
              ids.subList(offset, Math.min(offset + 500, ids.size())),
              owners,
              validateOwner)) {
        result.put(
            row.getTargetId(),
            new Allocation(
                row.getAmount(), row.getPartnerId(), Boolean.TRUE.equals(row.getReviewRequired())));
      }
    }
    return Map.copyOf(result);
  }
}
