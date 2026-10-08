package com.greenhouse.backend.sales.domain.auction;

import com.greenhouse.backend.common.domain.BaseEntity;
import com.greenhouse.backend.common.exception.ConflictException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** An evidenced payment target, never a fee calculation or a daily settlement rebuild. */
@Entity
@Table(name = "auction_proceeds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuctionProceeds extends BaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "auction_proceeds_id_seq")
  @SequenceGenerator(
      name = "auction_proceeds_id_seq",
      sequenceName = "auction_proceeds_id_seq",
      allocationSize = 50)
  private Long id;

  @Version
  @Column(nullable = false)
  private Long version;

  @Column(name = "auction_house_id", nullable = false)
  private Long auctionHouseId;

  @Column(name = "source_reference", columnDefinition = "text")
  private String sourceReference;

  @Column(name = "reported_gross_amount")
  private Long reportedGrossAmount;

  @Column(name = "receivable_amount")
  private Long receivableAmount;

  @Column(name = "matching_confirmed", nullable = false)
  private boolean matchingConfirmed;

  @Column(name = "confirmed_by")
  private String confirmedBy;

  @Column(name = "confirmed_at")
  private LocalDateTime confirmedAt;

  @OneToMany(mappedBy = "proceeds", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("resultLineId ASC")
  private List<AuctionProceedsResult> results = new ArrayList<>();

  public AuctionProceeds(
      Long auctionHouseId,
      String sourceReference,
      Long reportedGrossAmount,
      Long receivableAmount,
      List<Long> resultIds) {
    if (auctionHouseId == null) throw new IllegalArgumentException("대금 자료의 경매장이 필요합니다.");
    requireNonNegative(reportedGrossAmount);
    requireNonNegative(receivableAmount);
    this.auctionHouseId = auctionHouseId;
    this.sourceReference =
        sourceReference == null || sourceReference.isBlank() ? null : sourceReference.trim();
    this.reportedGrossAmount = reportedGrossAmount;
    // A gross-only report does not establish a net receivable.
    this.receivableAmount = receivableAmount;
    if (resultIds == null) throw new IllegalArgumentException("대금 자료의 결과 참조가 필요합니다.");
    var ids = new HashSet<Long>();
    for (Long resultId : resultIds) {
      if (resultId == null || !ids.add(resultId))
        throw new IllegalArgumentException("대금 자료의 결과 참조는 중복될 수 없습니다.");
      results.add(new AuctionProceedsResult(this, resultId));
    }
  }

  public void confirmMatching(long matchedGrossAmount, String worker, LocalDateTime confirmedAt) {
    if (sourceReference == null || reportedGrossAmount == null || results.isEmpty())
      throw new ConflictException(
          "AUCTION_PROCEEDS_EVIDENCE_REQUIRED", "근거 자료와 연결된 결과를 먼저 확인해야 합니다.");
    if (reportedGrossAmount != matchedGrossAmount)
      throw new ConflictException(
          "AUCTION_PROCEEDS_MATCH_INCOMPLETE", "자료의 경락 금액과 연결 결과의 금액이 일치하지 않습니다.");
    if (confirmedAt == null) throw new IllegalArgumentException("자료 연결 확인 시각이 필요합니다.");
    if (matchingConfirmed) return;
    this.matchingConfirmed = true;
    this.confirmedBy = worker;
    this.confirmedAt = confirmedAt;
  }

  public boolean isPaymentTargetReady() {
    return matchingConfirmed && receivableAmount != null;
  }

  public BigDecimal remainingAmount(BigDecimal validAllocations) {
    if (!isPaymentTargetReady())
      throw new ConflictException("AUCTION_PROCEEDS_NOT_READY", "자료 연결과 받을 금액 확인을 완료해야 합니다.");
    if (validAllocations == null || validAllocations.signum() < 0)
      throw new IllegalArgumentException("유효 배분액은 0 이상이어야 합니다.");
    return BigDecimal.valueOf(receivableAmount).subtract(validAllocations).max(BigDecimal.ZERO);
  }

  public boolean isAllocationAllowed(BigDecimal validAllocations, boolean reviewRequired) {
    return isPaymentTargetReady()
        && !reviewRequired
        && remainingAmount(validAllocations).signum() > 0;
  }

  public void requireAllocation(BigDecimal validAllocations, long amount, boolean reviewRequired) {
    if (reviewRequired)
      throw new ConflictException("AUCTION_PROCEEDS_ALLOCATION_REVIEW", "기존 입금 연결을 먼저 검토해야 합니다.");
    requireAllocation(validAllocations, amount);
  }

  public void requireAllocation(BigDecimal validAllocations, long amount) {
    var remaining = remainingAmount(validAllocations);
    if (amount <= 0) throw new IllegalArgumentException("입금액은 0원보다 커야 합니다.");
    if (BigDecimal.valueOf(amount).compareTo(remaining) > 0)
      throw new IllegalArgumentException("입금액은 현재 잔액을 초과할 수 없습니다.");
  }

  private static void requireNonNegative(Long amount) {
    if (amount != null && amount < 0) throw new IllegalArgumentException("대금 자료의 금액은 0 이상이어야 합니다.");
  }
}
