package com.greenhouse.backend.sales.document.domain;

import com.greenhouse.backend.sales.api.document.SalesType;
import java.util.Set;

/** Shared input rules; edit eligibility and stock allocation remain separate policies. */
public final class SalesSlipInputPolicy {

  private SalesSlipInputPolicy() {}

  public static void requireCreationReceipt(SalesType type, boolean hasReceipt, String status) {
    if (hasReceipt && type != SalesType.DIRECT)
      throw new IllegalArgumentException("경매 전표 수납은 경매 정산에서 기록해야 합니다.");
    if (status != null && Set.of("입금 완료", "부분입금", "부분 입금").contains(status.trim()))
      throw new IllegalArgumentException("수납 상태는 실제 수납·배분 기록으로 결정됩니다.");
  }

  public static void requirePartner(SalesType type, Long partnerId) {
    if (partnerId == null) {
      throw new IllegalArgumentException(
          type == SalesType.DIRECT ? "일반 판매는 거래처를 선택해야 합니다." : "경매 판매는 경매장을 선택해야 합니다.");
    }
  }

  public static void requireItems(SalesType type, int itemCount) {
    if (itemCount <= 0) {
      throw new IllegalArgumentException(
          type == SalesType.DIRECT ? "일반 판매 품목은 1개 이상 입력해야 합니다." : "경매 판매는 1개 이상의 lot 품목이 필요합니다.");
    }
  }

  public static void requirePartnerType(SalesType type, boolean auctionHouse) {
    if (type == SalesType.DIRECT && auctionHouse) {
      throw new IllegalArgumentException("경매장 거래처는 경매 판매 전표에서 사용해야 합니다.");
    }
    if (type == SalesType.AUCTION && !auctionHouse) {
      throw new IllegalArgumentException("경매 판매는 경매장 거래처만 선택할 수 있습니다.");
    }
  }
}
