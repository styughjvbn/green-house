package com.greenhouse.backend.sales.domain.direct;

import com.greenhouse.backend.common.exception.ConflictException;
import java.math.BigDecimal;
import java.util.Collection;

/** Exact arithmetic for newly agreed direct-sale amounts; never recalculates migrated facts. */
public final class DirectSaleAmounts {
  private DirectSaleAmounts() {}

  public static void requireReviewCleared(boolean reviewRequired) {
    if (reviewRequired)
      throw new ConflictException(
          "DIRECT_AMOUNT_REVIEW_REQUIRED", "과거 금액과 입금 연결의 검토가 끝나기 전에는 새 입금이나 금액 변경을 할 수 없습니다.");
  }

  public static boolean isPaymentAllowed(int total, BigDecimal allocated, boolean reviewRequired) {
    return !reviewRequired
        && allocated.signum() >= 0
        && allocated.compareTo(BigDecimal.valueOf(total)) < 0;
  }

  public static String paymentStatus(int total, BigDecimal allocated) {
    return paymentStatus(total, allocated, "미입금");
  }

  public static String paymentStatus(int total, BigDecimal allocated, String unpaidLabel) {
    if (allocated.signum() == 0) return unpaidLabel;
    return allocated.compareTo(BigDecimal.valueOf(total)) >= 0 ? "입금 완료" : "부분입금";
  }

  public static int price(Integer quantity, Integer unitPrice) {
    if (quantity == null || quantity <= 0)
      throw new IllegalArgumentException("판매 수량은 1 이상이어야 합니다.");
    if (unitPrice == null || unitPrice < 0)
      throw new IllegalArgumentException("판매 단가는 0 이상이어야 합니다.");
    try {
      return Math.multiplyExact(quantity, unitPrice);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("판매 품목 금액은 2,147,483,647원 이하여야 합니다.", exception);
    }
  }

  public static int total(Collection<Integer> amounts) {
    int total = 0;
    for (Integer amount : amounts) {
      if (amount == null || amount < 0)
        throw new IllegalArgumentException("판매 품목 금액은 0 이상이어야 합니다.");
      try {
        total = Math.addExact(total, amount);
      } catch (ArithmeticException exception) {
        throw new IllegalArgumentException("판매 전표 금액은 2,147,483,647원 이하여야 합니다.", exception);
      }
    }
    return total;
  }

  public static long remaining(int total, long allocated) {
    if (allocated < 0) throw new IllegalArgumentException("유효 배분액은 0 이상이어야 합니다.");
    return Math.max(0L, Math.subtractExact((long) total, allocated));
  }
}
