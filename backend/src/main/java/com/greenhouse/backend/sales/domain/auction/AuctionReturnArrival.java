package com.greenhouse.backend.sales.domain.auction;

import com.greenhouse.backend.common.exception.ConflictException;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Actual arrival and its Farm creation; cancellation preserves both references. */
@Entity
@Table(name = "auction_return_arrivals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuctionReturnArrival {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "auction_return_arrivals_id_seq")
  @SequenceGenerator(
      name = "auction_return_arrivals_id_seq",
      sequenceName = "auction_return_arrivals_id_seq",
      allocationSize = 50)
  private Long id;

  @Column(name = "lot_id", nullable = false)
  private Long lotId;

  @Column(name = "decision_id", nullable = false)
  private Long decisionId;

  @Column(nullable = false)
  private Integer quantity;

  @Column(name = "arrival_date", nullable = false)
  private LocalDate arrivalDate;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  private String worker;

  @Column(name = "orchid_group_id")
  private Long orchidGroupId;

  @Column(name = "creation_mutation_id")
  private Long creationMutationId;

  @Column(name = "canceled_at")
  private LocalDateTime canceledAt;

  @Column(name = "cancellation_mutation_id")
  private Long cancellationMutationId;

  @Column(name = "cancellation_reason", columnDefinition = "text")
  private String cancellationReason;

  public AuctionReturnArrival(
      Long lotId, Long decisionId, int quantity, LocalDate date, String worker, LocalDateTime at) {
    if (lotId == null || decisionId == null || quantity < 1 || date == null || at == null)
      throw new IllegalArgumentException("실제 도착에는 lot, 수량, 도착일과 시각이 필요합니다.");
    this.lotId = lotId;
    this.decisionId = decisionId;
    this.quantity = quantity;
    this.arrivalDate = date;
    this.worker = worker;
    this.createdAt = at;
  }

  public void linkCreation(Long groupId, Long mutationId) {
    if (orchidGroupId != null
        || creationMutationId != null
        || groupId == null
        || mutationId == null) throw new IllegalArgumentException("도착 기록의 농장 생성 연결을 확인해야 합니다.");
    this.orchidGroupId = groupId;
    this.creationMutationId = mutationId;
  }

  public void cancel(Long mutationId, String reason, LocalDateTime at) {
    if (canceledAt != null)
      throw new ConflictException("AUCTION_ARRIVAL_ALREADY_CANCELED", "이미 취소한 도착 기록입니다.");
    if (creationMutationId == null
        || mutationId == null
        || at == null
        || reason == null
        || reason.isBlank()) throw new IllegalArgumentException("도착 취소에는 농장 보상 연결과 사유가 필요합니다.");
    canceledAt = at;
    cancellationMutationId = mutationId;
    cancellationReason = reason.trim();
  }
}
