package com.greenhouse.backend.farm.orchid.domain;

import com.greenhouse.backend.common.exception.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "orchid_stock_counts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrchidStockCount {

  @Id
  @Column(length = 100)
  private String requestKey;

  @Column(nullable = false, length = 64)
  private String requestFingerprint;

  @Column(nullable = false)
  private Long orchidGroupId;

  @Column(nullable = false)
  private LocalDateTime recordedAt;

  @Column(nullable = false)
  private LocalDate businessDate;

  @Column(length = 100)
  private String worker;

  @Column(nullable = false, length = 1000)
  private String reason;

  @Column(length = 1000)
  private String memo;

  private Integer beforeQuantity;

  private Integer actualQuantity;

  private Long mutationId;

  public void validate(String fingerprint) {
    if (!requestFingerprint.equals(fingerprint))
      throw new ConflictException("IDEMPOTENCY_KEY_REUSED", "같은 키를 다른 실사 요청에 사용할 수 없습니다.");
  }

  public void complete(int before, int actual, Long mutation, LocalDateTime completedAt) {
    if (mutationId != null || mutation == null)
      throw new IllegalStateException("실사는 한 번만 확정할 수 있습니다.");
    beforeQuantity = before;
    actualQuantity = actual;
    mutationId = mutation;
    recordedAt = completedAt;
  }
}
