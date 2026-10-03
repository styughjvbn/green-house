package com.greenhouse.backend.work.domain.correction;

import com.greenhouse.backend.common.exception.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "work_correction_receipts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkCorrectionReceipt {

  @Id
  @Column(length = 100)
  private String requestKey;

  @Column(nullable = false, length = 64)
  private String requestFingerprint;

  private Long correctionId;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  public void validate(String fingerprint) {
    if (!requestFingerprint.equals(fingerprint)) {
      throw new ConflictException("IDEMPOTENCY_KEY_REUSED", "같은 멱등 키를 다른 보정 요청에 사용할 수 없습니다.");
    }
  }

  public void complete(Long correctionId) {
    if (this.correctionId != null || correctionId == null)
      throw new IllegalStateException("보정 요청 결과는 한 번만 확정해야 합니다.");
    this.correctionId = correctionId;
  }
}
