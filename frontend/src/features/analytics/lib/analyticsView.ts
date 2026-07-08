import type { AnalyticsPageProps, AnalyticsViewModel } from "../model/types";

export function createAnalyticsViewModel(
  props: AnalyticsPageProps,
): AnalyticsViewModel {
  return {
    currentMonthSales: props.salesAnalytics?.currentMonthSales ?? 0,
    shippedQuantity: props.salesAnalytics?.shippedQuantity ?? 0,
    unpaidAmount: props.salesAnalytics?.unpaidAmount ?? 0,
    saleableQuantity: Math.max(props.summary.orchidGroupCount * 32, 0),
    monthlySales: props.salesAnalytics?.monthlySales ?? emptyMonthlySales(),
    varietySales: props.salesAnalytics?.varietySales ?? [],
    partnerSales:
      props.partnerAnalytics?.partnerSales ??
      props.salesAnalytics?.partnerSales ??
      [],
    partnerStats: props.partnerAnalytics?.partnerStats ?? [],
    paymentBreakdown: props.salesAnalytics?.paymentBreakdown ?? [
      { label: "입금 완료", value: 0 },
      { label: "부분입금", value: 0 },
      { label: "미입금", value: 0 },
    ],
    recentSlips: props.salesAnalytics?.recentSlips ?? [],
    salesInsights: props.salesAnalytics?.salesInsights ?? [
      {
        tone: "blue",
        text: "표시할 판매 데이터가 없습니다.",
      },
    ],
    unpaidSlips: props.salesAnalytics?.unpaidSlips ?? [],
  };
}

function emptyMonthlySales() {
  const today = new Date();
  return Array.from({ length: 6 }, (_, index) => {
    const date = new Date(today.getFullYear(), today.getMonth() - 5 + index, 1);
    return {
      label: `${date.getMonth() + 1}월`,
      value: 0,
    };
  });
}
