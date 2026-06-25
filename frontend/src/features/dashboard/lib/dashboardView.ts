import type { SalesSlip, WorkRecord } from "@/entities/farm/types";
import type { DashboardPageProps, DashboardTone } from "../model/types";

export function createDashboardViewModel({
  mapData,
  salesSlips,
  summary,
  workRecords,
}: DashboardPageProps) {
  const warningHouses = mapData.houses.filter(
    (house) => house.warningCount > 0,
  );

  return {
    recentSalesSlips: salesSlips.slice(0, 5),
    recentWorkRecords: workRecords.slice(0, 5),
    salesTotal: salesSlips.reduce((sum, slip) => sum + slip.totalAmount, 0),
    unpaidCount: salesSlips.filter((slip) => slip.paymentStatus === "미입금")
      .length,
    warningHouses,
    summaryCards: {
      houses: [
        [
          "정상",
          Math.max(summary.houseCount - warningHouses.length, 0),
          "green",
        ],
        ["주의", warningHouses.length, "orange"],
        ["이상", summary.warningCount, "red"],
      ] as SummaryDetail,
      physicalBeds: [
        [
          "정상",
          Math.max(summary.physicalBedCount - summary.warningCount, 0),
          "green",
        ],
        ["주의", summary.warningCount, "orange"],
        ["이상", 0, "red"],
      ] as SummaryDetail,
      bedZones: [
        [
          "정상",
          Math.max(summary.bedZoneCount - summary.warningCount, 0),
          "green",
        ],
        ["주의", summary.warningCount, "orange"],
        ["이상", 0, "red"],
      ] as SummaryDetail,
    },
  };
}

export type SummaryDetail = Array<[string, number, DashboardTone]>;

export function getHouseTone(warningCount: number): DashboardTone {
  if (warningCount > 1) {
    return "red";
  }
  if (warningCount > 0) {
    return "orange";
  }
  return "green";
}

export function getPaymentStatusLabel(slip: SalesSlip) {
  return slip.paymentStatus === "미입금" ? "미입금" : "입금";
}

export function getRecentWorkLabel(record: WorkRecord) {
  return record.memo ?? record.materialName ?? "농장 전체";
}

export function toneText(tone: DashboardTone) {
  return {
    green: "text-[#159447]",
    orange: "text-[#f59e0b]",
    red: "text-[#e52d2d]",
  }[tone];
}

export function dotClass(tone: DashboardTone) {
  return {
    green: "bg-[#159447]",
    orange: "bg-[#f59e0b]",
    red: "bg-[#e52d2d]",
  }[tone];
}

export function houseToneClass(tone: DashboardTone) {
  return {
    green: "border-[#b8dbc0] bg-[#eef8ef] text-[#0d6d31]",
    orange: "border-[#f5d383] bg-[#fff8e8] text-[#f59e0b]",
    red: "border-[#ffb1b1] bg-[#fff0f0] text-[#e52d2d]",
  }[tone];
}

export function toneBorder(tone: "blue" | "orange" | "red") {
  return {
    blue: "border-[#8dbdff] text-[#246df2]",
    orange: "border-[#ffc66d] text-[#f59e0b]",
    red: "border-[#ff9c9c] text-[#e52d2d]",
  }[tone];
}
