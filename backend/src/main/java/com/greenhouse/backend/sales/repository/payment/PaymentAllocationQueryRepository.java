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
      WITH targets(target_id, partner_id) AS (VALUES /*TARGET_VALUES*/), scoped AS (
          SELECT id, event_type, status, amount, partner_id, target_type, target_id, parent_event_id
          FROM partner_payment_events WHERE target_type = :targetType AND target_id IN (SELECT target_id FROM targets)
      ), roots AS (
          SELECT * FROM partner_payment_events WHERE id IN (
            SELECT id FROM scoped WHERE event_type = 'PAYMENT_RECEIVED'
            UNION SELECT parent_event_id FROM scoped WHERE event_type IN ('MANUAL_MATCH_CONFIRMED', 'PAYMENT_ALLOCATED'))
      )
      """
          + PaymentAllocationSql.CASH
          + """
      , eligible AS (
          SELECT link.id, link.target_id, link.parent_event_id, link.amount, link.event_type
          FROM cash_children link JOIN cash_states cash ON cash.id = link.parent_event_id
          JOIN targets owner ON owner.target_id = link.target_id
          WHERE link.target_type = :targetType AND link.shape_valid = 1 AND link.status = 'CONFIRMED'
            AND cash.balance_valid = 1 AND (NOT :validateOwner OR link.partner_id = owner.partner_id)
      ), unique_links AS (
          SELECT target_id, parent_event_id, MAX(CAST(amount AS NUMERIC)) AS amount, COUNT(*) AS matches
          FROM eligible WHERE event_type = 'MANUAL_MATCH_CONFIRMED' GROUP BY target_id, parent_event_id
          UNION ALL
          SELECT target_id, parent_event_id, CAST(amount AS NUMERIC), 1 FROM eligible WHERE event_type = 'PAYMENT_ALLOCATED'
      ), allocated AS (
          SELECT target_id, SUM(amount) AS amount, MAX(CASE WHEN matches <> 1 THEN 1 ELSE 0 END) AS duplicate_match
          FROM unique_links GROUP BY target_id
      ), review AS (
          SELECT event.target_id, MIN(event.partner_id) AS partner_id,
              CASE WHEN MIN(event.partner_id) <> MAX(event.partner_id) THEN 1 ELSE 0 END AS partner_mismatch,
              MAX(CASE
                WHEN event.event_type = 'PAYMENT_RECEIVED' AND EXISTS (
                  SELECT 1 FROM cash_states cash WHERE cash.id = event.id AND cash.balance_valid = 1 AND cash.child_review = 0) THEN 0
                WHEN event.event_type IN ('MANUAL_MATCH_CONFIRMED', 'PAYMENT_ALLOCATED') AND event.status = 'CONFIRMED' AND EXISTS (
                  SELECT 1 FROM eligible valid JOIN cash_states cash ON cash.id = valid.parent_event_id
                  WHERE valid.id = event.id AND cash.child_review = 0) THEN 0
                WHEN event.event_type IN ('MANUAL_MATCH_CONFIRMED', 'PAYMENT_ALLOCATED') AND event.status = 'CANCELLED' AND EXISTS (
                  SELECT 1 FROM cash_children child JOIN cash_states cash ON cash.id = child.parent_event_id
                  WHERE child.id = event.id AND child.shape_valid = 1 AND child.canceled_proven = 1 AND cash.balance_valid = 1 AND cash.child_review = 0) THEN 0
                WHEN event.event_type = 'PAYMENT_UNLINKED' AND EXISTS (
                  SELECT 1 FROM cancellation_proofs proof WHERE proof.id = event.id) THEN 0
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
        entityManager.createNativeQuery(
            QUERY.replace("/*TARGET_VALUES*/", String.join(",", values)), Tuple.class);
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
                      new BigDecimal(row.get(2).toString()).setScale(0),
                      (Boolean) row.get(3));
            })
        .toList();
  }

  private record Row(
      Long getTargetId, Long getPartnerId, BigDecimal getAmount, Boolean getReviewRequired)
      implements PaymentAllocationTotals {}
}
