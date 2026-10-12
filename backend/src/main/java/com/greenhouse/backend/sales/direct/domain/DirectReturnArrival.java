package com.greenhouse.backend.sales.direct.domain;

import com.greenhouse.backend.common.exception.ConflictException;
import jakarta.persistence.*;
import java.time.*;
import lombok.*;

@Entity
@Table(name = "direct_return_arrivals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DirectReturnArrival {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "sales_slip_id", nullable = false)
  private Long documentId;

  @Column(name = "return_change_id", nullable = false)
  private Long returnChangeId;

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

  @Column(name = "correction_date")
  private LocalDate correctionDate;

  @Column(name = "cancellation_mutation_id")
  private Long cancellationMutationId;

  @Column(name = "cancellation_reason", columnDefinition = "text")
  private String cancellationReason;

  public DirectReturnArrival(
      Long documentId,
      Long returnChangeId,
      int quantity,
      LocalDate date,
      String worker,
      LocalDateTime at) {
    if (documentId == null || returnChangeId == null || quantity < 1 || date == null || at == null)
      throw new IllegalArgumentException("반품 도착에는 원반품, 수량, 도착일이 필요합니다.");
    this.documentId = documentId;
    this.returnChangeId = returnChangeId;
    this.quantity = quantity;
    arrivalDate = date;
    this.worker = worker;
    createdAt = at;
  }

  public void linkCreation(Long groupId, Long mutationId) {
    if (groupId == null || mutationId == null || creationMutationId != null)
      throw new IllegalArgumentException("반품 도착의 농장 생성 연결을 확인해야 합니다.");
    orchidGroupId = groupId;
    creationMutationId = mutationId;
  }

  public boolean isCancellationAllowed() {
    return canceledAt == null;
  }

  public void cancel(Long mutationId, LocalDate date, String reason, LocalDateTime at) {
    if (canceledAt != null)
      throw new ConflictException("DIRECT_ARRIVAL_ALREADY_CANCELED", "이미 취소한 도착 기록입니다.");
    if ((creationMutationId == null) != (mutationId == null)
        || date == null
        || date.isBefore(arrivalDate)
        || reason == null
        || reason.isBlank()
        || at == null) throw new IllegalArgumentException("도착 취소의 날짜, 사유와 농장 보상 연결을 확인하세요.");
    canceledAt = at;
    correctionDate = date;
    cancellationMutationId = mutationId;
    cancellationReason = reason.trim();
  }
}
