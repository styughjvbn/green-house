package com.greenhouse.backend.sales.domain.direct;

import com.greenhouse.backend.common.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "direct_sales")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DirectSale extends BaseEntity {
  @Id
  @Column(name = "sales_slip_id")
  private Long documentId;

  @Version
  @Column(nullable = false)
  private Long version;

  @Column(name = "partner_id", nullable = false)
  private Long partnerId;

  @Column(name = "sale_date", nullable = false)
  private LocalDate saleDate;

  @Column(name = "total_amount", nullable = false)
  private Integer totalAmount;

  @Column(name = "expected_payment_date")
  private LocalDate expectedPaymentDate;

  @Column(name = "payment_method")
  private String paymentMethod;

  @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("documentItemId ASC")
  private List<DirectSalePrice> prices = new ArrayList<>();

  public DirectSale(
      Long documentId,
      Long partnerId,
      LocalDate date,
      LocalDate expectedDate,
      String paymentMethod,
      List<DirectSalePrice> prices) {
    if (documentId == null || partnerId == null || date == null)
      throw new IllegalArgumentException("판매 전표·거래처·거래일이 필요합니다.");
    var itemIds = new HashSet<Long>();
    for (var price : prices) {
      if (!itemIds.add(price.getDocumentItemId()))
        throw new IllegalArgumentException("판매 품목 가격은 중복될 수 없습니다.");
    }
    if (prices.isEmpty()) throw new IllegalArgumentException("일반 판매 품목은 1개 이상이어야 합니다.");
    this.documentId = documentId;
    this.partnerId = partnerId;
    this.saleDate = date;
    this.expectedPaymentDate = expectedDate;
    this.paymentMethod = paymentMethod;
    this.totalAmount =
        DirectSaleAmounts.total(prices.stream().map(DirectSalePrice::getAmount).toList());
    prices.forEach(price -> price.attach(this));
    this.prices.addAll(prices);
  }

  public long remainingAmount(long allocatedAmount) {
    return DirectSaleAmounts.remaining(totalAmount, allocatedAmount);
  }

  public void updateTerms(
      Long partnerId,
      LocalDate date,
      LocalDate expectedDate,
      String paymentMethod,
      List<DirectSalePrice> replacement) {
    if (partnerId == null || date == null) throw new IllegalArgumentException("거래처·거래일이 필요합니다.");
    var replacementIds = new HashSet<Long>();
    for (var price : replacement) {
      if (!replacementIds.add(price.getDocumentItemId()))
        throw new IllegalArgumentException("판매 품목 가격은 중복될 수 없습니다.");
    }
    if (!replacementIds.equals(
        new HashSet<>(prices.stream().map(DirectSalePrice::getDocumentItemId).toList())))
      throw new IllegalArgumentException("품목 개수 변경 수정은 아직 지원하지 않습니다.");
    int nextTotal =
        DirectSaleAmounts.total(replacement.stream().map(DirectSalePrice::getAmount).toList());
    var nextById =
        replacement.stream()
            .collect(Collectors.toMap(DirectSalePrice::getDocumentItemId, price -> price));
    for (var current : prices) {
      var next = nextById.get(current.getDocumentItemId());
      current.change(next.getPricedQuantity(), next.getUnitPrice());
    }
    this.partnerId = partnerId;
    this.saleDate = date;
    this.expectedPaymentDate = expectedDate;
    this.paymentMethod = paymentMethod;
    this.totalAmount = nextTotal;
  }

  public void requirePaymentAmount(long allocatedAmount, long amount) {
    if (amount <= 0) throw new IllegalArgumentException("입금액은 0보다 커야 합니다.");
    if (amount > remainingAmount(allocatedAmount))
      throw new IllegalArgumentException("입금액이 현재 잔액을 초과할 수 없습니다.");
  }
}
