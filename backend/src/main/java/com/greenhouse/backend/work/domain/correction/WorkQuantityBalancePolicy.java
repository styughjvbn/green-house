package com.greenhouse.backend.work.domain.correction;

public final class WorkQuantityBalancePolicy {

  private WorkQuantityBalancePolicy() {}

  public static void validate(
      long input, long result, long loss, long increase, boolean allowsIncrease) {
    if (input < 0
        || result < 0
        || loss < 0
        || increase < 0
        || input > Integer.MAX_VALUE
        || result > Integer.MAX_VALUE
        || loss > Integer.MAX_VALUE
        || increase > Integer.MAX_VALUE)
      throw new IllegalArgumentException("작업 수량이 유효한 범위를 벗어났습니다.");
    if (!allowsIncrease && increase > 0)
      throw new IllegalArgumentException("이 작업은 증식 수량을 기록할 수 없습니다. 원본 입력 오류 또는 실사 차이인지 확인하세요.");
    if (input + increase != result + loss)
      throw new IllegalArgumentException("투입 + 증식 = 결과 + 손실이 되어야 합니다. 원본 수량은 자동 변경하지 않습니다.");
  }
}
