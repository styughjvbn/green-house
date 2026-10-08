package com.greenhouse.backend.sales.payment.repository;

/**
 * Shared validity rules for target totals and receipt availability. roots is provided by callers.
 */
final class PaymentAllocationSql {
  private PaymentAllocationSql() {}

  static final String CASH =
      """
      , cancellation_proofs AS (
          SELECT correction.id, original.id AS allocation_id
          FROM partner_payment_events correction JOIN partner_payment_events original ON original.id = correction.parent_event_id
          WHERE original.parent_event_id IN (SELECT id FROM roots) AND correction.event_type = 'PAYMENT_UNLINKED' AND correction.status = 'CONFIRMED'
            AND original.event_type IN ('MANUAL_MATCH_CONFIRMED', 'PAYMENT_ALLOCATED') AND original.status = 'CANCELLED'
            AND correction.amount = original.amount AND correction.partner_id = original.partner_id
            AND correction.target_type = original.target_type AND correction.target_id = original.target_id
            AND correction.external_uid = CONCAT('ALLOCATION_CANCEL:', CAST(original.id AS VARCHAR))
      ), cash_cancel_proofs AS (
          SELECT correction.id, cash.id AS receipt_id FROM roots cash
          JOIN partner_payment_events correction ON correction.parent_event_id = cash.id
          WHERE cash.event_type = 'PAYMENT_RECEIVED' AND cash.status = 'CANCELLED'
            AND cash.target_type = 'NONE' AND cash.target_id IS NULL
            AND correction.event_type = 'ADJUSTMENT' AND correction.status = 'CONFIRMED'
            AND correction.partner_id = cash.partner_id AND correction.amount = cash.amount
            AND correction.target_type = 'NONE' AND correction.target_id IS NULL
            AND correction.external_uid LIKE 'UNASSIGNED_CANCEL:%'
      ), cash_children AS (
          SELECT child.id, child.parent_event_id, child.target_type, child.target_id, child.partner_id,
              child.event_type, child.status, child.amount,
              CASE WHEN child.amount > 0 AND child.partner_id = cash.partner_id AND (
                  (child.event_type = 'MANUAL_MATCH_CONFIRMED' AND child.amount = cash.amount
                    AND child.target_type = cash.target_type AND child.target_id = cash.target_id)
                  OR (child.event_type = 'PAYMENT_ALLOCATED' AND child.amount <= cash.amount
                    AND child.target_type IN ('SALES_SLIP', 'AUCTION_PROCEEDS') AND child.target_id > 0))
                  THEN 1
                  WHEN EXISTS (SELECT 1 FROM cash_cancel_proofs proof WHERE proof.id = child.id) THEN 1
                  ELSE 0 END AS shape_valid,
              CASE WHEN EXISTS (SELECT 1 FROM cancellation_proofs proof WHERE proof.allocation_id = child.id)
                  THEN 1 ELSE 0 END AS canceled_proven
          FROM partner_payment_events child JOIN roots cash ON cash.id = child.parent_event_id
      ), normalized_cash_links AS (
          SELECT parent_event_id, MAX(CAST(amount AS NUMERIC)) AS amount, COUNT(*) AS duplicates
          FROM cash_children WHERE shape_valid = 1 AND status = 'CONFIRMED' AND event_type = 'MANUAL_MATCH_CONFIRMED'
          GROUP BY parent_event_id
          UNION ALL
          SELECT parent_event_id, CAST(amount AS NUMERIC), 1 FROM cash_children
          WHERE shape_valid = 1 AND status = 'CONFIRMED' AND event_type = 'PAYMENT_ALLOCATED'
      ), cash_totals AS (
          SELECT parent_event_id, SUM(amount) AS applied, MAX(CASE WHEN duplicates > 1 THEN 1 ELSE 0 END) AS duplicate_match
          FROM normalized_cash_links GROUP BY parent_event_id
      ), cash_children_review AS (
          SELECT parent_event_id,
              MAX(CASE WHEN shape_valid = 1 AND (status = 'CONFIRMED' OR (status = 'CANCELLED' AND canceled_proven = 1))
                  THEN 0 ELSE 1 END) AS child_review,
              MAX(CASE WHEN shape_valid = 1 AND event_type = 'MANUAL_MATCH_CONFIRMED' THEN 1 ELSE 0 END) AS original_match
          FROM cash_children GROUP BY parent_event_id
      ), cash_states AS (
          SELECT cash.id, cash.partner_id, cash.amount, cash.unapplied_amount,
              CASE WHEN cash.event_type = 'PAYMENT_RECEIVED' AND cash.amount > 0
                AND cash.unapplied_amount BETWEEN 0 AND cash.amount
                AND (((cash.status = 'FULLY_APPLIED' AND cash.unapplied_amount = 0)
                  OR (cash.status = 'PARTIALLY_APPLIED' AND cash.unapplied_amount > 0 AND cash.unapplied_amount < cash.amount)
                  OR (cash.status = 'UNAPPLIED' AND cash.unapplied_amount = cash.amount))
                  OR (cash.status = 'CANCELLED' AND cash.unapplied_amount = 0 AND COALESCE(total.applied, 0) = 0
                    AND EXISTS (SELECT 1 FROM cash_cancel_proofs proof WHERE proof.receipt_id = cash.id)))
                AND (cash.status = 'CANCELLED' OR COALESCE(total.applied, 0) + CAST(cash.unapplied_amount AS NUMERIC) = CAST(cash.amount AS NUMERIC))
                AND ((cash.target_type = 'NONE' AND cash.target_id IS NULL) OR COALESCE(review.original_match, 0) = 1)
                THEN 1 ELSE 0 END AS balance_valid,
              CASE WHEN COALESCE(review.child_review, 0) <> 0 OR COALESCE(total.duplicate_match, 0) <> 0 THEN 1 ELSE 0 END AS child_review
          FROM roots cash LEFT JOIN cash_totals total ON total.parent_event_id = cash.id
          LEFT JOIN cash_children_review review ON review.parent_event_id = cash.id
      )
      """;
}
