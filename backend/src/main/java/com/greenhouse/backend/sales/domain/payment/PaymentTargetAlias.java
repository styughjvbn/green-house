package com.greenhouse.backend.sales.domain.payment;

import com.greenhouse.backend.sales.payment.api.PaymentTargetType;
import jakarta.persistence.*;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Identifiers for historical replay; this contains no derived amounts or payment facts. */
@Entity
@Table(
    name = "payment_target_aliases",
    uniqueConstraints = @UniqueConstraint(columnNames = {"target_type", "target_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentTargetAlias {
  @EmbeddedId private OriginalTarget originalTarget;

  @Column(name = "target_type", nullable = false)
  @Enumerated(EnumType.STRING)
  private PaymentTargetType targetType;

  @Column(name = "target_id", nullable = false)
  private Long targetId;

  @Embeddable
  @EqualsAndHashCode
  @NoArgsConstructor(access = AccessLevel.PROTECTED)
  public static class OriginalTarget implements Serializable {
    @Column(name = "original_target_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private PaymentTargetType type;

    @Column(name = "original_target_id", nullable = false)
    private Long id;
  }
}
