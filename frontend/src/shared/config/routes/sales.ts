export const SALES_TABS = {
  slips: {
    label: "판매 전표",
  },
  auction: {
    label: "출하·경매 추적",
  },
  settlement: {
    label: "경매 정산",
  },
  partners: {
    label: "거래처 관리",
  },
} as const;

export type SalesTab = keyof typeof SALES_TABS;

export const SALES_ROUTE = {
  root: "/sales",
  tab: (tab: SalesTab) => `/sales/${tab}` as const,
} as const;

export const DEFAULT_SALES_TAB: SalesTab = "slips";

export function isSalesTab(value: string): value is SalesTab {
  return Object.hasOwn(SALES_TABS, value);
}

export const SALES_NAV_ITEMS = Object.entries(SALES_TABS).map(
  ([tab, config]) => ({
    tab: tab as SalesTab,
    label: config.label,
    href: SALES_ROUTE.tab(tab as SalesTab),
  }),
);

export const SALES_V2_TABS = {
  slips: { label: "전표" },
  auction: { label: "경매" },
  payments: { label: "입금" },
  partners: { label: "거래처" },
} as const;
export type SalesV2Tab = keyof typeof SALES_V2_TABS;
export const SALES_V2_ROUTE = {
  root: "/sales-v2",
  tab: (tab: SalesV2Tab) => `/sales-v2/${tab}` as const,
};
export function isSalesV2Tab(value: string): value is SalesV2Tab {
  return Object.hasOwn(SALES_V2_TABS, value);
}
export const SALES_V2_NAV_ITEMS = Object.entries(SALES_V2_TABS).map(
  ([tab, config]) => ({
    tab,
    label: config.label,
    href: SALES_V2_ROUTE.tab(tab as SalesV2Tab),
  }),
);
export function salesV2Href(
  tab: SalesV2Tab,
  values: Record<string, string | number> = {},
) {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(values))
    params.set(key, String(value));
  const query = params.toString();
  return `${SALES_V2_ROUTE.tab(tab)}${query ? `?${query}` : ""}`;
}
