import type { SalesSlip } from "@/entities/farm/types";
import type {
  AnalyticsPageProps,
  SalesInsight,
  AnalyticsViewModel,
  PartnerAnalyticsStat,
  RankedValue,
} from "../model/types";

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
  const partnerStats = buildPartnerStats(props);

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
    partnerSales: aggregateBusinessPartners(sortedSlips),
    partnerStats,
    paymentBreakdown: buildPaymentBreakdown(sortedSlips),
    recentSlips: sortedSlips.slice(0, 5),
    salesInsights: buildSalesInsights({
      partnerStats,
      salesSlips: sortedSlips,
      unpaidAmount,
    }),
    unpaidSlips: unpaidSlips.slice(0, 5),
  };
}

function buildMonthlySales(slips: SalesSlip[]): RankedValue[] {
  const monthKeys = buildRecentMonthKeys(slips, 6);
  const values = new Map(monthKeys.map((key) => [key, 0]));
  for (const slip of slips) {
    const month = slip.saleDate.slice(0, 7);
    if (values.has(month)) {
      values.set(month, (values.get(month) ?? 0) + slip.totalAmount);
    }
  }
  return monthKeys.map((key) => ({
    label: `${Number(key.slice(5, 7))}월`,
    value: values.get(key) ?? 0,
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

function aggregateBusinessPartners(slips: SalesSlip[]): RankedValue[] {
  const values = new Map<string, number>();
  for (const slip of slips) {
    values.set(
      slip.partner.name,
      (values.get(slip.partner.name) ?? 0) + slip.totalAmount,
    );
  }
  return ranked(values, []);
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
  if (values.size === 0) {
    return [
      { label: "입금 완료", value: 0 },
      { label: "부분 입금", value: 0 },
      { label: "미입금", value: 0 },
    ];
  }
  return ["입금 완료", "부분 입금", "미입금"].map((label) => ({
    label,
    value: values.get(label) ?? 0,
  }));
}

function buildPartnerStats(props: AnalyticsPageProps): PartnerAnalyticsStat[] {
  const salesByPartner = new Map<number, SalesSlip[]>();
  for (const slip of props.salesSlips) {
    const current = salesByPartner.get(slip.partner.id) ?? [];
    current.push(slip);
    salesByPartner.set(slip.partner.id, current);
  }

  return props.businessPartners
    .map((partner) => {
      const slips = salesByPartner.get(partner.id) ?? [];
      const balance = props.partnerBalances.find(
        (item) => item.partnerId === partner.id,
      );
      const latestSaleDate =
        slips
          .map((slip) => slip.saleDate)
          .sort((left, right) => right.localeCompare(left))[0] ?? null;

      return {
        partnerId: partner.id,
        partnerName: partner.name,
        partnerType: partner.partnerType,
        totalSales: sum(slips.map((slip) => slip.totalAmount)),
        transactionCount: slips.length,
        unpaidAmount: sum(slips.map((slip) => slip.remainingAmount)),
        paidAmount: sum(slips.map((slip) => slip.paidAmount)),
        receivableBalance: balance?.receivableBalance ?? 0,
        creditBalance: balance?.creditBalance ?? 0,
        unappliedPaymentAmount: balance?.unappliedPaymentAmount ?? 0,
        latestSaleDate,
      };
    })
    .filter(
      (item) =>
        item.transactionCount > 0 ||
        item.receivableBalance > 0 ||
        item.creditBalance > 0 ||
        item.unappliedPaymentAmount > 0,
    )
    .sort((left, right) => {
      if (right.totalSales !== left.totalSales) {
        return right.totalSales - left.totalSales;
      }
      return right.transactionCount - left.transactionCount;
    });
}

function buildSalesInsights({
  partnerStats,
  salesSlips,
  unpaidAmount,
}: {
  partnerStats: PartnerAnalyticsStat[];
  salesSlips: SalesSlip[];
  unpaidAmount: number;
}): SalesInsight[] {
  const insights: SalesInsight[] = [];
  const monthKeys = buildRecentMonthKeys(salesSlips, 2);
  const [previousMonthKey, currentMonthKey] = monthKeys;
  const previousMonthSales = sum(
    salesSlips
      .filter((slip) => slip.saleDate.startsWith(previousMonthKey))
      .map((slip) => slip.totalAmount),
  );
  const currentMonthSales = sum(
    salesSlips
      .filter((slip) => slip.saleDate.startsWith(currentMonthKey))
      .map((slip) => slip.totalAmount),
  );
  const previousMonthQty = sum(
    salesSlips
      .filter((slip) => slip.saleDate.startsWith(previousMonthKey))
      .flatMap((slip) => slip.items.map((item) => item.quantity)),
  );
  const currentMonthQty = sum(
    salesSlips
      .filter((slip) => slip.saleDate.startsWith(currentMonthKey))
      .flatMap((slip) => slip.items.map((item) => item.quantity)),
  );
  const topPartner = partnerStats[0];
  const unpaidCount = salesSlips.filter(
    (slip) => slip.remainingAmount > 0,
  ).length;

  if (currentMonthKey) {
    insights.push({
      tone: currentMonthSales >= previousMonthSales ? "green" : "orange",
      text:
        previousMonthSales > 0
          ? `${formatMonthLabel(currentMonthKey)} 매출 ${formatSignedRate(changeRate(previousMonthSales, currentMonthSales))}`
          : `${formatMonthLabel(currentMonthKey)} 매출 ${currentMonthSales.toLocaleString()}원`,
    });
    insights.push({
      tone: currentMonthQty >= previousMonthQty ? "blue" : "orange",
      text:
        previousMonthQty > 0
          ? `${formatMonthLabel(currentMonthKey)} 출하 ${formatSignedRate(changeRate(previousMonthQty, currentMonthQty))}`
          : `${formatMonthLabel(currentMonthKey)} 출하 ${currentMonthQty.toLocaleString()}분`,
    });
  }

  if (topPartner) {
    insights.push({
      tone: "green",
      text: `최다 거래처 ${topPartner.partnerName} · 매출 ${topPartner.totalSales.toLocaleString()}원`,
      actionLabel: "거래처 보기",
      actionHref: "/analytics?tab=CUSTOMER",
    });
  }

  if (unpaidCount > 0 || unpaidAmount > 0) {
    insights.push({
      tone: "red",
      text: `미입금 전표 ${unpaidCount}건 · 미수 ${unpaidAmount.toLocaleString()}원`,
      actionLabel: "판매 관리",
      actionHref: "/sales",
    });
  }

  if (!insights.length) {
    insights.push({
      tone: "blue",
      text: "표시할 판매 데이터가 없습니다.",
    });
  }

  return insights.slice(0, 4);
}

function ranked(values: Map<string, number>, fallback: [string, number][]) {
  const source = values.size ? [...values.entries()] : fallback;
  return source
    .sort((left, right) => right[1] - left[1])
    .slice(0, 10)
    .map(([label, value]) => ({ label, value }));
}

function getUnpaidAmount(slip: SalesSlip) {
  return slip.remainingAmount > 0 ? slip.remainingAmount : 0;
}

function isPaymentCompleted(status: string) {
  return status === "입금완료" || status === "입금 완료" || status === "PAID";
}

function sum(values: number[]) {
  return values.reduce((total, value) => total + value, 0);
}

function buildRecentMonthKeys(slips: SalesSlip[], length: number) {
  const base = slips[0]?.saleDate
    ? new Date(`${slips[0].saleDate}T00:00:00`)
    : new Date();
  const year = base.getFullYear();
  const month = base.getMonth();
  return Array.from({ length }, (_, index) => {
    const date = new Date(year, month - (length - 1 - index), 1);
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, "0");
    return `${y}-${m}`;
  });
}

function changeRate(previous: number, current: number) {
  if (previous === 0) {
    return current > 0 ? 100 : 0;
  }
  return ((current - previous) / previous) * 100;
}

function formatSignedRate(value: number) {
  const sign = value > 0 ? "증가" : value < 0 ? "감소" : "유지";
  return `${Math.abs(value).toFixed(1)}% ${sign}`;
}

function formatMonthLabel(monthKey: string) {
  return `${Number(monthKey.slice(5, 7))}월`;
}
