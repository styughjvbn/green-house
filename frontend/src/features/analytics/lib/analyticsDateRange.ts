import type { AnalyticsFilters } from "../model/types";

export function readAnalyticsDateRange(
  from: string | undefined,
  to: string | undefined,
  today = new Date(),
): AnalyticsFilters {
  const defaults = defaultAnalyticsDateRange(today);
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
  today = new Date(),
): AnalyticsFilters {
  return {
    dateFrom: formatDate(
      new Date(today.getFullYear(), today.getMonth() - 11, 1),
    ),
    dateTo: formatDate(today),
  };
}

function isIsoDate(value?: string): value is string {
  if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const [year, month, day] = value.split("-").map(Number);
  const date = new Date(year, month - 1, day);
  return (
    date.getFullYear() === year &&
    date.getMonth() === month - 1 &&
    date.getDate() === day
  );
}

function exceedsTwoYears(from: string, to: string) {
  const [toYear, toMonth, toDay] = to.split("-").map(Number);
  const minimum = new Date(toYear - 2, toMonth - 1, toDay);
  if (minimum.getMonth() !== toMonth - 1) {
    minimum.setDate(0);
  }
  return new Date(`${from}T00:00:00`) < minimum;
}

function formatDate(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}
