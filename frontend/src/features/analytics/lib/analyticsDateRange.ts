import type { AnalyticsFilters } from "../model/types";

export function readAnalyticsDateRange(
  from: string | undefined,
  to: string | undefined,
  businessDate: string,
): AnalyticsFilters {
  const defaults = defaultAnalyticsDateRange(businessDate);
  if (!isIsoDate(from) || !isIsoDate(to)) return defaults;
  if (!isAnalyticsDateRangeValid(from, to)) return defaults;
  return { dateFrom: from, dateTo: to };
}

export function isAnalyticsDateRangeValid(from: string, to: string) {
  return (
    isIsoDate(from) && isIsoDate(to) && from <= to && !exceedsTwoYears(from, to)
  );
}

export function defaultAnalyticsDateRange(
  businessDate: string,
): AnalyticsFilters {
  const [year, month] = businessDate.split("-").map(Number);
  const startMonthIndex = year * 12 + month - 1 - 11;
  const startYear = Math.floor(startMonthIndex / 12);
  const startMonth = (startMonthIndex % 12) + 1;
  return {
    dateFrom: `${startYear}-${String(startMonth).padStart(2, "0")}-01`,
    dateTo: businessDate,
  };
}

function isIsoDate(value?: string): value is string {
  if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const [year, month, day] = value.split("-").map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  return (
    date.getUTCFullYear() === year &&
    date.getUTCMonth() === month - 1 &&
    date.getUTCDate() === day
  );
}

function exceedsTwoYears(from: string, to: string) {
  const [toYear, toMonth, toDay] = to.split("-").map(Number);
  const minimum = new Date(Date.UTC(toYear - 2, toMonth - 1, toDay));
  if (minimum.getUTCMonth() !== toMonth - 1) {
    minimum.setUTCDate(0);
  }
  return new Date(`${from}T00:00:00Z`) < minimum;
}
