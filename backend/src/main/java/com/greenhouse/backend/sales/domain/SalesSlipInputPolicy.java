package com.greenhouse.backend.sales.domain;

/** Shared input rules; edit eligibility and stock allocation remain separate policies. */
public final class SalesSlipInputPolicy {

  private SalesSlipInputPolicy() {}

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
