package com.greenhouse.backend.settlement.application;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// TODO: 별도 일괄 정산 기능을 구현한 뒤 이 시작 시 실행기와 rebuild-on-startup 설정을 제거한다.
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
