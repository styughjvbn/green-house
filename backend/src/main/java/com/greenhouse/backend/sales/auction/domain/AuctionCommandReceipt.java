package com.greenhouse.backend.sales.auction.domain;

import com.greenhouse.backend.common.exception.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(
    name = "auction_command_receipts",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_auction_command_receipt",
            columnNames = {"lot_id", "command_type", "request_key"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuctionCommandReceipt {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "auction_command_receipts_id_seq")
  @SequenceGenerator(
      name = "auction_command_receipts_id_seq",
      sequenceName = "auction_command_receipts_id_seq",
      allocationSize = 50)
  private Long id;

  @Column(name = "lot_id", nullable = false)
  private Long lotId;

  @Column(name = "command_type", nullable = false, length = 16)
  private String commandType;

  @Column(name = "request_key", nullable = false, length = 100)
  private String requestKey;

  @Column(name = "request_fingerprint", nullable = false, length = 64)
  private String requestFingerprint;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "response_snapshot", nullable = false, columnDefinition = "jsonb")
  private String responseSnapshot;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  public AuctionCommandReceipt(
      Long lotId,
      String commandType,
      String requestKey,
      String requestFingerprint,
      String responseSnapshot,
      LocalDateTime createdAt) {
    this.lotId = lotId;
    this.commandType = commandType;
    this.requestKey = requestKey;
    this.requestFingerprint = requestFingerprint;
    this.responseSnapshot = responseSnapshot;
    this.createdAt = createdAt;
  }

  public void validate(String fingerprint) {
    if (!requestFingerprint.equals(fingerprint)) {
      throw new ConflictException(
          "AUCTION_REQUEST_KEY_CONFLICT", "이미 처리한 경매 요청과 내용이 다릅니다. 이전 입력 내용과 처리 결과를 확인하세요.");
    }
  }
}
