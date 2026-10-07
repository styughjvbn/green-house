package com.greenhouse.backend.sales.repository.payment;

import com.greenhouse.backend.sales.domain.payment.PartnerPaymentEvent;
import com.greenhouse.backend.sales.domain.payment.PaymentEventType;
import com.greenhouse.backend.sales.domain.payment.PaymentTargetType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PartnerPaymentEventRepository extends JpaRepository<PartnerPaymentEvent, Long> {

  // PostgreSQL CTE aggregation counts each received amount at most once, even if
  // historical manual links are duplicated. Unknown/cancelled/corrupt facts remain reviewable.
  @Query(
      value =
          """
      WITH scoped AS (
              SELECT id, event_type, status, amount, partner_id, target_type, target_id, parent_event_id
              FROM partner_payment_events WHERE target_type = :targetType AND target_id IN (:ids)
      ), eligible AS (
          SELECT matched.id, matched.target_id, matched.parent_event_id, matched.amount
          FROM scoped matched JOIN partner_payment_events received ON received.id = matched.parent_event_id
          WHERE matched.event_type = 'MANUAL_MATCH_CONFIRMED' AND matched.status = 'CONFIRMED'
              AND matched.amount > 0 AND received.event_type = 'PAYMENT_RECEIVED'
              AND received.status = 'FULLY_APPLIED' AND received.unapplied_amount = 0
              AND received.target_type = matched.target_type AND received.target_id = matched.target_id
              AND received.partner_id = matched.partner_id AND received.amount = matched.amount
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
      """,
      nativeQuery = true)
  List<PaymentAllocationTotals> findAllocationTotals(
      @Param("targetType") String targetType, @Param("ids") List<Long> ids);

  boolean existsByTargetTypeAndTargetId(PaymentTargetType targetType, Long targetId);

  @Query(
      """
			select distinct event.targetId from PartnerPaymentEvent event
			where event.targetType = :targetType
			  and event.targetId in :targetIds
			""")
  List<Long> findExistingTargetIds(
      @Param("targetType") PaymentTargetType targetType, @Param("targetIds") List<Long> targetIds);

  Optional<PartnerPaymentEvent> findByExternalUid(String externalUid);

  @Query(
      """
			select event from PartnerPaymentEvent event
			where (:partnerId is null or event.partnerId = :partnerId)
			  and (:targetType is null or event.targetType = :targetType)
			  and (:targetId is null or event.targetId = :targetId)
			  and (:eventType is null or event.eventType = :eventType)
			order by event.eventDate desc, event.id desc
			""")
  Page<PartnerPaymentEvent> search(
      @Param("partnerId") Long partnerId,
      @Param("targetType") PaymentTargetType targetType,
      @Param("targetId") Long targetId,
      @Param("eventType") PaymentEventType eventType,
      Pageable pageable);
}
