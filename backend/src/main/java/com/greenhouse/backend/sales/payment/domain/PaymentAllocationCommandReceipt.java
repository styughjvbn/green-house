package com.greenhouse.backend.sales.payment.domain;

import com.greenhouse.backend.common.domain.BaseEntity;
import com.greenhouse.backend.common.exception.ConflictException;
import jakarta.persistence.*;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "payment_allocation_command_receipts",
    uniqueConstraints = @UniqueConstraint(columnNames = {"partner_id", "request_key"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentAllocationCommandReceipt extends BaseEntity {
  @Id
  @GeneratedValue(
      strategy = GenerationType.SEQUENCE,
      generator = "payment_allocation_command_receipts_id_seq")
  @SequenceGenerator(
      name = "payment_allocation_command_receipts_id_seq",
      sequenceName = "payment_allocation_command_receipts_id_seq",
      allocationSize = 50)
  private Long id;

  @Column(name = "partner_id", nullable = false)
  private Long partnerId;

  @Column(name = "request_key", nullable = false, length = 100)
  private String requestKey;

  @Column(nullable = false, length = 64)
  private String fingerprint;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "result_json", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> result;

  public PaymentAllocationCommandReceipt(
      Long partnerId,
      String key,
      String fingerprint,
      List<Long> allocations,
      List<Long> cancellations) {
    this.partnerId = partnerId;
    this.requestKey = key;
    this.fingerprint = fingerprint;
    this.result = Map.of("allocations", allocations, "cancellations", cancellations);
  }

  public Map<String, List<Long>> replay(String fingerprint) {
    if (!this.fingerprint.equals(fingerprint))
      throw new ConflictException("IDEMPOTENCY_KEY_REUSED", "같은 배분 요청 키의 입력을 변경할 수 없습니다.");
    return Map.of("allocations", ids("allocations"), "cancellations", ids("cancellations"));
  }

  private List<Long> ids(String key) {
    return ((List<?>) result.get(key)).stream().map(value -> ((Number) value).longValue()).toList();
  }
}
