import type {
  DashboardSummary,
  FarmStatusMapData,
  SalesSlip,
  WorkRecord,
} from "@/entities/farm/types";

export type DashboardPageProps = {
  mapData: FarmStatusMapData;
  salesSlips: SalesSlip[];
  summary: DashboardSummary;
  workRecords: WorkRecord[];
};

export type DashboardTone = "green" | "orange" | "red";
