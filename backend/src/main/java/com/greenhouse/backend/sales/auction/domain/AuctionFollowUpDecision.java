package com.greenhouse.backend.sales.auction.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Immutable decision history for the entire unsold remainder at the time of the decision. */
@Entity
@Table(name = "auction_follow_up_decisions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuctionFollowUpDecision {
  @Id
  @GeneratedValue(
      strategy = GenerationType.SEQUENCE,
      generator = "auction_follow_up_decisions_id_seq")
  @SequenceGenerator(
      name = "auction_follow_up_decisions_id_seq",
      sequenceName = "auction_follow_up_decisions_id_seq",
      allocationSize = 50)
  private Long id;

  @Column(name = "lot_id", nullable = false)
  private Long lotId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AuctionFollowUpMethod method;

  @Column(nullable = false)
  private Integer quantity;

  @Column(name = "decided_at", nullable = false)
  private LocalDateTime decidedAt;

  private String worker;

  @Column(columnDefinition = "text")
  private String reason;

  public AuctionFollowUpDecision(
      Long lotId,
      AuctionFollowUpMethod method,
      int quantity,
      String worker,
      String reason,
      LocalDateTime at) {
    if (lotId == null || method == null || quantity < 1 || at == null)
      throw new IllegalArgumentException("후속 결정에는 lot, 방법, 잔량과 시각이 필요합니다.");
    this.lotId = lotId;
    this.method = method;
    this.quantity = quantity;
    this.worker = worker;
    this.reason = reason;
    this.decidedAt = at;
  }
}
