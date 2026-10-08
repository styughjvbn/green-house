package com.greenhouse.backend.farm.inbound.domain;

import com.greenhouse.backend.common.exception.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "inbound_creation_receipts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InboundCreationReceipt {
  @Id
  @Column(name = "request_key", length = 100)
  private String requestKey;

  @Column(name = "request_fingerprint", nullable = false, length = 64)
  private String requestFingerprint;

  @Column(name = "inbound_record_id")
  private Long inboundRecordId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "response_snapshot", columnDefinition = "jsonb")
  private String responseSnapshot;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  public void validate(String fingerprint) {
    if (!requestFingerprint.equals(fingerprint)) {
      throw new ConflictException(
          "INBOUND_CREATE_REQUEST_KEY_CONFLICT",
          "이미 처리한 입고 생성 요청과 내용이 다릅니다. 이전 입력 내용과 처리 결과를 확인하세요.");
    }
  }

  public void complete(Long recordId, String response) {
    if (inboundRecordId != null
        || responseSnapshot != null
        || recordId == null
        || response == null) {
      throw new IllegalStateException("입고 생성 응답은 한 번만 확정해야 합니다.");
    }
    inboundRecordId = recordId;
    responseSnapshot = response;
  }
}
