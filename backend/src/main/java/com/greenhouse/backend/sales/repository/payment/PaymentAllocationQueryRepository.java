package com.greenhouse.backend.sales.repository.payment;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** CTE aggregation counts received facts once and inspects incomplete links in one query. */
@Repository
@RequiredArgsConstructor
public class PaymentAllocationQueryRepository {
  private final EntityManager entityManager;
  private static final String QUERY =
      """
      WITH targets(target_id, partner_id) AS (VALUES %s), scoped AS (
              SELECT id, event_type, status, amount, partner_id, target_type, target_id, parent_event_id
              FROM partner_payment_events WHERE target_type = :targetType AND target_id IN (SELECT target_id FROM targets)
      ), eligible AS (
          SELECT matched.id, matched.target_id, matched.parent_event_id, matched.amount
          FROM scoped matched JOIN partner_payment_events received ON received.id = matched.parent_event_id
          JOIN targets owner ON owner.target_id = matched.target_id
          WHERE matched.event_type = 'MANUAL_MATCH_CONFIRMED' AND matched.status = 'CONFIRMED'
              AND matched.amount > 0 AND received.event_type = 'PAYMENT_RECEIVED'
              AND received.status = 'FULLY_APPLIED' AND received.unapplied_amount = 0
              AND received.target_type = matched.target_type AND received.target_id = matched.target_id
              AND received.partner_id = matched.partner_id AND received.amount = matched.amount
              AND (NOT :validateOwner OR received.partner_id = owner.partner_id)
      ), unique_links AS (
          SELECT target_id, parent_event_id, MAX(amount) AS amount, COUNT(*) AS matches
          FROM eligible GROUP BY target_id, parent_event_id
      ), allocated AS (
          SELECT target_id, SUM(amount) AS amount, MAX(CASE WHEN matches <> 1 THEN 1 ELSE 0 END) AS duplicate_match
          FROM unique_links GROUP BY target_id
      ), review AS (
          SELECT event.target_id, MIN(event.partner_id) AS partner_id,
              CASE WHEN MIN(event.partner_id) <> MAX(event.partner_id) THEN 1 ELSE 0 END AS partner_mismatch,
              MAX(CASE
              WHEN event.event_type = 'PAYMENT_RECEIVED' AND EXISTS (
                  SELECT 1 FROM eligible valid WHERE valid.parent_event_id = event.id) THEN 0
              WHEN event.event_type = 'MANUAL_MATCH_CONFIRMED' AND EXISTS (
                  SELECT 1 FROM eligible valid WHERE valid.id = event.id) THEN 0
              ELSE 1 END) AS required
          FROM scoped event GROUP BY event.target_id
      )
      SELECT review.target_id AS targetId, review.partner_id AS partnerId, COALESCE(allocated.amount, 0) AS amount,
          (review.required <> 0 OR review.partner_mismatch <> 0 OR COALESCE(allocated.duplicate_match, 0) <> 0) AS reviewRequired
      FROM review LEFT JOIN allocated ON allocated.target_id = review.target_id
            """;

  public List<PaymentAllocationTotals> find(
      String targetType, List<Long> ids, Map<Long, Long> owners, boolean validateOwner) {
    if (ids.isEmpty()) return List.of();
    var values = new ArrayList<String>();
    for (int i = 0; i < ids.size(); i++)
      values.add("(CAST(:id" + i + " AS BIGINT), CAST(:partner" + i + " AS BIGINT))");
    var query =
        entityManager.createNativeQuery(QUERY.formatted(String.join(",", values)), Tuple.class);
    query.setParameter("targetType", targetType);
    query.setParameter("validateOwner", validateOwner);
    for (int i = 0; i < ids.size(); i++) {
      query.setParameter("id" + i, ids.get(i));
      query.setParameter("partner" + i, owners.get(ids.get(i)));
    }
    List<?> tuples = query.getResultList();
    return tuples.stream()
        .map(
            value -> {
              var row = (Tuple) value;
              return (PaymentAllocationTotals)
                  new Row(
                      ((Number) row.get(0)).longValue(),
                      row.get(1) == null ? null : ((Number) row.get(1)).longValue(),
                      new BigDecimal(row.get(2).toString()),
                      (Boolean) row.get(3));
            })
        .toList();
  }

  private record Row(
      Long getTargetId, Long getPartnerId, BigDecimal getAmount, Boolean getReviewRequired)
      implements PaymentAllocationTotals {}
}
