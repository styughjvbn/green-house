package com.greenhouse.backend.sales.domain.direct;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "direct_sale_prices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DirectSalePrice {
  @Id
  @Column(name = "sales_slip_item_id")
  private Long documentItemId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "sales_slip_id", nullable = false)
  private DirectSale sale;

  @Column(name = "priced_quantity", nullable = false)
  private Integer pricedQuantity;

  @Column(name = "unit_price", nullable = false)
  private Integer unitPrice;

  @Column(nullable = false)
  private Integer amount;

  public DirectSalePrice(Long documentItemId, Integer quantity, Integer unitPrice) {
    if (documentItemId == null) throw new IllegalArgumentException("가격 대상 전표 품목이 필요합니다.");
    this.documentItemId = documentItemId;
    change(quantity, unitPrice);
  }

  void change(Integer quantity, Integer unitPrice) {
    int nextAmount = DirectSaleAmounts.price(quantity, unitPrice);
    this.pricedQuantity = quantity;
    this.unitPrice = unitPrice;
    this.amount = nextAmount;
  }

  void attach(DirectSale sale) {
    this.sale = sale;
  }
}
