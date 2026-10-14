package com.greenhouse.backend.sales.direct.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "direct_return_command_receipts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DirectReturnCommandReceipt {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "sales_slip_id", nullable = false)
  private Long documentId;

  @Column(name = "request_key", nullable = false, length = 100)
  private String requestKey;

  @Column(nullable = false, length = 64)
  private String fingerprint;

  @Column(name = "arrival_id", nullable = false)
  private Long arrivalId;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  public DirectReturnCommandReceipt(
      Long documentId, String key, String fingerprint, Long arrivalId, LocalDateTime at) {
    this.documentId = documentId;
    requestKey = key;
    this.fingerprint = fingerprint;
    this.arrivalId = arrivalId;
    createdAt = at;
  }
}
