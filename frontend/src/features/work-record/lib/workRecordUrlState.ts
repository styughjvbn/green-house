import type { WorkOperationStatus } from "@/entities/farm/types";
import type { WorkOperationFilterState } from "../model/useWorkOperations";

export type WorkWorkspaceScope = "MANAGEMENT" | "ALL";
export type WorkWorkspaceView = "LIST" | "CALENDAR";

export type WorkRecordUrlState = {
  filters: WorkOperationFilterState;
  month: string;
  page: number;
  scope: WorkWorkspaceScope;
  size: number;
  view: WorkWorkspaceView;
};

export const WORK_LIST_FILTER_KEYS = [
  "from",
  "to",
  "status",
  "keyword",
] as const;

const WORK_STATUSES: WorkOperationStatus[] = [
  "PLANNED",
  "IN_PROGRESS",
  "PAUSED",
  "COMPLETED",
  "CORRECTED",
  "CANCELED",
];

type SearchParamsReader = Pick<URLSearchParams, "get">;

export function readWorkRecordUrlState(
  params: SearchParamsReader,
  defaultMonth = currentMonth(),
): WorkRecordUrlState {
  return {
    filters: {
      from: params.get("from") ?? "",
      to: params.get("to") ?? "",
      status: readStatus(params.get("status")),
      keyword: params.get("keyword") ?? "",
    },
    month: readMonth(params.get("month"), defaultMonth),
    page: readBoundedInteger(params.get("page"), 0, 0, Number.MAX_SAFE_INTEGER),
    scope: params.get("scope") === "ALL" ? "ALL" : "MANAGEMENT",
    size: readBoundedInteger(params.get("size"), 20, 1, 100),
    view: params.get("view") === "CALENDAR" ? "CALENDAR" : "LIST",
  };
}

export function writeWorkListFilterParams(
  params: URLSearchParams,
  filters: WorkOperationFilterState,
) {
  setParam(params, "from", filters.from);
  setParam(params, "to", filters.to);
  setParam(params, "status", filters.status);
  setParam(params, "keyword", filters.keyword);
}

export function setWorkWorkspaceScope(
  params: URLSearchParams,
  scope: WorkWorkspaceScope,
) {
  params.set("scope", scope);
  params.set("page", "0");
}

export function setWorkWorkspaceView(
  params: URLSearchParams,
  view: WorkWorkspaceView,
) {
  params.set("view", view);
}

export function currentMonth() {
  const date = new Date();
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  return `${year}-${month}`;
}

function readStatus(value: string | null): WorkOperationStatus | "" {
  return WORK_STATUSES.includes(value as WorkOperationStatus)
    ? (value as WorkOperationStatus)
    : "";
}

function readMonth(value: string | null, fallback: string) {
  if (!value || !/^\d{4}-(0[1-9]|1[0-2])$/.test(value)) return fallback;
  return value;
}

function readBoundedInteger(
  value: string | null,
  fallback: number,
  minimum: number,
  maximum: number,
) {
  const parsed = value == null ? fallback : Number(value);
  if (!Number.isInteger(parsed)) return fallback;
  return Math.min(Math.max(parsed, minimum), maximum);
}

function setParam(params: URLSearchParams, key: string, value: string) {
  const normalized = value.trim();
  if (normalized) params.set(key, normalized);
  else params.delete(key);
}
