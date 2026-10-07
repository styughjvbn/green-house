package com.greenhouse.backend.sales.application.auction.settlement;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// TODO(ADR-003): 결과 기반 조회·입금 대상 전환 뒤 파생 정산과 이 시작 실행기를 제거한다.
// 별도 일괄 재구성 기능이나 파생 이력 보관 모델은 추가하지 않는다.
// 시작 경로의 성능 최적화는 보류한다. 조사 근거: backend-audit/archive/14-performance-diagnosis.md
@Component
@ConditionalOnProperty(
    name = "app.settlement.rebuild-on-startup",
    havingValue = "true",
    matchIfMissing = true)
@RequiredArgsConstructor
public class AuctionSettlementInitializer implements ApplicationRunner {

  private final AuctionSettlementRebuildService settlementService;

  @Override
  public void run(ApplicationArguments args) {
    settlementService.rebuildExistingResults();
  }
}
