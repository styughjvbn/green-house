import type { PartnerSettlementSettings } from "@/entities/farm/types";

export function createSettlementFeatures(
  settings: PartnerSettlementSettings | null,
) {
  const capability = settings?.capabilities;
  const units = capability?.executableUnits ?? [];
  const unitReason = units.includes("AUCTION_DATE")
    ? "현재 실행은 경매일 단위 정산입니다. 저장한 단위가 실행 방식을 변경하지 않습니다."
    : units.includes("SALES_SLIP")
      ? "현재 실행은 판매 전표 단위입니다. 저장한 단위가 실행 방식을 변경하지 않습니다."
      : "실행 지원 정보를 확인할 수 없습니다.";
  return {
    settlementUnit: { enabled: units.length > 0, reason: unitReason },
    monthlySettlement: {
      enabled: units.includes("MONTHLY_BATCH"),
      reason: "월 정산 자동화 미구현",
    },
    salesSlipSettlement: {
      enabled: units.includes("SALES_SLIP"),
      reason: unitReason,
    },
    auctionDateSettlement: {
      enabled: units.includes("AUCTION_DATE"),
      reason: unitReason,
    },
    autoMatch: {
      enabled: capability?.autoMatching === true,
      reason: "자동 매칭 선호값만 보관하며 실행 기능은 제공하지 않습니다.",
    },
    autoSettle: {
      enabled: capability?.autoSettlement === true,
      reason: "자동 정산 선호값만 보관하며 실행 기능은 제공하지 않습니다.",
    },
    prepayment: {
      enabled: capability?.prepayment === true,
      reason: "선입금 선호값만 보관하며 예치금 처리는 제공하지 않습니다.",
    },
    autoCreditApply: {
      enabled: capability?.creditAutoApply === true,
      reason: "자동 차감 선호값만 보관하며 실행 기능은 제공하지 않습니다.",
    },
    ruleJson: {
      enabled: capability?.ruleExecution === true,
      reason: "규칙 JSON은 보관용이며 자동 수신·파싱·정산에 적용하지 않습니다.",
    },
  };
}
