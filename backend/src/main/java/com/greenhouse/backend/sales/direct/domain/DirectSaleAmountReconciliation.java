package com.greenhouse.backend.sales.direct.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.type.SqlTypes;

/** Immutable evidence from the migration, separate from current amounts. */
@Entity
@Table(name = "direct_sale_amount_reconciliations")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DirectSaleAmountReconciliation {
  @Id
  @Column(name = "sales_slip_id")
  private Long documentId;

  @Column(name = "stored_paid_amount")
  private Long storedPaidAmount;

  @Column(name = "stored_remaining_amount")
  private Long storedRemainingAmount;

  @Column(name = "stored_payment_status", nullable = false)
  private String storedPaymentStatus;

  @Column(name = "stored_item_amount_sum", nullable = false)
  private Long storedItemAmountSum;

  @Column(name = "confirmed_allocation_amount", nullable = false)
  private Long confirmedAllocationAmount;

  @Column(name = "total_mismatch", nullable = false)
  private boolean totalMismatch;

  @Column(name = "price_mismatch", nullable = false)
  private boolean priceMismatch;

  @Column(name = "paid_mismatch", nullable = false)
  private boolean paidMismatch;

  @Column(name = "remaining_mismatch", nullable = false)
  private boolean remainingMismatch;

  @Column(name = "ledger_review_required", nullable = false)
  private boolean ledgerReviewRequired;

  @Column(name = "signed_amount_review_required", nullable = false)
  private boolean signedAmountReviewRequired;

  @org.hibernate.annotations.JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "cutover_legacy_snapshot", columnDefinition = "jsonb")
  private Map<String, Object> cutoverLegacySnapshot;

  @Column(name = "cutover_review_required", nullable = false)
  @org.hibernate.annotations.ColumnDefault("false")
  private boolean cutoverReviewRequired;

  public boolean requiresReview() {
    return cutoverReviewRequired
        || totalMismatch
        || priceMismatch
        || paidMismatch
        || remainingMismatch
        || ledgerReviewRequired
        || signedAmountReviewRequired;
  }
}
