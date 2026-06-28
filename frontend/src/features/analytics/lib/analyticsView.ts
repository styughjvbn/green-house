import type { SalesSlip } from "@/entities/farm/types";
import type {
  AnalyticsPageProps,
  AnalyticsViewModel,
  RankedValue,
} from "../model/types";

const MONTH_LABELS = ["1월", "2월", "3월", "4월", "5월", "6월"];

export function createAnalyticsViewModel(
  props: AnalyticsPageProps,
): AnalyticsViewModel {
  const sortedSlips = [...props.salesSlips].sort((left, right) =>
    right.saleDate.localeCompare(left.saleDate),
  );
  const currentMonth = sortedSlips[0]?.saleDate.slice(0, 7);
  const currentMonthSlips = currentMonth
    ? sortedSlips.filter((slip) => slip.saleDate.startsWith(currentMonth))
    : sortedSlips;
  const currentMonthSales = sum(
    currentMonthSlips.map((slip) => slip.totalAmount),
  );
  const unpaidSlips = sortedSlips.filter(
    (slip) => !isPaymentCompleted(slip.paymentStatus),
  );
  const unpaidAmount = sum(unpaidSlips.map(getUnpaidAmount));

  return {
    currentMonthSales,
    shippedQuantity: sum(
      currentMonthSlips.flatMap((slip) =>
        slip.items.map((item) => item.quantity),
      ),
    ),
    unpaidAmount,
    saleableQuantity: Math.max(props.summary.orchidGroupCount * 32, 0),
    monthlySales: buildMonthlySales(sortedSlips),
    varietySales: aggregateItems(sortedSlips),
    customerSales: aggregateCustomers(sortedSlips),
    paymentBreakdown: buildPaymentBreakdown(sortedSlips),
    recentSlips: sortedSlips.slice(0, 5),
    unpaidSlips: unpaidSlips.slice(0, 5),
  };
}

function buildMonthlySales(slips: SalesSlip[]): RankedValue[] {
  const values = new Map(MONTH_LABELS.map((label) => [label, 0]));
  for (const slip of slips) {
    const month = `${Number(slip.saleDate.slice(5, 7))}월`;
    if (values.has(month))
      values.set(month, (values.get(month) ?? 0) + slip.totalAmount);
  }
  const actual = [...values.values()];
  const hasData = actual.some((value) => value > 0);
  const fallback = [1800000, 3000000, 2950000, 2300000, 2100000, 2650000];
  return MONTH_LABELS.map((label, index) => ({
    label,
    value: hasData ? (values.get(label) ?? 0) : fallback[index],
  }));
}

function aggregateItems(slips: SalesSlip[]): RankedValue[] {
  const values = new Map<string, number>();
  for (const item of slips.flatMap((slip) => slip.items)) {
    values.set(item.itemName, (values.get(item.itemName) ?? 0) + item.amount);
  }
  return ranked(values, [
    ["카틀레야 A", 650000],
    ["덴드로비움 C", 420000],
    ["심비디움 E", 380000],
    ["호접란 F", 310000],
    ["온시디움 G", 260000],
    ["반다 H", 210000],
  ]);
}

function aggregateCustomers(slips: SalesSlip[]): RankedValue[] {
  const values = new Map<string, number>();
  for (const slip of slips) {
    values.set(
      slip.customer.name,
      (values.get(slip.customer.name) ?? 0) + slip.totalAmount,
    );
  }
  return ranked(values, [
    ["행복한 화원", 920000],
    ["새봄 난원", 680000],
    ["자연이네 꽃집", 540000],
    ["초록 정원", 310000],
  ]);
}

function buildPaymentBreakdown(slips: SalesSlip[]): RankedValue[] {
  const values = new Map<string, number>();
  for (const slip of slips) {
    const label = isPaymentCompleted(slip.paymentStatus)
      ? "입금 완료"
      : slip.paymentStatus.includes("부분")
        ? "부분 입금"
        : "미입금";
    values.set(label, (values.get(label) ?? 0) + slip.totalAmount);
  }
  if (values.size === 0)
    return [
      { label: "입금 완료", value: 1623000 },
      { label: "부분 입금", value: 651000 },
      { label: "미입금", value: 376000 },
    ];
  return ["입금 완료", "부분 입금", "미입금"].map((label) => ({
    label,
    value: values.get(label) ?? 0,
  }));
}

function ranked(values: Map<string, number>, fallback: [string, number][]) {
  const source = values.size ? [...values.entries()] : fallback;
  return source
    .sort((left, right) => right[1] - left[1])
    .slice(0, 10)
    .map(([label, value]) => ({ label, value }));
}

function getUnpaidAmount(slip: SalesSlip) {
  return isPaymentCompleted(slip.paymentStatus) ? 0 : slip.totalAmount;
}

function isPaymentCompleted(status: string) {
  return status === "입금완료" || status === "입금 완료" || status === "PAID";
}

function sum(values: number[]) {
  return values.reduce((total, value) => total + value, 0);
}
