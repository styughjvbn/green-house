export const ANALYTICS_TABS = {
  sales: {
    label: "매출/출하",
  },
  variety: {
    label: "품종 분석",
  },
  customer: {
    label: "거래처 분석",
  },
  space: {
    label: "농장 공간",
  },
  work: {
    label: "작업/상태",
  },
} as const;

export type AnalyticsTab = keyof typeof ANALYTICS_TABS;

export const ANALYTICS_ROUTE = {
  root: "/analytics",
  tab: (tab: AnalyticsTab) => `/analytics/${tab}` as const,
} as const;

export const DEFAULT_ANALYTICS_TAB: AnalyticsTab = "sales";

export function isAnalyticsTab(value: string): value is AnalyticsTab {
  return Object.hasOwn(ANALYTICS_TABS, value);
}

export const ANALYTICS_NAV_ITEMS = Object.entries(ANALYTICS_TABS).map(
  ([tab, config]) => ({
    tab: tab as AnalyticsTab,
    label: config.label,
    href: ANALYTICS_ROUTE.tab(tab as AnalyticsTab),
  }),
);
