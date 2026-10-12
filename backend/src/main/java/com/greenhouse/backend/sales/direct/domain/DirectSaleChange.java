package com.greenhouse.backend.sales.direct.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Append-only commercial facts. Neither a physical stock movement nor a cash refund. */
@Entity
@Table(name = "direct_sale_changes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DirectSaleChange {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "sales_slip_id", nullable = false)
  private Long documentId;

  @Column(name = "sales_slip_item_id", nullable = false)
  private Long itemId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private DirectSaleChangeType type;

  @Column(name = "source_change_id")
  private Long sourceChangeId;

  @Column(name = "canceled_change_id")
  private Long canceledChangeId;

  @Column(nullable = false)
  private int quantity;

  @Column(name = "previous_unit_price", nullable = false)
  private int previousUnitPrice;

  @Column(name = "next_unit_price", nullable = false)
  private int nextUnitPrice;

  @Column(name = "amount_delta", nullable = false)
  private long amountDelta;

  @Column(name = "allocated_before", nullable = false)
  private long allocatedBefore;

  @Column(name = "change_date", nullable = false)
  private LocalDate changeDate;

  @Column(nullable = false, length = 500)
  private String reason;

  @Column(name = "request_key", nullable = false, length = 100)
  private String requestKey;

  @Column(nullable = false, length = 64)
  private String fingerprint;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  public DirectSaleChange(
      Long documentId,
      Long itemId,
      DirectSaleChangeType type,
      Long sourceChangeId,
      Long canceledChangeId,
      int quantity,
      int previousUnitPrice,
      int nextUnitPrice,
      long amountDelta,
      long allocatedBefore,
      LocalDate changeDate,
      String reason,
      String requestKey,
      String fingerprint,
      LocalDateTime createdAt) {
    this.documentId = documentId;
    this.itemId = itemId;
    this.type = type;
    this.sourceChangeId = sourceChangeId;
    this.canceledChangeId = canceledChangeId;
    this.quantity = quantity;
    this.previousUnitPrice = previousUnitPrice;
    this.nextUnitPrice = nextUnitPrice;
    this.amountDelta = amountDelta;
    this.allocatedBefore = allocatedBefore;
    this.changeDate = changeDate;
    this.reason = reason;
    this.requestKey = requestKey;
    this.fingerprint = fingerprint;
    this.createdAt = createdAt;
  }
}
