import assert from "node:assert/strict";
import test from "node:test";
import { createSettlementFeatures } from "../src/features/sales/model/settlementCapabilities.ts";

test("stored monthly and automation preferences do not enable unsupported actions", () => {
  const features = createSettlementFeatures({
    settlementUnit: "MONTHLY_BATCH",
    autoMatchEnabled: true,
    autoSettleEnabled: true,
    allowPrepayment: true,
    creditAutoApplyEnabled: true,
    ruleJson: { enabled: true },
    capabilities: {
      executableUnits: ["AUCTION_DATE"],
      autoMatching: false,
      autoSettlement: false,
      prepayment: false,
      creditAutoApply: false,
      ruleExecution: false,
    },
  });
  assert.equal(features.auctionDateSettlement.enabled, true);
  for (const name of [
    "monthlySettlement",
    "salesSlipSettlement",
    "autoMatch",
    "autoSettle",
    "prepayment",
    "autoCreditApply",
    "ruleJson",
  ]) {
    assert.equal(features[name].enabled, false, name);
  }
  assert.match(features.settlementUnit.reason, /경매일/);
  assert.match(features.autoSettle.reason, /선호값만 보관/);
});

test("execution choices follow the server independently of the saved unit", () => {
  const features = createSettlementFeatures({
    settlementUnit: "AUCTION_DATE",
    capabilities: { executableUnits: ["SALES_SLIP"] },
  });
  assert.equal(features.salesSlipSettlement.enabled, true);
  assert.equal(features.auctionDateSettlement.enabled, false);
  assert.match(features.settlementUnit.reason, /판매 전표/);
});

test("missing capability information keeps actions disabled", () => {
  for (const settings of [
    null,
    { settlementUnit: "SALES_SLIP", autoSettleEnabled: true },
  ]) {
    const features = createSettlementFeatures(settings);
    assert.ok(Object.values(features).every((feature) => !feature.enabled));
    assert.match(features.settlementUnit.reason, /확인할 수 없습니다/);
  }
});
