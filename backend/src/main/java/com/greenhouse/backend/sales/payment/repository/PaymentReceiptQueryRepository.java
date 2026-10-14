package com.greenhouse.backend.sales.payment.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PaymentReceiptQueryRepository {
  private final EntityManager entityManager;

  public record State(long availableAmount, boolean reviewRequired) {}

  public Map<Long, State> findAll(Collection<Long> ids) {
    if (ids.isEmpty()) return Map.of();
    String sql =
        "WITH roots AS (SELECT * FROM partner_payment_events WHERE id IN (:ids))"
            + PaymentAllocationSql.CASH
            + " SELECT id, unapplied_amount, (balance_valid = 0 OR child_review <> 0) FROM cash_states";
    var query = entityManager.createNativeQuery(sql, Tuple.class).setParameter("ids", ids);
    List<?> rows = query.getResultList();
    return rows.stream()
        .map(Tuple.class::cast)
        .collect(
            Collectors.toMap(
                row -> ((Number) row.get(0)).longValue(),
                row -> new State(((Number) row.get(1)).longValue(), (Boolean) row.get(2))));
  }
}
