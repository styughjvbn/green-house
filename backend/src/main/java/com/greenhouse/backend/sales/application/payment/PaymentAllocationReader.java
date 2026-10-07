package com.greenhouse.backend.sales.application.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
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
  private static final JsonMapper TARGET_MAPPER = JsonMapper.builder().build();
  private final PartnerPaymentEventRepository repository;

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
          repository.findAllocationTotals(
              type.name(),
              ownerJson(ids.subList(offset, Math.min(offset + 500, ids.size())), owners),
              validateOwner)) {
        result.put(
            row.getTargetId(),
            new Allocation(
                row.getAmount(), row.getPartnerId(), Boolean.TRUE.equals(row.getReviewRequired())));
      }
    }
    return Map.copyOf(result);
  }

  private String ownerJson(List<Long> ids, Map<Long, Long> owners) {
    try {
      return TARGET_MAPPER.writeValueAsString(
          ids.stream().map(id -> new TargetOwner(id, owners.get(id))).toList());
    } catch (JsonProcessingException failure) {
      throw new IllegalStateException("배분 대상 식별자 직렬화에 실패했습니다.", failure);
    }
  }

  private record TargetOwner(Long targetId, Long partnerId) {}

  public record Allocation(BigDecimal amount, Long partnerId, boolean reviewRequired) {}
}
